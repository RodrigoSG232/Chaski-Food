import { digest } from "../access/commercialAccessPolicy";
import { MAX_ORDER_EPOCH_MILLIS } from "./orderModels";
import { OrderHttpError } from "./orderRequest";

/** HU20: each extension adds 5–60 minutes to the current estimate; there is no cumulative cap. */
export const MIN_EXTENSION_MINUTES = 5;
export const MAX_EXTENSION_MINUTES = 60;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type PreparationExtension = Readonly<{ operationId: string; additionalMinutes: number; expectedEstimateRevision: number }>;

const invalid = (): never => { throw new OrderHttpError("INVALID_REQUEST", 400); };

/** Strict body: the client chooses only how much to add, never the resulting time. */
export function parsePreparationExtension(body: unknown): PreparationExtension {
  if (!body || typeof body !== "object" || Array.isArray(body)) return invalid();
  const value = body as Record<string, unknown>;
  if (Object.keys(value).sort().join(",") !== "additionalMinutes,expectedEstimateRevision,operationId") return invalid();
  const { operationId, additionalMinutes, expectedEstimateRevision } = value;
  if (typeof operationId !== "string" || !UUID_V4.test(operationId)) return invalid();
  if (!Number.isInteger(additionalMinutes) || (additionalMinutes as number) < MIN_EXTENSION_MINUTES ||
      (additionalMinutes as number) > MAX_EXTENSION_MINUTES) return invalid();
  if (!Number.isSafeInteger(expectedEstimateRevision) || (expectedEstimateRevision as number) < 0) return invalid();
  return Object.freeze({ operationId, additionalMinutes: additionalMinutes as number,
    expectedEstimateRevision: expectedEstimateRevision as number });
}

export function canExtendPreparation(status: unknown): boolean {
  return status === "PREPARING";
}

/** newReadyAt = max(current estimate, server time) + minutes. A past estimate restarts from now. */
export function extendedReadyAtMillis(currentReadyAtMillis: number, serverNowMillis: number, additionalMinutes: number): number {
  const result = Math.max(currentReadyAtMillis, serverNowMillis) + additionalMinutes * 60_000;
  if (!Number.isSafeInteger(result) || result > MAX_ORDER_EPOCH_MILLIS) throw new OrderHttpError("DATA_INCONSISTENT", 503);
  return result;
}

/** Stable per order and operation; the hash binds actor and payload so a reused ID cannot replay other data. */
export function extensionOperationKey(operationId: string): string {
  return digest(["preparation-extension-v1", operationId]);
}

export function extensionRequestHash(actorUid: string, storeId: string, orderId: string, input: PreparationExtension): string {
  return digest([actorUid, storeId, orderId, input.additionalMinutes, input.expectedEstimateRevision]);
}
