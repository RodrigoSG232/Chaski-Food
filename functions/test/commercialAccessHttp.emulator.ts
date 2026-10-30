import { connectedHttp } from "./connectedHandler.fixture";
import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { digest } from "../src/access/commercialAccessPolicy";

// Sin credenciales ni fallback remoto: direcciones fijas loopback y proyecto demo.
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const projectId = "demo-chaski-hu15";
const app = initializeApp({ projectId }, "commercial-coordinator-tests");
const auth = getAuth(app); const db = getFirestore(app);
const base = `http://127.0.0.1:5001/${projectId}/southamerica-west1/foodApi/api/v1/access`;
const prefix = `coordinator-${Date.now()}`;
let n = 0; let admin: Account;
type Account = { uid: string; email: string; token: string };
async function account(verified = true, claims = {}): Promise<Account> {
  const uid = `${prefix}-${++n}`; const email = `${uid}@example.test`;
  await auth.createUser({ uid, email, emailVerified: verified, password: "emulator-password" });
  await auth.setCustomUserClaims(uid, claims);
  const response = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }),
  });
  assert.equal(response.status, 200);
  return { uid, email, token: (await response.json() as { idToken: string }).idToken };
}
async function request(actor: Account, body?: Record<string, unknown>) {
  const response = await fetch(`${base}/${body ? "operations" : "facts"}`, {
    method: body ? "POST" : "GET", headers: { Authorization: `Bearer ${actor.token}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(25_000),
  });
  return { status: response.status, body: await response.json() as Record<string, any> };
}
async function op(actor: Account, action: string, revision: number, fields: Record<string, unknown> = {}) {
  return request(actor, { action, expectedRevision: revision, operationId: `${prefix}-op-${++n}`, ...fields });
}
async function prepare(email: string, revision = 0) {
  const r = await op(admin, "PREPARE", revision, { email }); assert.equal(r.status, 200, JSON.stringify(r.body)); return r.body.revision as number;
}
async function owner() {
  const user = await account();
  await db.doc(`business_requests/${user.uid}`).set({ ownerUid: user.uid, status: "APPROVED", businessName: "Ficticio" });
  await db.doc(`stores/${user.uid}-store`).set({ businessId: user.uid, status: "ACTIVE", operationalStatus: "CLOSED" });
  await db.doc(`stores/${user.uid}-second`).set({ businessId: user.uid, status: "INACTIVE", operationalStatus: "PAUSED" });
  await prepare(user.email); return user;
}
async function assign(o: Account, target: Account | { email: string }, revision: number, stores = [`${o.uid}-store`]) {
  return op(o, "ASSIGN", revision, { businessId: o.uid, email: target.email, storeIds: stores, fullName: "Ficticio", phone: "000" });
}
const business = { businessName: "Prueba", ruc: "12345678901", legalAddress: "Lugar publico", phone: "000", email: "contact@example.test", category: "Restaurante" };
before(async () => { admin = await account(true, { admin: true }); });
after(async () => { await db.terminate(); await deleteApp(app); });

test("aprobacion valida y repeticion recuperan una auditoria", async () => {
  const target = await account(); await db.doc(`business_requests/${target.uid}`).set({ ownerUid: target.uid, status: "PENDING_REVIEW" });
  const revision = await prepare(target.email);
  const body = { action: "APPROVE", businessId: target.uid, expectedRevision: revision, operationId: `${prefix}-repeat` };
  const a = await request(admin, body); const b = await request(admin, body);
  assert.equal(a.status, 200); assert.deepEqual(a, b);
  const doc = await db.doc(`business_requests/${target.uid}`).get(); assert.equal(doc.get("status"), "APPROVED");
  assert.equal((await db.doc(`audit_logs/${doc.get("lastAuditId")}`).get()).get("action"), "APROBAR_NEGOCIO");
  assert.equal((await request(admin, { ...body, expectedRevision: revision + 1 })).body.error, "OPERATION_ID_REUSED");
});
test("responsabilidad confirmada rechaza aprobacion y registro reenvio", async () => {
  const o = await owner(); const target = await account(); let revision = await prepare(target.email);
  revision = (await assign(o, target, revision)).body.revision;
  assert.equal((await op(target, "REGISTER", revision, { business })).body.error, "MANAGER_CANNOT_REGISTER");
  await db.doc(`business_requests/${target.uid}`).set({ ownerUid: target.uid, status: "PENDING_REVIEW" });
  assert.equal((await op(admin, "APPROVE", revision, { businessId: target.uid })).body.error, "AUTHORIZATION_CONFLICT");
  await db.doc(`business_requests/${target.uid}`).update({ status: "OBSERVED" });
  assert.equal((await op(target, "RESUBMIT", revision, { business })).body.error, "MANAGER_CANNOT_REGISTER");
});
test("propietario aprobado o suspendido no puede ser asignado", async () => {
  const o = await owner(); const target = await owner();
  assert.equal((await assign(o, target, 1)).body.error, "AUTHORIZATION_CONFLICT");
  await db.doc(`business_requests/${target.uid}`).update({ status: "SUSPENDED" });
  assert.equal((await assign(o, target, 1)).body.error, "AUTHORIZATION_CONFLICT");
  const facts = await request(target); assert.equal(facts.status, 200); assert.equal(facts.body.ownership.status, "SUSPENDED");
});
test("correo sin cuenta se asigna y vincula posteriormente verificado", async () => {
  const o = await owner(); const email = `${prefix}-future@example.test`; let revision = await prepare(email);
  const pending = await assign(o, { email }, revision); assert.equal(pending.status, 200); revision = pending.body.revision;
  const ref = db.doc(`store_managers/${o.uid}_${email}`); assert.equal((await ref.get()).get("bindingState"), "PENDING");
  const uid = `${prefix}-future`; await auth.createUser({ uid, email, emailVerified: true, password: "emulator-password" });
  // Preparar UID derivado, preservando la invitacion y sin vincularla por coincidencia de texto.
  revision = await prepare(email, revision);
  const login = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }) });
  const target = { uid, email, token: (await login.json() as { idToken: string }).idToken };
  assert.equal((await request(target)).body.error, "RECONCILIATION_REQUIRED");
  assert.equal((await op(target, "REGISTER", revision, { business })).body.error, "RECONCILIATION_REQUIRED");
  assert.equal((await op(target, "BIND", revision)).status, 200);
  const facts = await request(target); assert.equal(facts.status, 200); assert.equal(facts.body.assignments[0].boundUid, uid);
});
test("binding UID ajeno no permite apropiacion", async () => {
  const o = await owner(); const target = await account(); let revision = await prepare(target.email);
  revision = (await assign(o, target, revision)).body.revision;
  await db.doc(`store_managers/${o.uid}_${target.email}`).update({ boundUid: "another-uid" });
  revision = await prepare(target.email, revision);
  assert.equal((await op(target, "BIND", revision)).body.error, "BINDING_OWNED_BY_ANOTHER_UID");
});
test("varios locales retirada parcial y total conservan registros", async () => {
  const o = await owner(); const target = await account(); let revision = await prepare(target.email);
  revision = (await assign(o, target, revision, [`${o.uid}-store`, `${o.uid}-second`])).body.revision;
  assert.equal((await request(target)).body.assignments[0].assignedStoreIds.length, 2);
  revision = (await assign(o, target, revision, [`${o.uid}-second`])).body.revision;
  assert.deepEqual((await request(target)).body.assignments[0].assignedStoreIds, [`${o.uid}-second`]);
  assert.equal((await op(o, "REVOKE", revision, { businessId: o.uid, email: target.email })).status, 200);
  assert.equal((await request(target)).body.assignments.length, 0);
  assert.equal((await db.doc(`store_managers/${o.uid}_${target.email}`).get()).get("bindingState"), "REVOKED");
});
test("invitacion pendiente no provoca falso conflicto de propietario", async () => {
  const o = await owner(); const target = await account(false); let revision = await prepare(target.email);
  revision = (await assign(o, target, revision)).body.revision;
  await db.doc(`business_requests/${target.uid}`).set({ ownerUid: target.uid, status: "PENDING_REVIEW" });
  assert.equal((await op(admin, "APPROVE", revision, { businessId: target.uid })).status, 200);
  const facts = await request(target); assert.equal(facts.status, 200); assert.equal(facts.body.ownership.status, "APPROVED"); assert.equal(facts.body.assignments.length, 0);
});
test("aprobacion y asignacion concurrentes comparten revision y solo una confirma", async () => {
  for (let iteration = 0; iteration < 3; iteration++) {
    const o = await owner(); const target = await account(); const revision = await prepare(target.email);
    await db.doc(`business_requests/${target.uid}`).set({ ownerUid: target.uid, status: "PENDING_REVIEW" });
    const [approval, assignment] = await Promise.all([op(admin, "APPROVE", revision, { businessId: target.uid }), assign(o, target, revision)]);
    assert.equal([approval, assignment].filter(r => r.status === 200).length, 1);
    assert.ok([approval, assignment].some(r => r.status === 409));
    const approved = (await db.doc(`business_requests/${target.uid}`).get()).get("status") === "APPROVED";
    const assigned = (await db.doc(`store_managers/${o.uid}_${target.email}`).get()).get("bindingState") === "VERIFIED";
    assert.notEqual(approved, assigned);
  }
});
test("revision antigua y error de permisos no escriben asignacion", async () => {
  const o = await owner(); const target = await account(); await prepare(target.email);
  assert.equal((await assign(o, target, 0)).body.error, "STALE_REVISION");
  assert.equal((await op(target, "ASSIGN", 1, { businessId: o.uid, email: target.email, storeIds: [`${o.uid}-store`], fullName: "Prueba", phone: "0" })).status, 403);
  assert.equal((await db.doc(`store_managers/${o.uid}_${target.email}`).get()).exists, false);
});
test("tokens anteriores al cambio de correo o verificacion no autorizan", async () => {
  const target = await account(); await prepare(target.email);
  await auth.updateUser(target.uid, { email: `${target.uid}-changed@example.test`, emailVerified: false });
  assert.equal((await request(target)).status, 401);
});
test("sin correo verificado no se vincula", async () => {
  const target = await account(false); const revision = await prepare(target.email);
  assert.equal((await op(target, "BIND", revision)).status, 403);
});
test("registro y reenvio permitidos mantienen contrato HU05", async () => {
  const target = await account(); let revision = await prepare(target.email);
  const first = await op(target, "REGISTER", revision, { business }); assert.equal(first.status, 200); revision = first.body.revision;
  const ref = db.doc(`business_requests/${target.uid}`); assert.equal((await ref.get()).get("ownerUid"), target.uid);
  await ref.update({ status: "OBSERVED" });
  assert.equal((await op(target, "RESUBMIT", revision, { business: { ...business, businessName: "Corregido" } })).status, 200);
  assert.equal((await ref.get()).get("status"), "PENDING_REVIEW");
});
test("legacy requiere actualizacion autorizada y reparacion derivada no borra fuentes", async () => {
  const o = await owner(); const target = await account(); const ref = db.doc(`store_managers/${o.uid}_${target.email}`);
  await ref.set({ businessId: o.uid, email: target.email, assignedStoreIds: [`${o.uid}-store`], fullName: "Legacy" });
  assert.equal((await request(target)).body.error, "PREPARATION_REQUIRED");
  let revision = await prepare(target.email); assert.equal((await request(target)).body.error, "RECONCILIATION_REQUIRED");
  assert.equal((await ref.get()).get("bindingState"), undefined);
  revision = (await assign(o, target, revision)).body.revision;
  await db.doc(`commercial_access_index/email_${digest(target.email)}`).update({ sourceHash: "damaged" });
  assert.equal((await request(target)).body.error, "INDEX_INCONSISTENT");
  await prepare(target.email, revision);
  assert.equal((await request(target)).status, 200); assert.equal((await ref.get()).exists, true);
});
test("rechazo no crea pedidos ni cambia carrito", async () => {
  const target = await account(); const before = (await db.collection("orders").get()).size;
  const revision = await prepare(target.email);
  assert.equal((await op(target, "APPROVE", revision, { businessId: target.uid })).status, 403);
  assert.equal((await db.collection("orders").get()).size, before);
});

test("cambio correo requiere retirada autorizada preparacion y nueva asignacion sin transferencia", async () => {
  const o = await owner(); const target = await account(); let revision = await prepare(target.email);
  revision = (await assign(o, target, revision)).body.revision;
  const email = `${target.uid}-new@example.test`;
  await auth.updateUser(target.uid, { email, emailVerified: true });
  assert.equal((await request(target)).status, 401);
  assert.equal((await op(admin, "PREPARE", 0, { email })).body.error, "RECONCILIATION_REQUIRED");
  const removed = await op(o, "REVOKE", revision, { businessId: o.uid, email: target.email }); assert.equal(removed.status, 200, JSON.stringify(removed.body));
  const newRevision = await prepare(email);
  const old = await db.doc(`store_managers/${o.uid}_${target.email}`).get(); assert.equal(old.get("bindingState"), "REVOKED");
  assert.equal((await db.doc(`store_managers/${o.uid}_${email}`).get()).exists, false);
  assert.equal((await assign(o, { email }, newRevision)).status, 200);
  assert.equal((await old.ref.get()).get("bindingState"), "REVOKED");
});
test("propietario obtiene revision necesaria sin leer hechos privados de destinatario", async () => {
  const o = await owner(); const target = await account(); const revision = await prepare(target.email);
  const response = await fetch(`${base}/assignment-revision`, { method: "POST", headers: { Authorization: `Bearer ${o.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ businessId: o.uid, email: target.email }) });
  assert.equal(response.status, 200); assert.deepEqual(await response.json(), { revision });
});

