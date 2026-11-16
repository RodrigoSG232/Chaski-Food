import { timingSafeEqual } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { OrderHttpError } from "./orderRequest";

/**
 * E2 — pickup security (HU21-B). Pure rules shared by the manager verification, riderApi events and the admin unlock.
 * The stored map is additive: orders without it start at cycle 1 with no failures, no lock and no verification.
 */
export const MAX_PICKUP_FAILURES = 5;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const ASSIGNMENT_ID = /^[A-Za-z0-9_-]{1,100}$/;

export type PickupVerification = Readonly<{ assignmentId: string; assignmentVersion: number; verifiedAt: Timestamp; verifiedByUid: string }>;
export type PickupSecurity = Readonly<{ cycle: number; failures: number; lockedAt: Timestamp | null; verification: PickupVerification | null }>;
export type AssignmentRef = Readonly<{ id: string; version: number }>;

const inconsistent = () => new OrderHttpError("DATA_INCONSISTENT", 503, true);

export function readPickupSecurity(value: unknown): PickupSecurity {
  if (value === undefined || value === null) return { cycle: 1, failures: 0, lockedAt: null, verification: null };
  if (typeof value !== "object" || Array.isArray(value)) throw inconsistent();
  const raw = value as Record<string, any>;
  if (!Number.isSafeInteger(raw.cycle) || raw.cycle < 1 || !Number.isSafeInteger(raw.failures) || raw.failures < 0 ||
      raw.failures > MAX_PICKUP_FAILURES || (raw.lockedAt !== null && !(raw.lockedAt instanceof Timestamp))) throw inconsistent();
  let verification: PickupVerification | null = null;
  if (raw.verification !== null && raw.verification !== undefined) {
    const v = raw.verification;
    if (typeof v.assignmentId !== "string" || !Number.isSafeInteger(v.assignmentVersion) || !(v.verifiedAt instanceof Timestamp) ||
        typeof v.verifiedByUid !== "string") throw inconsistent();
    verification = { assignmentId: v.assignmentId, assignmentVersion: v.assignmentVersion, verifiedAt: v.verifiedAt, verifiedByUid: v.verifiedByUid };
  }
  return { cycle: raw.cycle, failures: raw.failures, lockedAt: raw.lockedAt, verification };
}

/** A verification only counts for the exact assignment (id + version) that is current now. */
export function verificationIsCurrent(security: PickupSecurity, assignment: AssignmentRef | null | undefined): boolean {
  return !!assignment && !!security.verification && security.verification.assignmentId === assignment.id &&
    security.verification.assignmentVersion === assignment.version;
}

/** Releasing or replacing an assignment drops its verification but never failures, lock or cycle. */
export function withoutVerification(security: PickupSecurity): PickupSecurity {
  return { ...security, verification: null };
}

export type PickupVerificationRequest = Readonly<{ operationId: string; code: string; assignmentId: string; assignmentVersion: number }>;
export function parsePickupVerificationRequest(value: unknown): PickupVerificationRequest {
  const invalid = () => new OrderHttpError("INVALID_REQUEST", 400);
  if (!value || typeof value !== "object" || Array.isArray(value)) throw invalid();
  const body = value as Record<string, unknown>;
  if (Object.keys(body).sort().join(",") !== "assignmentId,assignmentVersion,code,operationId") throw invalid();
  if (typeof body.operationId !== "string" || !UUID_V4.test(body.operationId) || typeof body.code !== "string" ||
      !/^[0-9]{4}$/.test(body.code) || typeof body.assignmentId !== "string" || !ASSIGNMENT_ID.test(body.assignmentId) ||
      !Number.isSafeInteger(body.assignmentVersion) || (body.assignmentVersion as number) < 1) throw invalid();
  return { operationId: body.operationId, code: body.code, assignmentId: body.assignmentId, assignmentVersion: body.assignmentVersion as number };
}

