import { test } from "node:test";
import assert from "node:assert/strict";
import { simulatedPaymentDecision, simulatedPickupCode } from "../src/orders/simulatedPaymentPolicy";

test("el pago anticipado queda deshabilitado para efectivo contra entrega", () => {
  const order={status:"PREPARING",paymentStatus:"PENDING",paymentMethod:"CASH_ON_DELIVERY"};
  assert.equal(simulatedPaymentDecision(order),"NOT_ALLOWED");
  assert.equal(simulatedPaymentDecision({...order,status:"PENDING_CONFIRMATION"}),"NOT_ALLOWED");
  assert.equal(simulatedPaymentDecision({...order,paymentStatus:"PAID"}),"NOT_ALLOWED");
  assert.equal(simulatedPaymentDecision({...order,pickupCode:"1234"}),"NOT_ALLOWED");
});

test("un pago simulado repetido conserva el código ya generado", () => {
  assert.equal(simulatedPaymentDecision({status:"PREPARING",paymentStatus:"PAID",paymentMethod:"CASH_ON_DELIVERY",
    paymentSimulation:true,pickupCode:"0042"}),"ALREADY_PAID");
  assert.equal(simulatedPickupCode(42),"0042");
});