test("local ajeno rechaza sin cambios parciales de indice ni asignacion", async () => {
  const o = await owner(); const target = await account(); const revision = await prepare(target.email);
  const rejected = await assign(o, target, revision, [`${o.uid}-store`, "not-owned"]); assert.equal(rejected.status, 403);
  assert.equal((await request(target)).body.revision, revision);
  assert.equal((await db.doc(`store_managers/${o.uid}_${target.email}`).get()).exists, false);
});
test("indice privado no es legible directamente ni con claim admin cliente", async () => {
  const target = await account(); await prepare(target.email);
  for (const actor of [target, admin]) {
    const response = await fetch(`http://127.0.0.1:8089/v1/projects/${projectId}/databases/(default)/documents/commercial_access_index/uid_${target.uid}`, { headers: { Authorization: `Bearer ${actor.token}` } });
    assert.equal(response.status, 403);
  }
});

test("recuperar exito previo no requiere que el destinatario siga en Auth", async () => {
  const target = await account(); await db.doc(`business_requests/${target.uid}`).set({ ownerUid: target.uid, status: "PENDING_REVIEW" });
  const revision = await prepare(target.email);
  const body = { action: "APPROVE", businessId: target.uid, expectedRevision: revision, operationId: `${prefix}-lost-response` };
  const first = await request(admin, body); assert.equal(first.status, 200);
  await auth.deleteUser(target.uid);
  assert.deepEqual(await request(admin, body), first);
  assert.equal((await db.doc(`business_requests/${target.uid}`).get()).get("status"), "APPROVED");
});

