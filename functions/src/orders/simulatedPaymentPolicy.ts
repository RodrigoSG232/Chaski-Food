import { pickupCode } from "./orderReadyPolicy";

export type SimulatedPaymentDecision = "PAY" | "ALREADY_PAID" | "NOT_ALLOWED";

export function simulatedPaymentDecision(order: Record<string, unknown>): SimulatedPaymentDecision {
  if (order.paymentStatus === "PAID" && order.paymentSimulation === true &&
      typeof order.pickupCode === "string" && /^[0-9]{4}$/.test(order.pickupCode)) return "ALREADY_PAID";
  if (order.status !== "PREPARING" || order.paymentStatus !== "PENDING" ||
      order.paymentMethod !== "CASH_ON_DELIVERY" || order.pickupCode != null) return "NOT_ALLOWED";
  return "PAY";
}

export function simulatedPickupCode(value: number): string {
  return pickupCode(value);
}
