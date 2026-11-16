import { test } from "node:test";
import assert from "node:assert/strict";
import { orderInRiderScope, orderRiderManaged, parseRiderActivation, storeInRiderScope } from "../src/integration/riderActivation";

test("E5.3: activation is DISABLED by default, explicit for pilot or general, and fails closed on anything else", () => {
  assert.equal(parseRiderActivation(undefined).mode, "DISABLED");
  assert.equal(parseRiderActivation("  ").mode, "DISABLED");
  assert.equal(parseRiderActivation("general").mode, "GENERAL");
  const pilot = parseRiderActivation("pilot:store-a, store_b");
  assert.equal(pilot.mode, "PILOT"); assert.equal(storeInRiderScope(pilot, "store_b"), true); assert.equal(storeInRiderScope(pilot, "store-c"), false);
  for (const bad of ["GENERAL", "pilot:", "pilot:bad id", "pilot:a,,b", "yes", "pilot:" + "a,".repeat(50) + "a"])
    assert.throws(() => parseRiderActivation(bad), /RIDER_ACTIVATION_INVALID/, bad);
});
test("E5.1/E5.3: Rider-managed and scope rules", () => {
  const disabled = parseRiderActivation(undefined);
  assert.equal(storeInRiderScope(disabled, "s"), false);
  assert.equal(orderInRiderScope(disabled, { storeId: "s", riderIntegration: { assignment: null } }), true);
  assert.equal(orderRiderManaged({}), false);
  for (const touched of [{ riderIntegration: {} }, { pickupSecurity: { verification: {} } }, { pickupSecurity: { lockedAt: 1 } },
    { paymentCollection: {} }, { collectionReview: {} }]) assert.equal(orderRiderManaged(touched), true, JSON.stringify(touched));
  assert.equal(orderRiderManaged({ pickupSecurity: { cycle: 1, failures: 2, lockedAt: null, verification: null } }), false);
});
