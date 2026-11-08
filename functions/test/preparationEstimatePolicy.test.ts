import { test } from "node:test";
import assert from "node:assert/strict";
import { canUpdatePreparationEstimate, parsePreparationEstimate, preparationMinutes } from "../src/orders/preparationEstimatePolicy";

test("preparation estimates use whole minutes between five and three hours", () => {
  for (const value of [5, 30, 180]) assert.equal(preparationMinutes(value), value);
  for (const value of [null, "30", 4, 181, 10.5, Number.NaN]) assert.throws(() => preparationMinutes(value));
});

test("estimate update payload is strict and cannot select another order or timestamp", () => {
  assert.deepEqual(parsePreparationEstimate({ estimatedPreparationMinutes: 45 }), { estimatedPreparationMinutes: 45 });
  for (const body of [null, [], {}, { estimatedPreparationMinutes: 20, orderId: "other" },
    { estimatedReadyAt: 1_800_000_000_000 }]) assert.throws(() => parsePreparationEstimate(body));
});

test("only PREPARING permits an estimate update", () => {
  assert.equal(canUpdatePreparationEstimate("PREPARING"), true);
  for (const status of ["PENDING_CONFIRMATION", "READY_FOR_PICKUP", "CANCELLED", "REJECTED", null])
    assert.equal(canUpdatePreparationEstimate(status), false);
});
