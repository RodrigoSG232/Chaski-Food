import { test } from "node:test";
import assert from "node:assert/strict";
import { Auth } from "firebase-admin/auth";
import { verifyCommercialIdentity } from "../src/auth/verifyBuyerToken";
import { digest, assignmentFrom, assertExclusive, CommercialAccessError, normalizedEmail, parseCommercialCommand } from "../src/access/commercialAccessPolicy";

const row = assignmentFrom("b_u@example.test", { businessId: "b", email: "u@example.test", assignedStoreIds: ["s"], bindingState: "VERIFIED", boundUid: "u" });
for (const status of ["APPROVED", "SUSPENDED"]) test(`conflicto ${status} conserva propiedad`, () => {
  assert.throws(() => assertExclusive(status, [row], "u"), CommercialAccessError);
});
test("pendiente no es propietario ni invitacion es conflicto", () => {
  assert.doesNotThrow(() => assertExclusive("PENDING_REVIEW", [row], "u"));
  assert.doesNotThrow(() => assertExclusive("APPROVED", [{ ...row, bindingState: "PENDING", boundUid: null }], "u"));
  assert.doesNotThrow(() => assertExclusive("APPROVED", [row], "other"));
});
test("normalizacion y validacion de correo", () => {
  assert.equal(normalizedEmail(" U@Example.Test "), "u@example.test");
  for (const bad of [null, "missing", "a/b@example.test"]) assert.throws(() => normalizedEmail(bad));
});
test("registro antiguo no se convierte en vinculacion verificada", () => {
  assert.equal(assignmentFrom("b_u@example.test", { businessId: "b", email: "u@example.test", assignedStoreIds: ["s"] }).bindingState, "LEGACY");
});
test("rechaza asignaciones estructuralmente invalidas", () => {
  assert.throws(() => assignmentFrom("b_u@example.test", { ...row, assignedStoreIds: ["s", "s"] }));
  assert.throws(() => assignmentFrom("wrong", row));
});
test("contrato rechaza roles UID y revisiones inventadas", () => {
  const command = { action: "BIND", operationId: "op", expectedRevision: 0 };
  assert.equal(parseCommercialCommand(command).action, "BIND");
  for (const bad of [{ ...command, uid: "victim" }, { ...command, expectedRevision: -1 }, { ...command, expectedRevision: 1.2 }, { ...command, action: "UNKNOWN" }]) assert.throws(() => parseCommercialCommand(bad));
});
test("fallo Auth no concede identidad", async () => {
  const auth = { verifyIdToken: async () => { throw new Error("unavailable"); } } as unknown as Auth;
  await assert.rejects(verifyCommercialIdentity(auth, "Bearer fixture-token"));
});
test("token con correo anterior se rechaza contra identidad actual", async () => {
  const auth = { verifyIdToken: async () => ({ uid: "u", email: "old@example.test", email_verified: true }),
    getUser: async () => ({ uid: "u", email: "new@example.test", emailVerified: true, disabled: false }) } as unknown as Auth;
  await assert.rejects(verifyCommercialIdentity(auth, "Bearer fixture-token"));
});
test("claim administrativo anterior se rechaza", async () => {
  const auth = { verifyIdToken: async () => ({ uid: "u", email: "u@example.test", email_verified: true, admin: true }),
    getUser: async () => ({ uid: "u", email: "u@example.test", emailVerified: true, disabled: false }) } as unknown as Auth;
  await assert.rejects(verifyCommercialIdentity(auth, "Bearer fixture-token"));
});

test("reintento no depende del orden de campos JSON", () => {
  assert.equal(digest({ action: "ASSIGN", body: { email: "e", revision: 1 } }), digest({ body: { revision: 1, email: "e" }, action: "ASSIGN" }));
});
