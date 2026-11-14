import { test } from "node:test";
import assert from "node:assert/strict";
import { orderCanBeResponded, parseOrderDecision, REJECTION_REASONS } from "../src/orders/orderDecisionPolicy";

test("acceptance requires a bounded integer preparation estimate", () => {
  for (const minutes of [5, 30, 180])
    assert.deepEqual(parseOrderDecision({ action: "ACCEPT", estimatedPreparationMinutes: minutes }),
      { action: "ACCEPT", estimatedPreparationMinutes: minutes });
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

test("OTHER explanation length is bounded to 3..300 characters after trimming", () => {
  for (const length of [3, 300]) {
    const explanation = "x".repeat(length);
    assert.deepEqual(parseOrderDecision({ action: "REJECT", reason: "OTHER", explanation: `  ${explanation}  ` }),
      { action: "REJECT", reason: "OTHER", explanation });
  }
  for (const length of [2, 301])
    assert.throws(() => parseOrderDecision({ action: "REJECT", reason: "OTHER", explanation: "x".repeat(length) }), String(length));
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
