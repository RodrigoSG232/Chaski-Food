import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { commandHash, decideResolution, parseReconciliationCommand, publicResolution, reviewIsOpen }
  from "../src/orders/collectionReconciliationPolicy";
import { aggregateDashboard } from "../src/dashboard/dashboardPolicy";
import { collectionStatusOf, publicCollectionReview, riderPaymentEligibility } from "../src/integration/riderIntegrationPolicy";
import { projectBuyerOrderDetail } from "../src/orders/buyerOrdersPolicy";

/** E5.4a — pure reconciliation rules. Scenarios use Food's expected total S/87.00 (8700). */
const op = "6f1c2b4e-9a3d-4c1e-8f2a-1b2c3d4e5f60";
const resolve = (outcome: string, confirmedAmountMinor: unknown, extra: Record<string, unknown> = {}) =>
  ({ action: "RESOLVE", operationId: op, outcome, confirmedAmountMinor, evidence: "Voucher revisado con el local", ...extra });

test("E5.4a: strict command validation (fields, operationId, amounts, evidence)", () => {
  assert.equal(parseReconciliationCommand(resolve("FULLY_COLLECTED", 8700)).action, "RESOLVE");
  assert.equal((parseReconciliationCommand(resolve("NOT_FULLY_COLLECTED", 0)) as any).confirmedAmountMinor, 0);
  assert.equal(parseReconciliationCommand({ action: "RECORD_EXCESS_REFUND", operationId: op, refundedAmountMinor: 300,
    evidence: "Devuelto en efectivo" }).action, "RECORD_EXCESS_REFUND");
  const bad: unknown[] = [null, [], "x", {},
    resolve("FULLY_COLLECTED", 8700, { extra: 1 }), resolve("PARTIAL", 8700), resolve("FULLY_COLLECTED", -1),
    resolve("FULLY_COLLECTED", 1.5), resolve("FULLY_COLLECTED", "8700"), resolve("FULLY_COLLECTED", null),
    resolve("FULLY_COLLECTED", 10_000_001), resolve("FULLY_COLLECTED", 8700, { operationId: "op-1" }),
    resolve("FULLY_COLLECTED", 8700, { operationId: "../x/" + op }), resolve("FULLY_COLLECTED", 8700, { operationId: op.toUpperCase() }),
    resolve("FULLY_COLLECTED", 8700, { evidence: "abcd" }), resolve("FULLY_COLLECTED", 8700, { evidence: " padded " }),
    resolve("FULLY_COLLECTED", 8700, { evidence: "x".repeat(301) }), resolve("FULLY_COLLECTED", 8700, { evidence: "line\nbreak ok" }),
    resolve("FULLY_COLLECTED", 8700, { evidence: 12345 }), { ...resolve("FULLY_COLLECTED", 8700), action: "DELETE" },
    { action: "RECORD_EXCESS_REFUND", operationId: op, refundedAmountMinor: 0, evidence: "Devuelto en efectivo" },
    { action: "RECORD_EXCESS_REFUND", operationId: op, refundedAmountMinor: 300, evidence: "Devuelto", outcome: "X" }];
  for (const body of bad) assert.throws(() => parseReconciliationCommand(body), /INVALID_REQUEST/, JSON.stringify(body));
  assert.ok(parseReconciliationCommand(resolve("FULLY_COLLECTED", 8700, { evidence: "x".repeat(300) })));
});

test("E5.4a: scenarios B–D decide amounts against Food's expected total; mismatches are rejected", () => {
  assert.deepEqual(decideResolution(8700, "FULLY_COLLECTED", 8700),
    { outcome: "FULLY_COLLECTED", confirmedMinor: 8700, shortfallMinor: 0, excessMinor: 0, excessStatus: "NONE" }); // B
  assert.deepEqual(decideResolution(8700, "NOT_FULLY_COLLECTED", 4000),
    { outcome: "NOT_FULLY_COLLECTED", confirmedMinor: 4000, shortfallMinor: 4700, excessMinor: 0, excessStatus: "NONE" }); // C
  assert.deepEqual(decideResolution(8700, "FULLY_COLLECTED", 9000),
    { outcome: "FULLY_COLLECTED", confirmedMinor: 9000, shortfallMinor: 0, excessMinor: 300, excessStatus: "PENDING_REFUND" }); // D
  assert.equal(decideResolution(8700, "NOT_FULLY_COLLECTED", 0).shortfallMinor, 8700);
  assert.throws(() => decideResolution(8700, "FULLY_COLLECTED", 8699), /AMOUNT_OUTCOME_MISMATCH/);
  assert.throws(() => decideResolution(8700, "NOT_FULLY_COLLECTED", 8700), /AMOUNT_OUTCOME_MISMATCH/);
  assert.throws(() => decideResolution(0, "FULLY_COLLECTED", 1), /DATA_INCONSISTENT/);
});