export type PickupUnlockRequest = Readonly<{ operationId: string; reason: string }>;
export function parsePickupUnlockRequest(value: unknown): PickupUnlockRequest {
  const invalid = () => new OrderHttpError("INVALID_REQUEST", 400);
  if (!value || typeof value !== "object" || Array.isArray(value)) throw invalid();
  const body = value as Record<string, unknown>;
  if (Object.keys(body).sort().join(",") !== "operationId,reason") throw invalid();
  if (typeof body.operationId !== "string" || !UUID_V4.test(body.operationId) || typeof body.reason !== "string") throw invalid();
  const reason = body.reason.trim();
  if (reason !== body.reason || reason.length < 5 || reason.length > 200) throw invalid();
  return { operationId: body.operationId, reason };
}

export type PickupVerificationOutcome = "VERIFIED" | "ALREADY_VERIFIED" | "PICKUP_CODE_INVALID" | "ASSIGNMENT_CHANGED" |
  "ORDER_STATE_CHANGED" | "PICKUP_LOCKED";
export type PickupDecision = Readonly<{ outcome: PickupVerificationOutcome; next: PickupSecurity | null; attemptsRemaining: number }>;

/** Constant-time comparison of two 4-digit strings; the submitted code is never stored or logged. */
function sameCode(expected: string, submitted: string): boolean {
  const a = Buffer.from(expected), b = Buffer.from(submitted);
  return a.length === b.length && timingSafeEqual(a, b);
}

/**
 * Decision order: state → lock → current assignment → already verified → code.
 * `next` is the state to persist (null = unchanged). The fifth wrong code locks the order in the same write.
 */
export function decidePickupVerification(input: { status: unknown; storedCode: unknown; security: PickupSecurity;
  assignment: AssignmentRef | null | undefined; request: PickupVerificationRequest; actorUid: string; at: Timestamp }): PickupDecision {
  const { security, request } = input, remaining = MAX_PICKUP_FAILURES - security.failures;
  if (input.status !== "READY_FOR_PICKUP") return { outcome: "ORDER_STATE_CHANGED", next: null, attemptsRemaining: remaining };
  if (security.lockedAt) return { outcome: "PICKUP_LOCKED", next: null, attemptsRemaining: 0 };
  if (!input.assignment || input.assignment.id !== request.assignmentId || input.assignment.version !== request.assignmentVersion)
    return { outcome: "ASSIGNMENT_CHANGED", next: null, attemptsRemaining: remaining };
  if (verificationIsCurrent(security, input.assignment)) return { outcome: "ALREADY_VERIFIED", next: null, attemptsRemaining: remaining };
  if (typeof input.storedCode !== "string" || !/^[0-9]{4}$/.test(input.storedCode)) throw inconsistent();
  if (sameCode(input.storedCode, request.code)) {
    return { outcome: "VERIFIED", attemptsRemaining: remaining, next: { ...security, verification:
      { assignmentId: request.assignmentId, assignmentVersion: request.assignmentVersion, verifiedAt: input.at, verifiedByUid: input.actorUid } } };
  }
  const failures = security.failures + 1, locked = failures >= MAX_PICKUP_FAILURES;
  return { outcome: locked ? "PICKUP_LOCKED" : "PICKUP_CODE_INVALID", attemptsRemaining: MAX_PICKUP_FAILURES - failures,
    next: { ...security, failures, lockedAt: locked ? input.at : null } };
}

/** HTTP mapping kept next to the decision so every caller answers the same way. */
export function pickupOutcomeStatus(outcome: PickupVerificationOutcome): number {
  return outcome === "VERIFIED" || outcome === "ALREADY_VERIFIED" ? 200 : outcome === "PICKUP_LOCKED" ? 423 : 409;
}

/** After an authorized unlock: new cycle, clean counters, no verification. */
export function unlockedSecurity(security: PickupSecurity): PickupSecurity {
  return { cycle: security.cycle + 1, failures: 0, lockedAt: null, verification: null };
}

export function storedPickupSecurity(security: PickupSecurity) {
  return { cycle: security.cycle, failures: security.failures, lockedAt: security.lockedAt, verification: security.verification };
}
