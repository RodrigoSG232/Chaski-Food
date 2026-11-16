import { after, test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { hu23Event, hu23Order, hu23Sample, hu23Verification } from "./lab/hu23Fixture";

/** E1.1: what the Rider backend receives from riderApi (LOCAL emulator, synthetic data only). */
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
const project = "demo-chaski-hu15", prefix = "e11-" + Date.now();
const app = initializeApp({ projectId: project }, prefix), db = getFirestore(app);
const base = "http://127.0.0.1:5001/" + project + "/southamerica-west1/riderApi/integration/v1/";
after(async () => { await db.terminate(); await deleteApp(app); });

async function call(path: string, method = "GET", body?: unknown) {
  const r = await fetch(base + path, { method, headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) });
  return { status: r.status, body: await r.json() as any };
}
const forbidden = ["_seconds", "_nanoseconds", "payloadHash", "receivedAt", "paymentSimulation", "buyerUid", "PREPAID_SIMULATION"];
function assertClean(value: unknown, extra: string[] = []) {
  const json = JSON.stringify(value);
  for (const secret of [...forbidden, ...extra]) assert.equal(json.includes(secret), false, secret);
}
async function seed(n: number, cash = false) {
  const id = createHash("sha256").update(prefix + n).digest("hex"), buyer = prefix + "-PRIVATE-BUYER";
  const { paymentSimulation, ...legacy } = hu23Order(id, buyer);
  const order = cash ? { ...legacy, paymentStatus: "PENDING" } : { ...legacy, paymentSimulation };
  await db.doc("food_orders/" + id).set({ ...order, readyAt: Timestamp.fromMillis(Date.now() - 60_000) });
  return { id, buyer };
}
const sendEvent = (body: unknown) => call("rider-events", "POST", body);

test("E1.1: listing never exposes the pickup code nor the buyer UID", async () => {
  const { buyer } = await seed(1);
  const list = await call("orders?limit=50");
  assert.equal(list.status, 200);
  // Until E1.3 the listing is unordered and limited before filtering, so every returned order is checked.
  for (const order of list.body.orders) {
    assert.equal("pickupCode" in order, false); assert.equal("id" in order.customer, false);
    assert.equal(typeof order.payment.amountToCollectMinor, "number");
  }
  assertClean(list.body, [buyer]);
});