test("E5.4a: idempotency hash depends on actor, order and full content", () => {
  const a = parseReconciliationCommand(resolve("FULLY_COLLECTED", 8700));
  assert.equal(commandHash("u", "o", a), commandHash("u", "o", parseReconciliationCommand(resolve("FULLY_COLLECTED", 8700))));
  assert.notEqual(commandHash("u", "o", a), commandHash("u2", "o", a));
  assert.notEqual(commandHash("u", "o", a), commandHash("u", "o2", a));
  assert.notEqual(commandHash("u", "o", a), commandHash("u", "o", parseReconciliationCommand(resolve("FULLY_COLLECTED", 8800))));
});

const open = { status: "OPEN", expectedMinor: 8700, declaredMinor: 4000, method: "CASH", currency: "PEN", eventId: "EV", assignmentId: "A",
  assignmentVersion: 1, openedAt: Timestamp.fromMillis(1) };
const delivered = { status: "COMPLETED", paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PENDING",
  riderIntegration: { deliveryOutcome: "SUCCESS" }, collectionReview: open };
const resolved = (resolution: Record<string, unknown>) => ({ ...open, status: "RESOLVED",
  resolution: { actorUid: "PRIVATE_ADMIN", operationId: op, resolvedAt: Timestamp.fromMillis(2), ...resolution } });

test("E5.4a: only an E4 discrepancy (delivered, cash, PENDING, OPEN review) is eligible", () => {
  assert.equal(reviewIsOpen(delivered), true);
  for (const change of [{ status: "IN_DELIVERY" }, { paymentStatus: "PAID" }, { paymentMethod: "PREPAID" },
    { riderIntegration: { deliveryOutcome: "UNSUCCESSFUL" } }, { collectionReview: undefined },
    { collectionReview: resolved({ outcome: "NOT_FULLY_COLLECTED" }) }])
    assert.equal(reviewIsOpen({ ...delivered, ...change }), false, JSON.stringify(change));
});

test("E5.4a: Rider projection is additive, keeps the historic DISCREPANCY and never exposes actor or evidence", () => {
  const d = resolved({ outcome: "FULLY_COLLECTED", confirmedMinor: 9000, shortfallMinor: 0, excessMinor: 300, excessStatus: "PENDING_REFUND" });
  assert.deepEqual(publicCollectionReview(d), { status: "RESOLVED", expectedMinor: 8700, declaredMinor: 4000, method: "CASH", currency: "PEN",
    outcome: "FULLY_COLLECTED", confirmedMinor: 9000, shortfallMinor: 0, excessMinor: 300, excessStatus: "PENDING_REFUND" });
  assert.equal(JSON.stringify(publicCollectionReview(d)).includes("PRIVATE"), false);
  assert.deepEqual(publicCollectionReview(open), { status: "OPEN", expectedMinor: 8700, declaredMinor: 4000, method: "CASH", currency: "PEN" });
  assert.equal(publicResolution(open), null);
  assert.equal(collectionStatusOf({ collectionReview: d, paymentCollection: { source: "RECONCILED" } }), "DISCREPANCY");
  assert.equal(collectionStatusOf({ paymentCollection: { source: "RIDER" } }), "RECORDED");
  assert.equal(riderPaymentEligibility({ pickupCode: "0042", paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PAID",
    paymentCollection: { source: "RECONCILED" } }), "CASH_COLLECTED");
});

