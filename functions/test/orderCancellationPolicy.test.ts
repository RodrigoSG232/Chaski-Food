import { test } from "node:test";
import assert from "node:assert/strict";
import { buyerCancellationDecision } from "../src/orders/orderCancellationService";

const uid = "buyer";

test("HU18 only allows direct cancellation while the order is pending confirmation", () => {
  assert.equal(buyerCancellationDecision({ status: "PENDING_CONFIRMATION" }, uid), "CANCEL");
  for (const status of ["PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "IN_DELIVERY", "COMPLETED", "REJECTED"])
    assert.equal(buyerCancellationDecision({ status }, uid), "NOT_ALLOWED", status);
});

test("a repeated cancellation by the same buyer is idempotent but never impersonates another buyer", () => {
  assert.equal(buyerCancellationDecision({ status: "CANCELLED", cancelledByUid: uid }, uid), "ALREADY_CANCELLED");
  assert.equal(buyerCancellationDecision({ status: "CANCELLED", cancelledByUid: "other" }, uid), "NOT_ALLOWED");
});

test("a linked delivery requires future logistics coordination even while pending", () => {
  assert.equal(buyerCancellationDecision({ status: "PENDING_CONFIRMATION", deliveryId: "delivery" }, uid), "LOGISTICS_REQUIRED");
});
