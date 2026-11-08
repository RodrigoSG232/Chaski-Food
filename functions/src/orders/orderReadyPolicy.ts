export function pickupCode(value: number): string {
  if (!Number.isInteger(value) || value < 0 || value > 9_999) throw new Error("INVALID_PICKUP_CODE");
  return value.toString().padStart(4, "0");
}

export function orderCanBeMarkedReady(status: unknown): boolean {
  return status === "PREPARING";
}
