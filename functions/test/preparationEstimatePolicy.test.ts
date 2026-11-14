import { test } from "node:test";
import assert from "node:assert/strict";
import { canExtendPreparation, extendedReadyAtMillis, extensionOperationKey, extensionRequestHash,
  parsePreparationExtension } from "../src/orders/preparationEstimatePolicy";

const operationId = "3f8a1c2e-5b7d-4e9f-8a6b-1c2d3e4f5a6b";
const body = (patch: Record<string, unknown> = {}) => ({ operationId, additionalMinutes: 10, expectedEstimateRevision: 0, ...patch });

test("an extension adds whole minutes between five and sixty", () => {
  for (const minutes of [5, 10, 60])
    assert.deepEqual(parsePreparationExtension(body({ additionalMinutes: minutes })),
      { operationId, additionalMinutes: minutes, expectedEstimateRevision: 0 });
  for (const minutes of [null, "10", 4, 61, 10.5, Number.NaN, 0, -5, 180])
    assert.throws(() => parsePreparationExtension(body({ additionalMinutes: minutes })), String(minutes));
});

test("extension payload is strict: UUID v4 operation, safe revision and no client-chosen time", () => {
  assert.equal(parsePreparationExtension(body({ expectedEstimateRevision: 7 })).expectedEstimateRevision, 7);
  for (const bad of [null, [], {}, "x", { additionalMinutes: 10, expectedEstimateRevision: 0 },
    body({ estimatedReadyAt: 1_800_000_000_000 }), body({ orderId: "other" }), { estimatedPreparationMinutes: 30 },
    body({ operationId: "not-a-uuid" }), body({ operationId: operationId.toUpperCase() }),
    body({ operationId: "3f8a1c2e-5b7d-1e9f-8a6b-1c2d3e4f5a6b" }), body({ expectedEstimateRevision: -1 }),
    body({ expectedEstimateRevision: 1.5 }), body({ expectedEstimateRevision: "0" })])
    assert.throws(() => parsePreparationExtension(bad), JSON.stringify(bad));
});

test("only PREPARING can be extended", () => {
  assert.equal(canExtendPreparation("PREPARING"), true);
  for (const status of ["PENDING_CONFIRMATION", "READY_FOR_PICKUP", "IN_DELIVERY", "COMPLETED", "CANCELLED", "REJECTED", null])
    assert.equal(canExtendPreparation(status), false, String(status));
});

test("the new time adds to the current estimate, or to server time once the estimate has passed", () => {
  const at = (h: number, m: number) => Date.UTC(2026, 10, 14, h + 5, m); // Lima 12:00 = 17:00 UTC
  assert.equal(extendedReadyAtMillis(at(12, 40), at(12, 20), 10), at(12, 50));
  assert.equal(extendedReadyAtMillis(at(12, 50), at(12, 30), 20), at(13, 10));
  assert.equal(extendedReadyAtMillis(at(12, 40), at(12, 55), 10), at(13, 5));
  assert.equal(extendedReadyAtMillis(at(12, 40), at(12, 40), 5), at(12, 45));
  assert.throws(() => extendedReadyAtMillis(253_402_300_799_999, 0, 5));
});

test("operation keys are stable per operation and the request hash binds actor, order and payload", () => {
  const input = parsePreparationExtension(body());
  assert.equal(extensionOperationKey(operationId), extensionOperationKey(operationId));
  assert.notEqual(extensionOperationKey(operationId), extensionOperationKey("9f8a1c2e-5b7d-4e9f-8a6b-1c2d3e4f5a6b"));
  const hash = extensionRequestHash("manager", "store", "order", input);
  assert.equal(hash, extensionRequestHash("manager", "store", "order", { ...input }));
  for (const other of [extensionRequestHash("other", "store", "order", input),
    extensionRequestHash("manager", "store", "order", { ...input, additionalMinutes: 15 }),
    extensionRequestHash("manager", "store", "order", { ...input, expectedEstimateRevision: 1 })]) assert.notEqual(hash, other);
});
