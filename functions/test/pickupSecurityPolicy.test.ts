import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { decidePickupVerification, MAX_PICKUP_FAILURES, parsePickupUnlockRequest, parsePickupVerificationRequest,
  pickupOutcomeStatus, readPickupSecurity, unlockedSecurity, verificationIsCurrent, withoutVerification } from "../src/orders/pickupSecurityPolicy";

const at = Timestamp.fromMillis(1_800_000_000_000), op = "0b8f1a52-3c4d-4e5f-8a6b-7c8d9e0f1a2b";
const request = { operationId: op, code: "0427", assignmentId: "ASG_1", assignmentVersion: 2 };
const base = { status: "READY_FOR_PICKUP", storedCode: "0427", security: readPickupSecurity(undefined),
  assignment: { id: "ASG_1", version: 2 }, request, actorUid: "manager", at };

test("E2: missing map is a fresh cycle and corrupt maps are rejected", () => {
  assert.deepEqual(readPickupSecurity(undefined), { cycle: 1, failures: 0, lockedAt: null, verification: null });
  for (const bad of [{ cycle: 0, failures: 0, lockedAt: null }, { cycle: 1, failures: 6, lockedAt: null }, { cycle: 1, failures: 0, lockedAt: "x" }, []])
    assert.throws(() => readPickupSecurity(bad), /DATA_INCONSISTENT/);
});

test("E2: the right code verifies only the current assignment and keeps the order ready", () => {
  const ok = decidePickupVerification(base);
  assert.equal(ok.outcome, "VERIFIED"); assert.equal(ok.next!.failures, 0);
  assert.deepEqual(ok.next!.verification, { assignmentId: "ASG_1", assignmentVersion: 2, verifiedAt: at, verifiedByUid: "manager" });
  assert.equal(verificationIsCurrent(ok.next!, { id: "ASG_1", version: 2 }), true);
  assert.equal(verificationIsCurrent(ok.next!, { id: "ASG_1", version: 3 }), false);
  assert.equal(verificationIsCurrent(ok.next!, null), false);
  assert.equal(decidePickupVerification({ ...base, security: ok.next! }).outcome, "ALREADY_VERIFIED");
});

test("E2: wrong codes accumulate and the fifth locks the order", () => {
  let security = readPickupSecurity(undefined);
  for (let i = 1; i < MAX_PICKUP_FAILURES; i++) {
    const wrong = decidePickupVerification({ ...base, security, request: { ...request, code: "9999" } });
    assert.equal(wrong.outcome, "PICKUP_CODE_INVALID"); assert.equal(wrong.attemptsRemaining, MAX_PICKUP_FAILURES - i);
    security = wrong.next!;
  }
  const fifth = decidePickupVerification({ ...base, security, request: { ...request, code: "9999" } });
  assert.equal(fifth.outcome, "PICKUP_LOCKED"); assert.equal(fifth.next!.failures, 5); assert.equal(fifth.next!.lockedAt, at);
  assert.equal(decidePickupVerification({ ...base, security: fifth.next! }).outcome, "PICKUP_LOCKED");
  const released = withoutVerification(fifth.next!);
  assert.equal(released.lockedAt, at); assert.equal(released.failures, 5);
  assert.deepEqual(unlockedSecurity(fifth.next!), { cycle: 2, failures: 0, lockedAt: null, verification: null });
});

test("E2: state and assignment are checked before the code", () => {
  assert.equal(decidePickupVerification({ ...base, status: "IN_DELIVERY" }).outcome, "ORDER_STATE_CHANGED");
  assert.equal(decidePickupVerification({ ...base, assignment: null }).outcome, "ASSIGNMENT_CHANGED");
  assert.equal(decidePickupVerification({ ...base, assignment: { id: "ASG_1", version: 3 } }).outcome, "ASSIGNMENT_CHANGED");
  for (const outcome of ["ORDER_STATE_CHANGED", "ASSIGNMENT_CHANGED"] as const)
    assert.equal(decidePickupVerification({ ...base, ...(outcome === "ORDER_STATE_CHANGED" ? { status: "PREPARING" } : { assignment: null }) }).next, null);
  assert.deepEqual([pickupOutcomeStatus("VERIFIED"), pickupOutcomeStatus("PICKUP_CODE_INVALID"), pickupOutcomeStatus("PICKUP_LOCKED")], [200, 409, 423]);
});

test("E2: request parsers are strict", () => {
  assert.deepEqual(parsePickupVerificationRequest(request), request);
  for (const bad of [{ ...request, code: "427" }, { ...request, code: "04a7" }, { ...request, operationId: "x" }, { ...request, assignmentVersion: 0 },
    { ...request, extra: 1 }, { operationId: op, code: "0427" }, null, []])
    assert.throws(() => parsePickupVerificationRequest(bad), /INVALID_REQUEST/);
  assert.deepEqual(parsePickupUnlockRequest({ operationId: op, reason: "Repartidor confirmado por teléfono" }),
    { operationId: op, reason: "Repartidor confirmado por teléfono" });
  for (const bad of [{ operationId: op, reason: "ok" }, { operationId: op, reason: " espacios " }, { operationId: op }, { operationId: op, reason: "x".repeat(201) }])
    assert.throws(() => parsePickupUnlockRequest(bad), /INVALID_REQUEST/);
});
