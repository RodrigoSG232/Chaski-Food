import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { classifyStatus, encodeBuyerOrdersCursor, parseBuyerOrdersQuery, projectBuyerOrderDetail, projectBuyerOrderSummary,
  buyerOrderId } from "../src/orders/buyerOrdersPolicy";
const id = "b".repeat(64), uid = "buyer";
const order = () => ({ schemaVersion: 1, orderId: id, buyerUid: uid, status: "PENDING_CONFIRMATION", createdAt: new Timestamp(1800000000, 5000000),
  items: [{ productId: "p1", categoryId: "c", name: "Plato", quantity: 2, unitPriceMinor: 1000, lineTotalMinor: 2000 },
    { productId: "p2", categoryId: "c", name: "Bebida", quantity: 1, unitPriceMinor: 500, lineTotalMinor: 500 }],
  subtotalMinor: 2500, deliveryFeeMinor: 300, totalMinor: 2800, currency: "PEN", paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PENDING",
  storeSnapshot: { id: "s", name: "Local ficticio", phone: "000" }, businessSnapshot: { id: "b", name: "Negocio ficticio" },
  pickupSnapshot: { addressText: "Av. Local 1", location: { latitude: -12, longitude: -77 }, reference: null, instructions: null },
  dropoffSnapshot: { sourceAddressId: "addr", label: "Casa", addressText: "Calle Cliente 2", location: { latitude: -12.1, longitude: -77.1 },
    reference: "Puerta verde", instructions: "" },
  buyerSnapshot: { uid, displayName: "PRIVATE_NAME", phone: "PRIVATE_PHONE" }, intentId: "PRIVATE_INTENT", requestHash: "PRIVATE_HASH",
  quoteSnapshot: { quoteId: "PRIVATE_QUOTE" } });
const history = [{ schemaVersion: 1, fromStatus: null, toStatus: "PENDING_CONFIRMATION", actorUid: "PRIVATE_ACTOR", occurredAt: new Timestamp(1800000000, 5000000) }];