test("E1.1: detail gives the code only while a current assignment exists, with normalized dispatch and tracking", async () => {
  const { id, buyer } = await seed(2);
  const before = await call("orders/" + id);
  assert.equal(before.status, 200); assert.equal(before.body.order.pickupCode, null);
  assert.deepEqual(before.body.order.payment, { method: "CASH_ON_DELIVERY", status: "PAID", amountToCollectMinor: 0, currency: "PEN" });
  assert.match(before.body.order.readyAt, /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/);
  assert.equal(before.body.order.dispatch.updatedAt, null);

  assert.equal((await call("rider-events", "POST", hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  const assigned = await call("orders/" + id);
  assert.equal(assigned.body.order.pickupCode, "0042");
  assert.deepEqual(assigned.body.order.dispatch.assignment, { id: "hu23-assignment-1", version: 1 });
  assert.match(assigned.body.order.dispatch.updatedAt, /Z$/);
  assertClean(assigned.body, [buyer]);

  await db.doc("food_orders/" + id).update(hu23Verification());
  const verified = await call("orders/" + id);
  assert.equal(verified.body.order.pickupCode, null);
  assert.equal(verified.body.order.pickupVerification.assignmentVersion, 1);
  assert.equal((await call("rider-events", "POST", hu23Event(id, "PICKED_UP", 2))).status, 200);
  assert.equal((await call("orders/" + id + "/tracking", "PUT", hu23Sample(1))).status, 200);
  const moving = await call("orders/" + id);
  assert.equal(moving.body.order.status, "IN_DELIVERY"); assert.equal(moving.body.order.pickupCode, null);
  assert.deepEqual(Object.keys(moving.body.order.tracking).sort(), ["accuracyMeters", "assignmentId", "assignmentVersion",
    "estimatedArrivalAt", "latitude", "longitude", "recordedAt", "sequence"]);
  assert.match(moving.body.order.tracking.recordedAt, /Z$/);
  assertClean(moving.body, [buyer]);
});

test("E1.1: a released assignment no longer receives the code", async () => {
  const { id } = await seed(3);
  assert.equal((await call("rider-events", "POST", hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  assert.equal((await call("rider-events", "POST", hu23Event(id, "ASSIGNMENT_RELEASED", 2))).status, 200);
  const released = await call("orders/" + id);
  assert.equal(released.body.order.dispatch.assignment, null); assert.equal(released.body.order.pickupCode, null);
});

test("E1.2: a cash order is discoverable, assignable, reaches the store and can be released and reassigned", async () => {
  const { id, buyer } = await seed(4, true);
  const detail = await call("orders/" + id);
  assert.equal(detail.status, 200); assert.equal(detail.body.order.pickupCode, null);
  assert.deepEqual(detail.body.order.payment, { method: "CASH_ON_DELIVERY", status: "PENDING", amountToCollectMinor: 1300, currency: "PEN" });
  assertClean(detail.body, [buyer]);

  const accepted = hu23Event(id, "ASSIGNMENT_ACCEPTED", 1);
  assert.equal((await sendEvent(accepted)).status, 200);
  const replay = await sendEvent(accepted);
  assert.equal(replay.status, 200); assert.equal(replay.body.lastEventSequence, 1);
  assert.equal((await sendEvent({ ...accepted, eventId: "other-id", eventSequence: 2, assignment: { id: "hu23-assignment-2", version: 2 } })).body.error.code,
    "ASSIGNMENT_CONFLICT");
  assert.equal((await sendEvent({ ...accepted, reason: "changed" })).body.error.code, "IDEMPOTENCY_CONFLICT");
  assert.equal((await call("orders/" + id)).body.order.pickupCode, "0042");

  assert.equal((await sendEvent(hu23Event(id, "ARRIVED_AT_MERCHANT", 3))).body.error.code, "EVENT_SEQUENCE_CONFLICT");
  assert.equal((await sendEvent(hu23Event(id, "ARRIVED_AT_MERCHANT", 2))).status, 200);
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_RELEASED", 3))).status, 200);
  assert.equal((await sendEvent(hu23Event(id, "ARRIVED_AT_MERCHANT", 4))).body.error.code, "ASSIGNMENT_STALE");
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_ACCEPTED", 4, 2))).status, 200);
  const reassigned = await call("orders/" + id);
  assert.deepEqual(reassigned.body.order.dispatch.assignment, { id: "hu23-assignment-2", version: 2 });
  assert.equal(reassigned.body.order.dispatch.lastEventSequence, 4);
});

test("E1.2/E2: unverified pickup stays blocked without consuming the sequence", async () => {
  const { id } = await seed(5, true);
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  const expected: Record<string, string> = { PICKED_UP: "PICKUP_NOT_VERIFIED", ARRIVED_AT_CUSTOMER: "ORDER_STATE_CONFLICT",
    COMPLETED_SUCCESSFULLY: "ORDER_STATE_CONFLICT", COMPLETED_UNSUCCESSFULLY: "ORDER_STATE_CONFLICT" };
  for (const [type, code] of Object.entries(expected)) {
    const body = type === "COMPLETED_SUCCESSFULLY" ? hu23Event(id, type, 2, 1, { method: "CASH", amountMinor: 1300, currency: "PEN" }) : hu23Event(id, type, 2);
    const blocked = await sendEvent(body);
    assert.equal(blocked.status, 409, type); assert.equal(blocked.body.error.code, code, type);
  }
  const order = (await db.doc("food_orders/" + id).get()).data()!;
  assert.equal(order.status, "READY_FOR_PICKUP"); assert.equal(order.paymentStatus, "PENDING");
  assert.equal(order.riderIntegration.lastEventSequence, 1);
  assert.equal((await db.collection("food_orders/" + id + "/rider_events").get()).size, 1);
});

test("E1.2: unsupported payment combinations stay invisible and cannot be operated", async () => {
  const { id } = await seed(6, true);
  await db.doc("food_orders/" + id).update({ paymentStatus: "PAID" });
  assert.equal((await call("orders/" + id)).body.error.code, "ORDER_NOT_READY");
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).body.error.code, "ORDER_NOT_READY");
  const list = await call("orders?limit=50");
  assert.equal(list.body.orders.some((order: any) => order.orderId === id), false);
});

// ---- E1.3: ordered, cursor-based listing over a stable synthetic set (all readyAt in 2000–2001, before any other test data).
const year2000 = Date.UTC(2000, 0, 1) / 1000, year2001 = Date.UTC(2001, 0, 1) / 1000;
type Seeded = { id: string; ready: Timestamp | null; eligible: boolean };
async function seedSet(tag: string): Promise<Seeded[]> {
  const rows: Seeded[] = [], add = (ready: Timestamp | null, kind: "cash" | "legacy" | "paidNoSim" | "assigned") => {
    const id = createHash("sha256").update(prefix + tag + rows.length).digest("hex");
    rows.push({ id, ready, eligible: ready !== null && (kind === "cash" || kind === "legacy") }); return { id, kind, ready };
  };
  const plan: { id: string; kind: string; ready: Timestamp | null }[] = [];
  for (let i = 0; i < 210; i++) plan.push(add(new Timestamp(year2000 + i, 0), "paidNoSim"));         // > 200 non-eligible first
  for (let i = 0; i < 60; i++) plan.push(add(new Timestamp(year2001 + Math.floor(i / 2), 0), i % 10 === 0 ? "legacy" : "cash"));
  for (let i = 0; i < 5; i++) plan.push(add(new Timestamp(year2001 + 100, 0), "cash"));             // identical readyAt
  for (let i = 1; i <= 3; i++) plan.push(add(new Timestamp(year2001 + 101, 500_000_000 + i * 1000), "cash")); // same millisecond
  for (let i = 0; i < 20; i++) plan.push(add(new Timestamp(year2001 + 5 + i, 0), i % 2 ? "paidNoSim" : "assigned"));
  for (let i = 0; i < 2; i++) plan.push(add(null, "cash"));                                          // no readyAt
  for (let start = 0; start < plan.length; start += 400) {
    const batch = db.batch();
    for (const row of plan.slice(start, start + 400)) {
      const { paymentSimulation, ...legacy } = hu23Order(row.id, prefix + "-PRIVATE-BUYER");
      const payment = row.kind === "legacy" ? { paymentSimulation } : row.kind === "paidNoSim" ? {} : { paymentStatus: "PENDING" };
      const extra = row.kind === "assigned" ? { riderIntegration: { lastEventSequence: 1, lastAssignmentVersion: 1, status: "ACCEPTED",
        assignment: { id: "x", version: 1 }, rider: null, deliveryOutcome: null, reason: null, updatedAt: Timestamp.now() } } : {};
      batch.set(db.doc("food_orders/" + row.id), { ...legacy, ...payment, ...extra, ...(row.ready ? { readyAt: row.ready } : {}) });
    }
    await batch.commit();
  }
  return rows;
}
async function traverse(limit: number) {
  const pages: { ids: string[]; next: string | null }[] = []; let cursor: string | null = null;
  for (let guard = 0; guard < 400; guard++) {
    const page = await call("orders?limit=" + limit + (cursor ? "&cursor=" + cursor : ""));
    assert.equal(page.status, 200, JSON.stringify(page.body));
    pages.push({ ids: page.body.orders.map((o: any) => o.orderId), next: page.body.nextCursor });
    for (const order of page.body.orders) { assert.equal("pickupCode" in order, false); assert.equal("id" in order.customer, false); }
    if (page.body.nextCursor === null) return pages;
    assert.notEqual(page.body.nextCursor, cursor); cursor = page.body.nextCursor;
  }
  throw new Error("pagination did not finish");
}
const expectedOrder = (rows: Seeded[]) => rows.filter(row => row.eligible)
  .sort((a, b) => a.ready!.seconds - b.ready!.seconds || a.ready!.nanoseconds - b.ready!.nanoseconds || (a.id < b.id ? -1 : 1)).map(row => row.id);

test("E1.3: full traversal returns every eligible order exactly once, in order, across empty pages and the 200-document scan limit", async () => {
  const rows = await seedSet("a"), ours = new Set(rows.map(row => row.id));
  for (const limit of [50, 7]) {
    const pages = await traverse(limit), seen = pages.flatMap(page => page.ids);
    assert.equal(new Set(seen).size, seen.length, "no duplicates (limit " + limit + ")");
    assert.deepEqual(seen.filter(id => ours.has(id)), expectedOrder(rows), "exact eligible set and order (limit " + limit + ")");
    assert.deepEqual(pages[0].ids.filter(id => ours.has(id)), [], "first 200 examined are not eligible");
    assert.notEqual(pages[0].next, null, "empty page still carries a cursor");
    assert.ok(pages.every(page => page.ids.length <= limit));
  }
  const cash = rows.find(row => row.eligible && row.ready!.seconds === year2001 + 100)!;
  const detail = await call("orders/" + cash.id);
  assert.equal(detail.body.order.payment.status, "PENDING"); assert.equal(detail.body.order.pickupCode, null);
  for (const missing of rows.filter(row => row.ready === null)) assert.equal((await call("orders/" + missing.id)).status, 200);
});

test("E1.3: an assigned order leaves the listing and returns at its original position after release", async () => {
  const rows = await seedSet("b"), ours = new Set(rows.map(row => row.id)), target = expectedOrder(rows)[3];
  const listed = async () => (await traverse(50)).flatMap(page => page.ids).filter(id => ours.has(id));
  assert.equal((await sendEvent(hu23Event(target, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  assert.equal((await listed()).includes(target), false);
  assert.equal((await sendEvent(hu23Event(target, "ASSIGNMENT_RELEASED", 2))).status, 200);
  assert.deepEqual(await listed(), expectedOrder(rows));
});

test("E1.3: malformed cursors and parameters are rejected", async () => {
  const raw = (value: unknown) => Buffer.from(JSON.stringify(value)).toString("base64url");
  for (const query of ["cursor=%25%25", "cursor=" + raw({ v: 2, s: 1, n: 0, id: "c".repeat(64) }), "cursor=" + raw({ v: 1, s: 1, n: 1e9, id: "c".repeat(64) }),
    "limit=0", "limit=51", "status=READY_FOR_PICKUP", "limit=1&limit=2"]) {
    const r = await call("orders?" + query);
    assert.equal(r.status, 400, query); assert.equal(r.body.error.code, "INVALID_REQUEST", query);
  }
});

// ---- E4: completion, cash collection and reconciliation (LOCAL emulator, synthetic data)
const cash = (amountMinor: number, method = "YAPE") => ({ method, amountMinor, currency: "PEN" });
async function delivering(n: number) {
  const { id, buyer } = await seed(n, true);
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  await db.doc("food_orders/" + id).update(hu23Verification());
  assert.equal((await sendEvent(hu23Event(id, "PICKED_UP", 2))).status, 200);
  assert.equal((await sendEvent(hu23Event(id, "ARRIVED_AT_CUSTOMER", 3))).status, 200);
  return { id, buyer };
}
test("E4: exact collection completes and records the payment atomically; a replay returns the same result", async () => {
  const { id, buyer } = await delivering(30);
  const finish = hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(1300));
  const done = await sendEvent(finish);
  assert.equal(done.status, 200); assert.equal(done.body.collectionStatus, "RECORDED"); assert.equal(done.body.collectionReview, null);
  const order = (await db.doc("food_orders/" + id).get()).data()!;
  assert.equal(order.status, "COMPLETED"); assert.equal(order.riderIntegration.deliveryOutcome, "SUCCESS");
  assert.equal(order.paymentStatus, "PAID"); assert.ok(order.paidAt instanceof Timestamp);
  assert.equal(order.paymentCollection.source, "RIDER"); assert.equal(order.paymentCollection.method, "YAPE");
  assert.equal(order.paymentCollection.amountMinor, 1300); assert.equal(order.collectionReview, undefined);
  const history = (await db.doc("food_orders/" + id + "/payment_history/rider_collection").get()).data()!;
  assert.equal(history.status, "RECORDED"); assert.equal(history.expectedMinor, 1300);
  assert.equal((await db.collection("order_events").where("orderId", "==", id).where("type", "==", "ORDER_DELIVERED").get()).size, 1);
  const replay = await sendEvent(finish);
  assert.equal(replay.status, 200); assert.equal(replay.body.collectionStatus, "RECORDED");
  assert.equal((await sendEvent({ ...finish, collection: cash(1200) })).body.error.code, "IDEMPOTENCY_CONFLICT");
  assert.equal((await sendEvent(hu23Event(id, "COMPLETED_SUCCESSFULLY", 5, 1, cash(1300)))).body.error.code, "ORDER_STATE_CONFLICT");
  assert.equal((await db.collection("food_orders/" + id + "/payment_history").get()).size, 1);
  const detail = await call("orders/" + id);
  assert.equal(detail.status, 200); assert.equal(detail.body.order.collectionStatus, "RECORDED");
  assert.deepEqual(detail.body.order.payment, { method: "CASH_ON_DELIVERY", status: "PAID", amountToCollectMinor: 0, currency: "PEN" });
  assertClean(detail.body, [buyer]);
});
test("E4: a lower or higher declared amount opens a review, keeps the payment PENDING and never marks PAID", async () => {
  for (const [n, amount] of [[31, 1000], [32, 1500]] as const) {
    const { id } = await delivering(n);
    const done = await sendEvent(hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(amount, "CASH")));
    assert.equal(done.body.collectionStatus, "DISCREPANCY");
    assert.deepEqual(done.body.collectionReview, { status: "OPEN", expectedMinor: 1300, declaredMinor: amount, method: "CASH", currency: "PEN" });
    const order = (await db.doc("food_orders/" + id).get()).data()!;
    assert.equal(order.status, "COMPLETED"); assert.equal(order.riderIntegration.deliveryOutcome, "SUCCESS");
    assert.equal(order.paymentStatus, "PENDING"); assert.equal(order.paidAt, undefined); assert.equal(order.paymentCollection, undefined);
    assert.equal(order.collectionReview.status, "OPEN"); assert.equal(order.collectionReview.declaredMinor, amount);
    assert.equal((await db.doc("food_orders/" + id + "/payment_history/rider_collection").get()).get("status"), "DISCREPANCY");
    assert.equal((await call("orders/" + id)).body.order.collectionStatus, "DISCREPANCY");
  }
});
test("E4: a failed delivery records no collection; declaring money on a failure is rejected", async () => {
  const { id } = await delivering(33);
  const rejected = await sendEvent(hu23Event(id, "COMPLETED_UNSUCCESSFULLY", 4, 1, cash(1300)));
  assert.equal(rejected.status, 422);
  const failed = await sendEvent(hu23Event(id, "COMPLETED_UNSUCCESSFULLY", 4));
  assert.equal(failed.status, 200); assert.equal(failed.body.collectionStatus, null);
  const order = (await db.doc("food_orders/" + id).get()).data()!;
  assert.equal(order.status, "COMPLETED"); assert.equal(order.riderIntegration.deliveryOutcome, "UNSUCCESSFUL");
  assert.equal(order.paymentStatus, "PENDING");
  assert.equal((await db.collection("food_orders/" + id + "/payment_history").get()).size, 0);
});
test("E4: invalid or missing collections are rejected without consuming the sequence", async () => {
  const { id } = await delivering(34);
  const base = hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(1300));
  for (const body of [hu23Event(id, "COMPLETED_SUCCESSFULLY", 4), { ...base, collection: cash(0) }, { ...base, collection: { ...cash(1300), method: "CARD" } },
    { ...base, collection: { ...cash(1300), currency: "USD" } }, { ...hu23Event(id, "ARRIVED_AT_CUSTOMER", 4), collection: null }]) {
    assert.equal((await sendEvent(body)).status, 422, JSON.stringify(body.collection));
  }
  assert.equal((await db.doc("food_orders/" + id).get()).get("riderIntegration.lastEventSequence"), 3);
});
test("E4: concurrent terminal events finish the order once and record one collection", async () => {
  const { id } = await delivering(35);
  const results = await Promise.all([
    sendEvent({ ...hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(1300)), eventId: "terminal-a" }),
    sendEvent({ ...hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(1300)), eventId: "terminal-b" }),
    sendEvent({ ...hu23Event(id, "COMPLETED_UNSUCCESSFULLY", 4), eventId: "terminal-c" })]);
  assert.equal(results.filter(r => r.status === 200).length, 1, JSON.stringify(results.map(r => r.body)));
  assert.equal((await db.collection("food_orders/" + id + "/rider_events").where("eventSequence", "==", 4).get()).size, 1);
  assert.ok((await db.collection("food_orders/" + id + "/payment_history").get()).size <= 1);
});
test("E4: legacy prepaid orders finish with collection null and reject a declared collection", async () => {
  const { id } = await seed(36);
  assert.equal((await sendEvent(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  await db.doc("food_orders/" + id).update(hu23Verification());
  assert.equal((await sendEvent(hu23Event(id, "PICKED_UP", 2))).status, 200);
  assert.equal((await sendEvent(hu23Event(id, "COMPLETED_SUCCESSFULLY", 3, 1, cash(1300)))).status, 422);
  const done = await sendEvent(hu23Event(id, "COMPLETED_SUCCESSFULLY", 3));
  assert.equal(done.status, 200); assert.equal(done.body.collectionStatus, null);
  const order = (await db.doc("food_orders/" + id).get()).data()!;
  assert.equal(order.paymentStatus, "PAID"); assert.equal(order.paymentSimulation, true); assert.equal(order.paymentCollection, undefined);
});
