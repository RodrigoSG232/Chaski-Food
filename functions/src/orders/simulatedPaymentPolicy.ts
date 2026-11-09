import { pickupCode } from "./orderReadyPolicy";

export type SimulatedPaymentDecision = "PAY" | "ALREADY_PAID" | "NOT_ALLOWED";

export function simulatedPaymentDecision(order: Record<string, unknown>): SimulatedPaymentDecision {
  if (order.paymentStatus === "PAID" && order.paymentSimulation === true &&
      typeof order.pickupCode === "string" && /^[0-9]{4}$/.test(order.pickupCode)) return "ALREADY_PAID";
  // The legacy endpoint remains recognizable to older APKs, but cash is now
  // recorded only when the buyer confirms the simulated delivery.
  return "NOT_ALLOWED";
}

export function simulatedPickupCode(value: number): string {
  return pickupCode(value);
}
