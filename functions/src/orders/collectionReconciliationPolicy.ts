import { createHash } from "node:crypto";
import { OrderHttpError } from "./orderRequest";

/**
 * E5.4a — administrative reconciliation of a Rider collection discrepancy (pure rules, no Firestore).
 *
 * The admin decision is an ADJUSTMENT/AUDIT record, never a second physical collection: Rider's original declaration
 * (rider_events, payment_history/rider_collection and the original fields of collectionReview) is never modified.
 * - FULLY_COLLECTED: confirmed >= expected → PAID; any excess stays visible as PENDING_REFUND (never counted as a sale).
 * - NOT_FULLY_COLLECTED: 0 <= confirmed < expected → stays PENDING (no partial PAID); the shortfall is recorded so a
 *   future "recover balance" feature can build on it.
 * - RECORD_EXCESS_REFUND: an admin documents a refund made OUTSIDE the app (no transfer, no Yape/Plin call); full excess only.
 */
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export const EVIDENCE_MIN = 5;
export const EVIDENCE_MAX = 300;
export const MAX_CONFIRMED_MINOR = 10_000_000;

export type ReconciliationOutcome = "FULLY_COLLECTED" | "NOT_FULLY_COLLECTED";
export type ExcessStatus = "NONE" | "PENDING_REFUND" | "REFUNDED";

export type ReconciliationCommand =
  | Readonly<{ action: "RESOLVE"; operationId: string; outcome: ReconciliationOutcome; confirmedAmountMinor: number; evidence: string }>
  | Readonly<{ action: "RECORD_EXCESS_REFUND"; operationId: string; refundedAmountMinor: number; evidence: string }>;

const invalid = () => new OrderHttpError("INVALID_REQUEST", 400);
const keysAre = (body: Record<string, unknown>, keys: string[]) => Object.keys(body).sort().join(",") === [...keys].sort().join(",");
const amount = (v: unknown, min: number) => Number.isSafeInteger(v) && (v as number) >= min && (v as number) <= MAX_CONFIRMED_MINOR;

/** Strict request validation: exact field set, UUIDv4 operationId (safe as a Firestore doc id), trimmed evidence. */
export function parseReconciliationCommand(value: unknown): ReconciliationCommand {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw invalid();
  const body = value as Record<string, unknown>;
  if (typeof body.operationId !== "string" || !UUID_V4.test(body.operationId)) throw invalid();
  if (typeof body.evidence !== "string") throw invalid();
  const evidence = body.evidence.trim();
  if (evidence !== body.evidence || evidence.length < EVIDENCE_MIN || evidence.length > EVIDENCE_MAX || /[\u0000-\u001f\u007f]/.test(evidence))
    throw invalid();
  if (body.action === "RESOLVE") {
    if (!keysAre(body, ["action", "operationId", "outcome", "confirmedAmountMinor", "evidence"])) throw invalid();
    if (body.outcome !== "FULLY_COLLECTED" && body.outcome !== "NOT_FULLY_COLLECTED") throw invalid();
    if (!amount(body.confirmedAmountMinor, 0)) throw invalid();
    return { action: "RESOLVE", operationId: body.operationId, outcome: body.outcome,
      confirmedAmountMinor: body.confirmedAmountMinor as number, evidence };
  }
  if (body.action === "RECORD_EXCESS_REFUND") {
    if (!keysAre(body, ["action", "operationId", "refundedAmountMinor", "evidence"])) throw invalid();
    if (!amount(body.refundedAmountMinor, 1)) throw invalid();
    return { action: "RECORD_EXCESS_REFUND", operationId: body.operationId, refundedAmountMinor: body.refundedAmountMinor as number, evidence };
  }
  throw invalid();
}

/** Content hash used for idempotency: same operationId + same content → replay; different content → conflict. */
export function commandHash(actorUid: string, orderId: string, command: ReconciliationCommand): string {
  return createHash("sha256").update(JSON.stringify([actorUid, orderId, command])).digest("hex");
}

export type ReviewResolution = Readonly<{
  outcome: ReconciliationOutcome; confirmedMinor: number; shortfallMinor: number; excessMinor: number; excessStatus: ExcessStatus;
}>;

/**
 * Decides a RESOLVE against Food's authoritative expected amount (stored in the open review). The admin must pick the
 * outcome that matches the amount; a mismatch is rejected instead of guessed.
 */
export function decideResolution(expectedMinor: number, outcome: ReconciliationOutcome, confirmedMinor: number): ReviewResolution {
  if (!Number.isSafeInteger(expectedMinor) || expectedMinor < 1) throw new OrderHttpError("DATA_INCONSISTENT", 503, true);
  if (outcome === "FULLY_COLLECTED") {
    if (confirmedMinor < expectedMinor) throw new OrderHttpError("AMOUNT_OUTCOME_MISMATCH", 422);
    const excessMinor = confirmedMinor - expectedMinor;
    return { outcome, confirmedMinor, shortfallMinor: 0, excessMinor, excessStatus: excessMinor > 0 ? "PENDING_REFUND" : "NONE" };
  }
  if (confirmedMinor >= expectedMinor) throw new OrderHttpError("AMOUNT_OUTCOME_MISMATCH", 422);
  return { outcome, confirmedMinor, shortfallMinor: expectedMinor - confirmedMinor, excessMinor: 0, excessStatus: "NONE" };
}

/** E4 discrepancy still awaiting a decision: delivered successfully, cash on delivery, PENDING and an OPEN review. */
export function reviewIsOpen(order: Record<string, any>): boolean {
  return order.status === "COMPLETED" && order.paymentMethod === "CASH_ON_DELIVERY" && order.paymentStatus === "PENDING" &&
    order.riderIntegration?.deliveryOutcome === "SUCCESS" && order.collectionReview?.status === "OPEN";
}

/** Public, actor-free and evidence-free view of a resolution (Rider detail and admin list). */
export function publicResolution(review: Record<string, any> | undefined) {
  const r = review?.resolution;
  if (review?.status !== "RESOLVED" || !r) return null;
  return { outcome: r.outcome, confirmedMinor: r.confirmedMinor, shortfallMinor: r.shortfallMinor,
    excessMinor: r.excessMinor, excessStatus: r.excessStatus };
}
