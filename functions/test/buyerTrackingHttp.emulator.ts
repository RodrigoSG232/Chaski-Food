import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { hu23Event, hu23Order, hu23Sample } from "./lab/hu23Fixture";
import { seedBase } from "./lab/labData";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { join } from "node:path";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15", prefix = "hu23-" + Date.now();
const app = initializeApp({ projectId: project }, prefix), db = getFirestore(app), auth = getAuth(app);
const base = "http://127.0.0.1:5001/" + project + "/southamerica-west1/";
let token: string, otherToken: string, count = 0;
const uid = prefix + "-buyer";

async function account(id: string) {
  const email = id + "@example.test";
  await auth.createUser({ uid: id, email, password: "emulator-password", emailVerified: true });
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200); return (await r.json() as any).idToken as string;
}
before(async () => { token = await account(uid); otherToken = await account(prefix + "-other"); });
after(async () => { await db.terminate(); await deleteApp(app); });

async function call(path: string, method = "GET", body?: unknown, bearer?: string) {
  const r = await fetch(base + path, { method, headers: { "Content-Type": "application/json",
    ...(bearer ? { Authorization: "Bearer " + bearer } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) });
  return { status: r.status, body: await r.json() as any };
}
const read = (id: string, bearer = token) => call("foodApi/api/v1/me/orders/" + id + "/tracking", "GET", undefined, bearer);
const event = (body: unknown) => call("riderApi/integration/v1/rider-events", "POST", body);
const gps = (id: string, body: unknown) => call("riderApi/integration/v1/orders/" + id + "/tracking", "PUT", body);
async function seed() {
  const id = createHash("sha256").update(prefix + (++count)).digest("hex"), ref = db.doc("food_orders/" + id);
  const order = hu23Order(id, uid);
  await ref.set(order);
  for (const [i, status] of ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP"].entries())
    await ref.collection("status_history").doc("initial-" + i).set({ schemaVersion: 1, toStatus: status,
      fromStatus: i ? ["PENDING_CONFIRMATION", "PREPARING"][i - 1] : null, actorUid: uid,
      occurredAt: Timestamp.fromMillis(order.createdAt.toMillis() + i) });
  return id;
}
async function move(id: string) {
  assert.equal((await event(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  assert.equal((await event(hu23Event(id, "PICKED_UP", 2))).status, 200);
}

test("own buyer only, authentication and strict route; no Rider means no map", async () => {
  const id = await seed(), r = await read(id);
  assert.equal(r.status, 200); assert.equal(r.body.stage, "SEARCHING_RIDER");
  assert.equal(r.body.position, null); assert.equal(r.body.estimatedArrivalAtEpochMillis, null);
  assert.equal((await read(id, "")).status, 401);
  assert.deepEqual(await read(id, otherToken), await read("f".repeat(64)));
  assert.equal((await read(id, otherToken)).status, 404);
  assert.equal((await call("foodApi/api/v1/me/orders/" + id + "/tracking?x=1", "GET", undefined, token)).status, 400);
  assert.equal((await call("foodApi/api/v1/me/orders/" + id + "/tracking", "POST", {}, token)).status, 405);
});

test("acceptance, pickup, GPS/ETA, temporary loss and valid delivery through real HTTP handlers", async () => {
  const id = await seed(), accept = hu23Event(id, "ASSIGNMENT_ACCEPTED", 1);
  assert.equal((await event(accept)).status, 200);
  assert.equal((await event(accept)).status, 200);
  assert.equal((await read(id)).body.stage, "ACCEPTED");
  assert.equal((await gps(id, hu23Sample(1))).status, 409);
  assert.equal((await event(hu23Event(id, "COMPLETED_SUCCESSFULLY", 2))).status, 409);
  assert.equal((await event(hu23Event(id, "PICKED_UP", 2))).status, 200);
  assert.equal((await read(id)).body.position, null);
  const sample = hu23Sample(1);
  assert.equal((await gps(id, sample)).status, 200);
  const current = await read(id);
  assert.equal(current.body.estimatedArrivalAtEpochMillis, Date.parse(sample.estimatedArrivalAt));
  assert.equal(current.body.position.latitude, sample.latitude);
  for (const secret of ["pickupCode", "riderId", "phone", "assignment", "buyerUid"])
    assert.equal(secret in current.body, false, secret);
  assert.deepEqual((await read(id)).body.position, current.body.position);
  assert.equal((await event(hu23Event(id, "ARRIVED_AT_CUSTOMER", 3))).status, 200);
  assert.deepEqual((await read(id)).body.position, current.body.position);
  const delivered = hu23Event(id, "COMPLETED_SUCCESSFULLY", 4);
  assert.equal((await event(delivered)).status, 200); assert.equal((await event(delivered)).status, 200);
  const done = await read(id);
  assert.equal(done.body.status, "COMPLETED"); assert.equal(done.body.deliveryOutcome, "SUCCESS");
  assert.equal(done.body.position, null); assert.equal(done.body.estimatedArrivalAtEpochMillis, null);
  assert.equal((await gps(id, hu23Sample(2))).status, 409);
  assert.equal((await db.doc("food_orders/" + id).collection("status_history").get()).size, 5);
});

test("late/duplicate GPS cannot roll back location; missing ETA is supported; future samples rejected", async () => {
  const id = await seed(); await move(id);
  const first = hu23Sample(2, Date.now() - 180_000);
  assert.equal((await gps(id, first)).status, 200);
  assert.equal((await read(id)).body.position.recordedAtEpochMillis, Date.parse(first.recordedAt));
  assert.equal((await gps(id, first)).body.received, true);
  assert.equal((await gps(id, { ...first, latitude: -13 })).status, 409);
  assert.equal((await gps(id, hu23Sample(1))).body.received, false);
  assert.equal((await gps(id, hu23Sample(3, Date.now() - 240_000))).body.received, false);
  assert.equal((await gps(id, hu23Sample(3, Date.now() + 120_000))).status, 422);
  const { estimatedArrivalAt, ...legacy } = hu23Sample(3);
  assert.equal((await gps(id, legacy)).status, 200);
  assert.equal((await read(id)).body.estimatedArrivalAtEpochMillis, null);
});

test("assignment replacement requires release and failed delivery is never labelled successful", async () => {
  const id = await seed();
  assert.equal((await event(hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  assert.equal((await event(hu23Event(id, "ASSIGNMENT_ACCEPTED", 2, 2))).status, 409);
  assert.equal((await event(hu23Event(id, "ASSIGNMENT_RELEASED", 2))).status, 200);
  assert.equal((await read(id)).body.assigned, false);
  assert.equal((await event(hu23Event(id, "ASSIGNMENT_ACCEPTED", 3, 2))).status, 200);
  assert.equal((await event(hu23Event(id, "PICKED_UP", 4, 2))).status, 200);
  assert.equal((await gps(id, hu23Sample(1))).status, 409);
  assert.equal((await gps(id, hu23Sample(1, Date.now(), 2))).status, 200);
  assert.equal((await event(hu23Event(id, "COMPLETED_UNSUCCESSFULLY", 5, 2))).status, 200);
  assert.equal((await read(id)).body.deliveryOutcome, "UNSUCCESSFUL");
  const detail = await call("foodApi/api/v1/me/orders/" + id, "GET", undefined, token);
  assert.equal(detail.status, 200, JSON.stringify(detail.body));
  assert.equal(detail.body.order.deliveryOutcome, "UNSUCCESSFUL");
  const list = await call("foodApi/api/v1/me/orders?phase=HISTORICAL", "GET", undefined, token);
  assert.equal(list.status, 200);
  assert.equal(list.body.orders.find((o: any) => o.orderId === id).deliveryOutcome, "UNSUCCESSFUL");
});

test("LOCAL simulator drives the same API and refuses a non-demo environment", async () => {
  await seedBase(db, auth);
  const run = promisify(execFile), script = join(__dirname, "lab/hu23Simulation.js");
  const env = { ...process.env, GCLOUD_PROJECT: project };
  for (const action of ["prepare", "assign", "pickup", "move", "no-eta", "finish"]) {
    const result = await run(process.execPath, [script, action], { env, timeout: 30_000 });
    assert.match(result.stdout, /HU23_LOCAL/);
  }
  const id = createHash("sha256").update("lab-order-hu23").digest("hex");
  assert.equal((await db.doc("food_orders/" + id).get()).data()?.status, "COMPLETED");
  await assert.rejects(run(process.execPath, [script, "prepare"], {
    env: { ...env, GCLOUD_PROJECT: "non-demo-project" }, timeout: 10_000 }), /LOCAL_EMULATORS_REQUIRED/);
});
