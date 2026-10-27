import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const projectId = "demo-chaski-hu15";
const app = initializeApp({ projectId }, "eligibility-tests");
const db = getFirestore(app);
const auth = getAuth(app);
const base = `http://127.0.0.1:5001/${projectId}/southamerica-west1/foodApi/api/v1/stores/`;
let buyer: string;
let owner: string;
let admin: string;

async function account(uid: string, claims = {}) {
  await auth.deleteUser(uid).catch(() => undefined);
  await auth.createUser({ uid, email: `${uid}@example.invalid`, password: "emulator-password" });
  await auth.setCustomUserClaims(uid, claims);
  const response = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: `${uid}@example.invalid`, password: "emulator-password", returnSecureToken: true }),
  });
  assert.equal(response.status, 200);
  return (await response.json() as { idToken: string }).idToken;
}
async function seed(businessStatus = "APPROVED", storeStatus = "ACTIVE") {
  await db.doc("business_requests/http-owner").set({ ownerUid: "http-owner", status: businessStatus, ruc: "PRIVATE", observations: "PRIVATE" });
  await db.doc("stores/http-store").set({ businessId: "http-owner", status: storeStatus });
}
async function check(token: string | undefined = buyer, storeId = "http-store") {
  const response = await fetch(`${base}${storeId}/purchase-eligibility`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: AbortSignal.timeout(20_000),
  });
  return { response, body: await response.json() as Record<string, unknown> };
}
before(async () => {
  buyer = await account("http-buyer"); owner = await account("http-owner"); admin = await account("http-admin", { admin: true });
});
after(async () => { await db.terminate(); await deleteApp(app); });

test("APPROVED + ACTIVE devuelve solo datos mínimos", async () => {
  await seed(); const { response, body } = await check();
  assert.equal(response.status, 200); assert.equal(body.decision, "ELIGIBLE");
  assert.deepEqual(Object.keys(body).sort(), ["businessId", "checkedAt", "decision", "storeId"]);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-eligibility-reads"), "2");
});
for (const [role, getToken] of [["comprador", () => buyer], ["propietario", () => owner], ["administrador", () => admin]] as const) {
  test(`SUSPENDED bloquea a ${role}`, async () => {
    await seed("SUSPENDED"); const { response, body } = await check(getToken());
    assert.equal(response.status, 200); assert.equal(body.decision, "BUSINESS_SUSPENDED");
  });
}
for (const status of ["PENDING_REVIEW", "OBSERVED", "REJECTED"]) {
  test(`negocio ${status}`, async () => { await seed(status); assert.equal((await check()).body.decision, "BUSINESS_NOT_APPROVED"); });
}
test("local ausente", async () => { assert.equal((await check(buyer, "missing-store")).body.decision, "STORE_UNAVAILABLE"); });
test("local inactivo", async () => { await seed("APPROVED", "INACTIVE"); assert.equal((await check()).body.decision, "STORE_UNAVAILABLE"); });
test("negocio ausente", async () => {
  await seed(); await db.doc("business_requests/http-owner").delete();
  assert.equal((await check()).body.decision, "STORE_UNAVAILABLE");
});
test("relación y estados inconsistentes", async () => {
  await seed(); await db.doc("stores/http-store").update({ businessId: "bad/path" });
  assert.equal((await check()).body.error, "DATA_INCONSISTENT");
  await seed("UNKNOWN"); const result = await check();
  assert.equal(result.response.status, 503); assert.equal(result.body.error, "DATA_INCONSISTENT");
});
test("token ausente o inválido", async () => {
  assert.equal((await check("")).response.status, 401);
  assert.equal((await check("not-a-token")).response.status, 401);
});
test("token revocado", async () => {
  const token = await account("http-revoked");
  await new Promise(resolve => setTimeout(resolve, 1100));
  await auth.revokeRefreshTokens("http-revoked");
  assert.equal((await check(token)).response.status, 401);
});
test("no crea pedidos ni modifica documentos", async () => {
  await seed(); const beforeStore = (await db.doc("stores/http-store").get()).data();
  const beforeBusiness = (await db.doc("business_requests/http-owner").get()).data();
  const ordersBefore = (await db.collection("orders").get()).size;
  await check();
  assert.deepEqual((await db.doc("stores/http-store").get()).data(), beforeStore);
  assert.deepEqual((await db.doc("business_requests/http-owner").get()).data(), beforeBusiness);
  assert.equal((await db.collection("orders").get()).size, ordersBefore);
});