test("status table: known phases, unknown valid status shown as ACTIVE, missing or malformed is inconsistent", () => {
  for (const s of ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP", "IN_DELIVERY"]) assert.deepEqual(classifyStatus(s), { status: s, phase: "ACTIVE", known: true });
  for (const s of ["COMPLETED", "CANCELLED", "REJECTED"]) assert.deepEqual(classifyStatus(s), { status: s, phase: "HISTORICAL", known: true });
  assert.deepEqual(classifyStatus("AWAITING_RIDER"), { status: "AWAITING_RIDER", phase: "ACTIVE", known: false });
  assert.equal(classifyStatus("CONFIRMED").phase, "ACTIVE"); // never introduced by HU17; HU19 goes PENDING_CONFIRMATION → PREPARING
  for (const bad of [undefined, null, "", "pending", "X", 3, {}, "A".repeat(65)]) assert.throws(() => classifyStatus(bad));
});

test("summary is a whitelist for the owning buyer and keeps exact amounts", () => {
  const s = projectBuyerOrderSummary(id, order(), uid);
  assert.deepEqual(Object.keys(s).sort(), ["orderId", "createdAtEpochMillis", "status", "phase", "statusKnown", "storeName", "businessName",
    "itemCount", "itemPreview", "itemLineCount", "totalMinor", "currency"].sort());
  assert.deepEqual(s.itemPreview, [{ name: "Plato", quantity: 2 }, { name: "Bebida", quantity: 1 }]); assert.equal(s.itemLineCount, 2);
  assert.equal(s.itemCount, 3); assert.equal(s.totalMinor, 2800); assert.equal(s.createdAtEpochMillis, 1800000000005);
  assert.equal(JSON.stringify(s).includes("PRIVATE"), false);
  assert.throws(() => projectBuyerOrderSummary(id, order(), "other"));
});

test("item preview keeps at most two snapshot lines in order and counts the rest", () => {
  const items = ["A", "B", "C", "D"].map((name, i) => ({ productId: "p" + i, categoryId: "c", name, quantity: i + 1, unitPriceMinor: 100,
    lineTotalMinor: 100 * (i + 1) }));
  const s = projectBuyerOrderSummary(id, { ...order(), items, subtotalMinor: 1000, totalMinor: 1300 }, uid);
  assert.deepEqual(s.itemPreview, [{ name: "A", quantity: 1 }, { name: "B", quantity: 2 }]);
  assert.equal(s.itemLineCount, 4); assert.equal(s.itemCount, 10);
  const one = projectBuyerOrderSummary(id, { ...order(), items: [order().items[0]], subtotalMinor: 2000, totalMinor: 2300 }, uid);
  assert.deepEqual(one.itemPreview, [{ name: "Plato", quantity: 2 }]); assert.equal(one.itemLineCount, 1);
});

test("corrupted documents never become partial rows", () => {
  for (const change of [{ status: null }, { status: undefined }, { status: "bad status" }, { schemaVersion: 2 }, { orderId: "x" }, { totalMinor: 1 },
    { createdAt: "bad" }, { storeSnapshot: null }, { businessSnapshot: { name: "" } }, { items: [] }])
    assert.throws(() => projectBuyerOrderSummary(id, { ...order(), ...change }, uid));
});

test("detail keeps HU16 snapshots by value and excludes buyer data, coordinates, hashes, quote and actors", () => {
  const d = projectBuyerOrderDetail(id, order(), uid, history);
  assert.equal(d.delivery.addressText, "Calle Cliente 2"); assert.equal(d.delivery.reference, "Puerta verde"); assert.equal(d.delivery.instructions, null);
  assert.equal(d.pickupAddressText, "Av. Local 1"); assert.equal(d.storePhone, "000"); assert.equal(d.items[0].lineTotalMinor, 2000);
  assert.deepEqual(d.statusHistory, [{ status: "PENDING_CONFIRMATION", statusKnown: true, occurredAtEpochMillis: 1800000000005 }]);
  const text = JSON.stringify(d);
  for (const secret of ["PRIVATE", "latitude", "longitude", "sourceAddressId", "buyerUid", "quote"]) assert.equal(text.includes(secret), false, secret);
  assert.throws(() => projectBuyerOrderDetail(id, order(), uid, []));
  assert.throws(() => projectBuyerOrderDetail(id, order(), uid, [{ ...history[0], toStatus: null }]));
  assert.throws(() => projectBuyerOrderDetail(id, { ...order(), dropoffSnapshot: null }, uid, history));
});

test("detail history is chronological even if stored out of order", () => {
  const later = { schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "PREPARING", occurredAt: new Timestamp(1800000100, 0) };
  const d = projectBuyerOrderDetail(id, order(), uid, [later, history[0]]);
  assert.deepEqual(d.statusHistory.map(e => e.status), ["PENDING_CONFIRMATION", "PREPARING"]);
});

test("cursor is bound to UID and phase, canonical, and never carries a client timestamp", () => {
  const cursor = encodeBuyerOrdersCursor(uid, "HISTORICAL", id), parsed = parseBuyerOrdersQuery({ phase: "HISTORICAL", cursor }, uid);
  assert.equal(parsed.cursor?.anchorId, id); assert.equal(parsed.pageSize, 20);
  assert.equal(Buffer.from(cursor, "base64url").toString("utf8").includes(uid), false);
  assert.throws(() => parseBuyerOrdersQuery({ phase: "HISTORICAL", cursor }, "other"));
  assert.throws(() => parseBuyerOrdersQuery({ phase: "ACTIVE", cursor }, uid));
  for (const raw of ["", cursor + "=", "../orders", Buffer.from(JSON.stringify({ ...JSON.parse(Buffer.from(cursor, "base64url").toString("utf8")), anchorId: "../x" })).toString("base64url")])
    assert.throws(() => parseBuyerOrdersQuery({ phase: "HISTORICAL", cursor: raw }, uid));
});

test("query parameters are strict: phase required, bounded page size, no extra keys", () => {
  assert.equal(parseBuyerOrdersQuery({ phase: "ACTIVE", pageSize: "50" }, uid).pageSize, 50);
  for (const q of [{}, { phase: "active" }, { phase: "ACTIVE", pageSize: "0" }, { phase: "ACTIVE", pageSize: "51" }, { phase: "ACTIVE", pageSize: 5 },
    { phase: ["ACTIVE"] }, { phase: "ACTIVE", status: "X" }, { phase: "ACTIVE", buyerUid: "other" }])
    assert.throws(() => parseBuyerOrdersQuery(q as Record<string, unknown>, uid));
});

test("order IDs must be SHA-256 hex; anything else is treated as not found", () => {
  assert.equal(buyerOrderId(id), id);
  for (const bad of ["", "x", "B".repeat(64), "../" + id, id + "/status_history"]) assert.equal(buyerOrderId(bad), null);
});