test("revision propia y de aprobacion no requieren correo comercial ni hechos resueltos", async () => {
  const candidate = await account(); const revision = await prepare(candidate.email);
  const own = await fetch(`${base}/revision`, { headers: { Authorization: `Bearer ${candidate.token}` } });
  assert.equal(own.status, 200); assert.deepEqual(await own.json(), { revision });
  const response = await fetch(`${base}/assignment-revision`, { method: "POST", headers: { Authorization: `Bearer ${admin.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ businessId: candidate.uid }) });
  assert.equal(response.status, 200); assert.deepEqual(await response.json(), { revision });
});
test("actualizar correo asignacion es atomico y conserva fila anterior", async () => {
  const o = await owner(); const old = await account(); const next = await account();
  let oldRevision = await prepare(old.email); oldRevision = (await assign(o, old, oldRevision)).body.revision;
  const revision = await prepare(next.email);
  const r = await op(o, "ASSIGN", revision, { businessId: o.uid, email: next.email, previousEmail: old.email, previousRevision: oldRevision, storeIds: [`${o.uid}-store`], fullName: "Actualizado", phone: "0" });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await db.doc(`store_managers/${o.uid}_${old.email}`).get()).get("bindingState"), "REVOKED");
  assert.equal((await db.doc(`store_managers/${o.uid}_${next.email}`).get()).get("boundUid"), next.uid);
});
test("cambio correo rechazado no revoca asignacion anterior", async () => {
  const o = await owner(); const old = await account(); const next = await owner();
  let oldRevision = await prepare(old.email); oldRevision = (await assign(o, old, oldRevision)).body.revision;
  const r = await op(o, "ASSIGN", 1, { businessId: o.uid, email: next.email, previousEmail: old.email, previousRevision: oldRevision, storeIds: [`${o.uid}-store`], fullName: "Prueba", phone: "0" });
  assert.equal(r.body.error, "AUTHORIZATION_CONFLICT");
  assert.equal((await db.doc(`store_managers/${o.uid}_${old.email}`).get()).get("bindingState"), "VERIFIED");
});


test("facts propietario aprobado incluye varios locales propios y excluye ajenos", async () => {
  const o = await owner(); const other = await owner();
  const r = await request(o); assert.equal(r.status, 200);
  assert.deepEqual(r.body.stores.map((s: any) => s.storeId).sort(), [`${o.uid}-second`, `${o.uid}-store`].sort());
  assert(r.body.stores.every((s: any) => s.businessId === o.uid && s.businessStatus === "APPROVED"));
  assert(r.body.stores.every((s: any) => Object.keys(s).sort().join(",") === "businessId,businessStatus,operationalStatus,status,storeId"));
  assert.equal(r.body.stores.some((s: any) => s.businessId === other.uid), false);
  assert.deepEqual(r.body.assignments, []);
});
test("facts propietario suspendido conserva propiedad sin listado de locales", async () => {
  const o = await owner(); await db.doc(`business_requests/${o.uid}`).update({ status: "SUSPENDED", suspensionReason: "Prueba" });
  const r = await request(o); assert.equal(r.status, 200);
  assert.equal(r.body.ownership.status, "SUSPENDED"); assert.equal(r.body.ownership.suspensionReason, "Prueba");
  assert.deepEqual(r.body.stores, []); assert.deepEqual(r.body.assignments, []);
});

test("facts conflicto confirmado no devuelve autorizaciones parciales", async () => {
  const o = await owner(); const manager = await account(); const rev = await prepare(manager.email);
  assert.equal((await assign(o, manager, rev)).status, 200);
  await db.doc(`business_requests/${manager.uid}`).set({ ownerUid: manager.uid, status: "APPROVED" });
  const r = await request(manager); assert.equal(r.status, 409); assert.deepEqual(r.body, { error: "AUTHORIZATION_CONFLICT" });
});
test("facts propietario con estado de local inconsistente falla cerrado", async () => {
  const o = await owner(); await db.doc(`stores/${o.uid}-store`).update({ status: "UNKNOWN" });
  const r = await request(o); assert.equal(r.status, 503); assert.deepEqual(r.body, { error: "DATA_INCONSISTENT" });
});


test("CLOUD coordinator shares identity, suspension, scoped stores and admin checks",async()=>{
  const send=connectedHttp(app),approved=await owner(),ordinary=await account();await prepare(ordinary.email);
  assert.equal((await send(undefined,"/access/facts")).status,401);
  const facts=await send(approved.token,"/access/facts");assert.equal(facts.status,200);
  assert.equal(facts.body.ownership.status,"APPROVED");assert.equal(facts.body.stores.length,2);
  assert.ok(facts.body.stores.every((store:any)=>store.businessId===approved.uid));
  assert.equal((await send(ordinary.token,"/access/operations",{action:"PREPARE",email:ordinary.email,expectedRevision:1,operationId:`cloud-${++n}`})).status,403);
  await db.doc(`business_requests/${approved.uid}`).update({status:"SUSPENDED"});
  const suspended=await send(approved.token,"/access/facts");assert.equal(suspended.body.ownership.status,"SUSPENDED");
  assert.deepEqual(suspended.body.stores,[]);
  await auth.updateUser(ordinary.uid,{emailVerified:false});
  assert.equal((await send(ordinary.token,"/access/facts")).status,401);
});
