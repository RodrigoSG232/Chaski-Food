import { OrderHttpError } from "./orderRequest";

export const REJECTION_REASONS = ["STORE_BUSY", "PRODUCT_UNAVAILABLE", "CLOSING_SOON", "OTHER"] as const;
export type RejectionReason = typeof REJECTION_REASONS[number];
export type OrderDecision =
  | Readonly<{ action: "ACCEPT"; estimatedPreparationMinutes: number }>
  | Readonly<{ action: "REJECT"; reason: RejectionReason; explanation: string | null }>;

const invalid = (): never => { throw new OrderHttpError("INVALID_REQUEST", 400); };

export function parseOrderDecision(body: unknown): OrderDecision {
  if (!body || typeof body !== "object" || Array.isArray(body)) return invalid();
  const value = body as Record<string, unknown>;
  if (value.action === "ACCEPT") {
    if (Object.keys(value).sort().join(",") !== "action,estimatedPreparationMinutes" ||
        !Number.isInteger(value.estimatedPreparationMinutes) ||
        (value.estimatedPreparationMinutes as number) < 5 || (value.estimatedPreparationMinutes as number) > 180) return invalid();
    return Object.freeze({ action: "ACCEPT", estimatedPreparationMinutes: value.estimatedPreparationMinutes as number });
  }
  if (value.action === "REJECT") {
    const keys = Object.keys(value).sort().join(",");
    if (!["action,reason", "action,explanation,reason"].includes(keys) ||
        !REJECTION_REASONS.includes(value.reason as RejectionReason)) return invalid();
    const explanation = value.explanation;
    if (value.reason === "OTHER") {
      if (typeof explanation !== "string" || explanation.trim().length < 3 || explanation.trim().length > 300) return invalid();
      return Object.freeze({ action: "REJECT", reason: "OTHER", explanation: explanation.trim() });
    }
    if (explanation !== undefined) return invalid();
    return Object.freeze({ action: "REJECT", reason: value.reason as RejectionReason, explanation: null });
  }
  return invalid();
}

export function orderCanBeResponded(status: unknown): boolean {
  return status === "PENDING_CONFIRMATION";
}
