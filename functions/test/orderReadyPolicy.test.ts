import { test } from "node:test";
import assert from "node:assert/strict";
import { orderCanBeMarkedReady, pickupCode } from "../src/orders/orderReadyPolicy";

test("el código de recojo siempre conserva cuatro dígitos", () => {
  assert.equal(pickupCode(0), "0000");
  assert.equal(pickupCode(42), "0042");
  assert.equal(pickupCode(9_999), "9999");
  for (const value of [-1, 1.5, 10_000, NaN]) assert.throws(() => pickupCode(value));
});

test("solo un pedido en preparación puede marcarse listo", () => {
  assert.equal(orderCanBeMarkedReady("PREPARING"), true);
  for (const status of ["PENDING_CONFIRMATION", "READY_FOR_PICKUP", "IN_DELIVERY", null])
    assert.equal(orderCanBeMarkedReady(status), false);
});
