import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15"; const prefix = `rules34-${Date.now()}`;
const app = initializeApp({ projectId: project }, "commercial-rules-tests");
const db = getFirestore(app); const auth = getAuth(app);
const root = `projects/${project}/databases/(default)/documents/`;
const rest = `http://127.0.0.1:8089/v1/${root}`;
const backend = `http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1/access`;
type Account = { uid: string; email: string; token: string };
let sequence = 0; let admin: Account; let owner: Account; let manager: Account; let other: Account; let store: string;
async function account(verified = true, claims = {}): Promise<Account> {
  const uid = `${prefix}-${++sequence}`; const email = `${uid}@example.test`;
  await auth.createUser({ uid, email, emailVerified: verified, password: "emulator-password" }); await auth.setCustomUserClaims(uid, claims);
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200); return { uid, email, token: (await r.json() as { idToken: string }).idToken };
}
async function operation(actor: Account, action: string, expectedRevision: number, fields = {}) {
  const r = await fetch(`${backend}/operations`, { method: "POST", headers: { Authorization: `Bearer ${actor.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ action, operationId: `${prefix}-op-${++sequence}`, expectedRevision, ...fields }) });
  const body = await r.json() as any; assert.equal(r.status, 200, JSON.stringify(body)); return body.revision as number;
}
function value(v: any): any {
  if (v === null) return { nullValue: null }; if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return { integerValue: String(v) }; if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  if (v && typeof v.toDate === "function") return { timestampValue: v.toDate().toISOString() };
  if (typeof v === "object") return { mapValue: { fields: fields(v) } }; return { stringValue: v };
}
function fields(data: any): any { return Object.fromEntries(Object.entries(data).map(([k, v]) => [k, value(v)])); }
async function write(actor: Account, path: string, data: any, transforms: string[] = []) {
  const r = await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents:commit`, { method: "POST", headers: { Authorization: `Bearer ${actor.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ writes: [{ update: { name: root + path, fields: fields(data) }, updateTransforms: transforms.map(fieldPath => ({ fieldPath, setToServerValue: "REQUEST_TIME" })) }] }) });
  return r.status;
}
async function read(actor: Account, path: string) { return (await fetch(rest + path, { headers: { Authorization: `Bearer ${actor.token}` } })).status; }
async function query(actor: Account, filters: [string, string][]) {
  return (await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents:runQuery`, { method: "POST", headers: { Authorization: `Bearer ${actor.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "store_managers" }], where: { compositeFilter: { op: "AND", filters: filters.map(([fieldPath, v]) => ({ fieldFilter: { field: { fieldPath }, op: "EQUAL", value: value(v) } })) } } } }) })).status;
}
before(async () => {
  admin = await account(true, { admin: true }); owner = await account(); manager = await account(); other = await account(); store = `${owner.uid}-store`;
  await db.doc(`business_requests/${owner.uid}`).set({ ownerUid: owner.uid, status: "APPROVED", businessName: "Ficticio", ruc: "12345678901" });
  await db.doc(`stores/${store}`).set({ businessId: owner.uid, status: "ACTIVE", operationalStatus: "OPEN", imageUrl: "" });
  await operation(admin, "PREPARE", 0, { email: owner.email });
  let revision = await operation(admin, "PREPARE", 0, { email: manager.email });
  await operation(owner, "ASSIGN", revision, { businessId: owner.uid, email: manager.email, storeIds: [store], fullName: "Prueba", phone: "0" });
});
after(async () => { await db.terminate(); await deleteApp(app); });

test("registros reenvios y aprobaciones directos antiguos se rechazan aun con payload valido", async () => {
  const candidate = await account();
  const data = { ownerUid: candidate.uid, status: "PENDING_REVIEW", businessName: "Prueba", ruc: "12345678901", legalAddress: "Lima", phone: "0", email: "contact@example.test", category: "Restaurante", observations: null, suspensionReason: null, reviewedBy: null, reviewedAt: null };
  assert.equal(await write(candidate, `business_requests/${candidate.uid}`, data, ["createdAt", "updatedAt"]), 403);
  await db.doc(`business_requests/${candidate.uid}`).set({ ...data, status: "OBSERVED", createdAt: new Date(), updatedAt: new Date() });
  const observed = (await db.doc(`business_requests/${candidate.uid}`).get()).data();
  assert.equal(await write(candidate, `business_requests/${candidate.uid}`, { ...observed, status: "PENDING_REVIEW" }, ["updatedAt"]), 403);
  await db.doc(`business_requests/${candidate.uid}`).update({ status: "PENDING_REVIEW" });
  const pending = (await db.doc(`business_requests/${candidate.uid}`).get()).data();
  const auditId = `${prefix}-forged-approval`;
  const writes = [{ update: { name: root + `business_requests/${candidate.uid}`, fields: fields({ ...pending, status: "APPROVED", reviewedBy: admin.email, lastAuditId: auditId }) }, updateTransforms: ["reviewedAt", "updatedAt"].map(fieldPath => ({ fieldPath, setToServerValue: "REQUEST_TIME" })) }, { update: { name: root + `audit_logs/${auditId}`, fields: fields({ adminUid: admin.uid, adminEmail: admin.email, targetBusinessId: candidate.uid, previousStatus: "PENDING_REVIEW", newStatus: "APPROVED" }) }, updateTransforms: [{ fieldPath: "timestamp", setToServerValue: "REQUEST_TIME" }] }];
  const response = await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents:commit`, { method: "POST", headers: { Authorization: `Bearer ${admin.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ writes }) });
  assert.equal(response.status, 403);
  assert.equal((await db.doc(`business_requests/${candidate.uid}`).get()).get("status"), "PENDING_REVIEW");
});
test("cliente no asigna actualiza ni borra responsables directamente", async () => {
  const path = `store_managers/${owner.uid}_${manager.email}`; const original = (await db.doc(path).get()).data();
  assert.equal(await write(owner, path, { ...original, assignedStoreIds: [] }), 403);
  assert.equal(await write(admin, `${path}-new`, { businessId: owner.uid, email: other.email, assignedStoreIds: [store] }), 403);
  assert.equal((await fetch(rest + path, { method: "DELETE", headers: { Authorization: `Bearer ${owner.token}` } })).status, 403);
});
test("indice privado rechaza lectura y escritura de cualquier cliente", async () => {
  for (const actor of [owner, manager, admin]) {
    assert.equal(await read(actor, `commercial_access_index/uid_${owner.uid}`), 403);
    assert.equal(await write(actor, `commercial_access_index/uid_${owner.uid}`, { kind: "UID" }), 403);
  }
});
test("consultas legitimas propietario y responsable cumplen reglas sin lectura amplia", async () => {
  assert.equal(await query(owner, [["businessId", owner.uid]]), 200);
  assert.equal(await query(manager, [["email", manager.email], ["boundUid", manager.uid], ["bindingState", "VERIFIED"]]), 200);
  assert.equal(await query(other, [["businessId", owner.uid]]), 403);
  assert.equal(await query(manager, [["email", manager.email]]), 403);
  assert.equal(await read(manager, `business_requests/${owner.uid}`), 200);
  assert.equal(await read(other, `business_requests/${owner.uid}`), 403);
});
test("correo no verificado y binding legacy no conceden lecturas operativas", async () => {
  const unverified = await account(false); const legacy = await account();
  for (const actor of [unverified, legacy]) {
    await db.doc(`store_managers/${owner.uid}_${actor.email}`).set({ businessId: owner.uid, email: actor.email, assignedStoreIds: [store] });
    assert.equal(await read(actor, `store_managers/${owner.uid}_${actor.email}`), 403);
    assert.equal(await read(actor, `business_requests/${owner.uid}`), 403);
  }
});
test("HU09 horarios y estado mantienen autorizacion por local", async () => {
  assert.equal(await write(manager, `stores/${store}`, { businessId: owner.uid, status: "ACTIVE", operationalStatus: "PAUSED", imageUrl: "", pauseReason: "Prueba" }), 200);
  assert.equal(await write(other, `stores/${store}`, { businessId: owner.uid, status: "ACTIVE", operationalStatus: "CLOSED", imageUrl: "" }), 403);
});
test("HU10 propietario administra catalogo y HU11 responsable configura oferta", async () => {
  const product = `${prefix}-product`;
  assert.equal(await write(owner, `products/${product}`, { businessId: owner.uid, price: 10, prepTimeMinutes: 5, imageUrl: "" }), 200);
  const config = { businessId: owner.uid, storeId: store, productId: product, price: 12, availability: "AVAILABLE", updatedBy: manager.uid };
  assert.equal(await write(manager, `store_product_configs/${store}_${product}`, config, ["updatedAt"]), 200);
  assert.equal(await write(other, `store_product_configs/${store}_${product}`, { ...config, updatedBy: other.uid }, ["updatedAt"]), 403);
});
test("HU07 suspension y reactivacion administrativa auditadas siguen permitidas", async () => {
  for (const [from, to] of [["APPROVED", "SUSPENDED"], ["SUSPENDED", "APPROVED"]]) {
    const id = `${prefix}-audit-${to}`;
    const business = { ownerUid: owner.uid, businessName: "Ficticio", ruc: "12345678901", status: to, suspensionReason: to === "SUSPENDED" ? "Prueba" : null, reviewedBy: admin.email, lastAuditId: id };
    const audit = { adminUid: admin.uid, adminEmail: admin.email, targetBusinessId: owner.uid, previousStatus: from, newStatus: to };
    const writes = [{ update: { name: root + `business_requests/${owner.uid}`, fields: fields(business) }, updateTransforms: ["reviewedAt", "updatedAt"].map(fieldPath => ({ fieldPath, setToServerValue: "REQUEST_TIME" })) }, { update: { name: root + `audit_logs/${id}`, fields: fields(audit) }, updateTransforms: [{ fieldPath: "timestamp", setToServerValue: "REQUEST_TIME" }] }];
    const r = await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents:commit`, { method: "POST", headers: { Authorization: `Bearer ${admin.token}`, "Content-Type": "application/json" }, body: JSON.stringify({ writes }) });
    assert.equal(r.status, 200, JSON.stringify(await r.json()));
    if (to === "SUSPENDED") {
      assert.equal(await read(owner, `business_requests/${owner.uid}`), 200);
      assert.equal(await write(owner, `stores/${store}`, { businessId: owner.uid, status: "ACTIVE", operationalStatus: "CLOSED", imageUrl: "" }), 403);
      assert.equal(await write(manager, `store_product_configs/${store}_missing`, { businessId: owner.uid, storeId: store, productId: "missing", price: 10, availability: "AVAILABLE", updatedBy: manager.uid }, ["updatedAt"]), 403);
    }
  }
});
test("revocacion invalida lectura y operacion incluso con token anterior", async () => {
  const rev = (await db.doc(`commercial_access_index/uid_${manager.uid}`).get()).get("revision");
  await operation(owner, "REVOKE", rev, { businessId: owner.uid, email: manager.email });
  assert.equal(await read(manager, `store_managers/${owner.uid}_${manager.email}`), 403);
  assert.equal(await read(manager, `business_requests/${owner.uid}`), 403);
  assert.equal(await write(manager, `stores/${store}`, { businessId: owner.uid, status: "ACTIVE", operationalStatus: "CLOSED", imageUrl: "" }), 403);
  assert.equal((await db.doc(`store_managers/${owner.uid}_${manager.email}`).get()).exists, true);
});

test("HU34 mantiene direcciones personales por UID sin privilegio administrativo transversal", async () => {
  const actors = [owner, manager, other, admin];
  const paths = actors.map(actor => `users/${actor.uid}/addresses/hu34-personal`);
  for (const path of paths) {
    await db.doc(path).set({ label: "Prueba local", addressText: "Plaza pública de Lima", location: { lat: -12.0464, lng: -77.0428 },
      reference: "", instructions: "", createdAt: new Date(), updatedAt: new Date() });
  }
  const original = (await db.doc(`business_requests/${owner.uid}`).get()).get("status");
  try {
    await db.doc(`business_requests/${owner.uid}`).update({ status: "SUSPENDED" });
    for (let i = 0; i < actors.length; i++) {
      for (let j = 0; j < paths.length; j++) {
        assert.equal(await read(actors[i]!, paths[j]!), i === j ? 200 : 403);
      }
    }
  } finally {
    await db.doc(`business_requests/${owner.uid}`).update({ status: original });
    await Promise.all(paths.map(path => db.doc(path).delete()));
  }
});