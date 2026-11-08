import { test } from "node:test";
import assert from "node:assert/strict";
import { orderCanBeResponded, parseOrderDecision, REJECTION_REASONS } from "../src/orders/orderDecisionPolicy";

test("acceptance requires a bounded integer preparation estimate", () => {
  assert.deepEqual(parseOrderDecision({ action: "ACCEPT", estimatedPreparationMinutes: 30 }),
    { action: "ACCEPT", estimatedPreparationMinutes: 30 });
  for (const value of [undefined, null, 4, 181, 10.5, "30"])
    assert.throws(() => parseOrderDecision({ action: "ACCEPT", estimatedPreparationMinutes: value }));
});

test("rejection requires a known reason and OTHER requires a meaningful explanation", () => {
  for (const reason of REJECTION_REASONS.filter(value => value !== "OTHER"))
    assert.deepEqual(parseOrderDecision({ action: "REJECT", reason }), { action: "REJECT", reason, explanation: null });
  assert.deepEqual(parseOrderDecision({ action: "REJECT", reason: "OTHER", explanation: "  Motivo excepcional  " }),
    { action: "REJECT", reason: "OTHER", explanation: "Motivo excepcional" });
  for (const body of [{ action: "REJECT" }, { action: "REJECT", reason: "UNKNOWN" },
    { action: "REJECT", reason: "OTHER" }, { action: "REJECT", reason: "OTHER", explanation: "  " },
    { action: "REJECT", reason: "STORE_BUSY", explanation: "extra" }]) assert.throws(() => parseOrderDecision(body));
});

test("only pending confirmation may be accepted or rejected", () => {
  assert.equal(orderCanBeResponded("PENDING_CONFIRMATION"), true);
  for (const status of ["PREPARING", "REJECTED", "CANCELLED", "READY_FOR_PICKUP", null])
    assert.equal(orderCanBeResponded(status), false);
});

test("decision payloads reject unknown fields and malformed bodies", () => {
  for (const body of [null, [], "ACCEPT", {}, { action: "ACCEPT", estimatedPreparationMinutes: 20, storeId: "other" }])
    assert.throws(() => parseOrderDecision(body));
});