test("E5.4a: HU28 counts a PAID order once by subtotal; NOT_FULLY_COLLECTED is no sale and no error; corrupt states still fail", () => {
  const at = Timestamp.fromDate(new Date("2026-11-08T13:00:00Z"));
  const row = (id: string, more: Record<string, unknown>) => ({ id, data: { schemaVersion: 1, orderId: id, businessId: "business",
    storeId: "store", currency: "PEN", subtotalMinor: 8000, completedAt: at, status: "COMPLETED", riderIntegration: { deliveryOutcome: "SUCCESS" },
    createdAt: at, ...more } });
  const base = { businessId: "business", storeIds: ["store"], dates: ["2026-11-08"], created: [] };
  const completed = [
    row("A", { paymentStatus: "PAID", paymentCollection: { source: "RIDER" } }),
    row("B", { paymentStatus: "PAID", paymentCollection: { source: "RECONCILED" },
      collectionReview: resolved({ outcome: "FULLY_COLLECTED", confirmedMinor: 8700, excessMinor: 0, excessStatus: "NONE" }) }),
    row("C", { paymentStatus: "PENDING", collectionReview: resolved({ outcome: "NOT_FULLY_COLLECTED", confirmedMinor: 4000, shortfallMinor: 4700 }) }),
    row("D", { paymentStatus: "PAID", paymentCollection: { source: "RECONCILED" },
      collectionReview: resolved({ outcome: "FULLY_COLLECTED", confirmedMinor: 9000, excessMinor: 300, excessStatus: "PENDING_REFUND" }) }),
    row("E", { paymentStatus: "PAID", paymentCollection: { source: "RECONCILED" },
      collectionReview: resolved({ outcome: "FULLY_COLLECTED", confirmedMinor: 9000, excessMinor: 300, excessStatus: "REFUNDED" }) }),
    row("open", { paymentStatus: "PENDING", collectionReview: open }),
  ];
  const metrics = aggregateDashboard({ ...base, completed });
  assert.equal(metrics.salesMinor, 4 * 8000); // A, B, D, E by subtotal; the S/3 excess never adds
  assert.equal(metrics.orders, 0); // created list is empty: delivered rows add sales only
  for (const corrupt of [{ paymentStatus: "PENDING", collectionReview: resolved({ outcome: "FULLY_COLLECTED" }) },
    { paymentStatus: "PENDING", collectionReview: { status: "CLOSED" } }, { paymentStatus: "PENDING" }])
    assert.throws(() => aggregateDashboard({ ...base, completed: [row("bad", corrupt)] }), /DATA_INCONSISTENT/, JSON.stringify(corrupt));
});

test("E5.4a: buyer sees INCOMPLETE and its own excess status, never actors, evidence or partial amounts", () => {
  const order = (more: Record<string, unknown>) => ({ schemaVersion: 1, orderId: "b".repeat(64), buyerUid: "buyer", status: "COMPLETED",
    createdAt: Timestamp.fromMillis(1800000000000), items: [{ productId: "p", categoryId: "c", name: "Plato", quantity: 1, unitPriceMinor: 8400, lineTotalMinor: 8400 }],
    subtotalMinor: 8400, deliveryFeeMinor: 300, totalMinor: 8700, currency: "PEN", paymentMethod: "CASH_ON_DELIVERY",
    storeSnapshot: { name: "Local" }, businessSnapshot: { name: "Negocio" }, pickupSnapshot: { addressText: "Local" },
    dropoffSnapshot: { addressText: "Casa" }, riderIntegration: { deliveryOutcome: "SUCCESS" }, ...more });
  const history = [{ schemaVersion: 1, fromStatus: null, toStatus: "COMPLETED", actorUid: "x", occurredAt: Timestamp.fromMillis(1800000000000) }];
  const view = (more: Record<string, unknown>) => projectBuyerOrderDetail("b".repeat(64), order(more), "buyer", history) as any;
  const c = view({ paymentStatus: "PENDING", collectionReview: resolved({ outcome: "NOT_FULLY_COLLECTED", confirmedMinor: 4000, shortfallMinor: 4700,
    excessMinor: 0, excessStatus: "NONE" }) });
  assert.equal(c.paymentReview, "INCOMPLETE"); assert.equal(c.paymentExcess, null); assert.equal(c.paymentSource, null);
  assert.equal(JSON.stringify(c).includes("4000"), false); assert.equal(JSON.stringify(c).includes("PRIVATE"), false);
  const d = view({ paymentStatus: "PAID", paymentCollection: { source: "RECONCILED" }, collectionReview: resolved({ outcome: "FULLY_COLLECTED",
    confirmedMinor: 9000, shortfallMinor: 0, excessMinor: 300, excessStatus: "PENDING_REFUND" }) });
  assert.equal(d.paymentSource, "RIDER"); assert.equal(d.paymentReview, null); assert.deepEqual(d.paymentExcess, { amountMinor: 300, status: "PENDING_REFUND" });
  const e = view({ paymentStatus: "PAID", paymentCollection: { source: "RECONCILED" }, collectionReview: resolved({ outcome: "FULLY_COLLECTED",
    confirmedMinor: 9000, shortfallMinor: 0, excessMinor: 300, excessStatus: "REFUNDED" }) });
  assert.deepEqual(e.paymentExcess, { amountMinor: 300, status: "REFUNDED" });
  assert.equal(view({ paymentStatus: "PENDING", collectionReview: open }).paymentReview, "OPEN");
  assert.equal(view({ paymentStatus: "PAID", paymentCollection: { source: "RIDER" } }).paymentExcess, null);
});
