import { OrderHttpError } from "./orderRequest";

export const MIN_PREPARATION_MINUTES = 5;
export const MAX_PREPARATION_MINUTES = 180;

export function preparationMinutes(value: unknown): number {
  if (!Number.isInteger(value) || Number(value) < MIN_PREPARATION_MINUTES || Number(value) > MAX_PREPARATION_MINUTES)
    throw new OrderHttpError("INVALID_REQUEST", 400);
  return Number(value);
}

export function parsePreparationEstimate(body: unknown): Readonly<{ estimatedPreparationMinutes: number }> {
  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body as Record<string, unknown>).join(",") !== "estimatedPreparationMinutes")
    throw new OrderHttpError("INVALID_REQUEST", 400);
  return Object.freeze({ estimatedPreparationMinutes:
    preparationMinutes((body as Record<string, unknown>).estimatedPreparationMinutes) });
}

export function canUpdatePreparationEstimate(status: unknown): boolean {
  return status === "PREPARING";
}
