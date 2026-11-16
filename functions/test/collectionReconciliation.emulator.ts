import { connectedHttp } from "./connectedHandler.fixture";
import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { createHash, randomUUID } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";
import { hu23Event, hu23Order, hu23Verification } from "./lab/hu23Fixture";

/**
 * E5.4a — admin reconciliation over LOCAL emulators only (synthetic data). The discrepancy is produced by the real
 * riderApi E4 flow; Food's expected total in the HU23 fixture is S/13.00 (1300), so the A–E scenarios use it in place
 * of S/87.00: B declares 600/confirms 1300, C declares 600/confirms 600, D/E declare and confirm 1600 (excess 300).
 */
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15", prefix = `e54-${Date.now()}`;
const app = initializeApp({ projectId: project }, prefix), db = getFirestore(app), auth = getAuth(app);
const access = new CommercialAccessService(db, auth), send = connectedHttp(app);
const riderBase = `http://127.0.0.1:5001/${project}/southamerica-west1/riderApi/integration/v1/`;
type Actor = CommercialIdentity & { token: string };
let n = 0, admin: Actor, admin2: Actor, buyer: Actor, plain: Actor, unverifiedAdmin: Actor;

async function actor(isAdmin = false, verified = true): Promise<Actor> {
  const uid = `${prefix}-${++n}`;
  await auth.createUser({ uid, email: `${uid}@example.test`, password: "emulator-password", emailVerified: verified });
  if (isAdmin) await auth.setCustomUserClaims(uid, { admin: true });
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: `${uid}@example.test`, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200);
  return { uid, email: `${uid}@example.test`, emailVerified: verified, admin: isAdmin, token: (await r.json() as { idToken: string }).idToken };
}
async function prepare(who: Actor) {
  await access.execute(admin, { action: "PREPARE", operationId: `prepare-${++n}`, email: who.email, expectedRevision: 0 });
}
async function rider(path: string, method = "GET", body?: unknown) {
  const r = await fetch(riderBase + path, { method, headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) });
  return { status: r.status, body: await r.json() as any };
}
const cash = (amountMinor: number) => ({ method: "CASH", amountMinor, currency: "PEN" });
/** Real E4 flow: assignment, verified pickup, arrival and completion declaring [declared]. */
async function delivered(declared: number) {
  const id = createHash("sha256").update(`${prefix}-order-${++n}`).digest("hex");
  const { paymentSimulation: _ignored, ...order } = hu23Order(id, buyer.uid);
  await db.doc(`food_orders/${id}`).set({ ...order, paymentStatus: "PENDING", readyAt: Timestamp.fromMillis(Date.now() - 60_000) });
  assert.equal((await rider("rider-events", "POST", hu23Event(id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  await db.doc(`food_orders/${id}`).update(hu23Verification());
  assert.equal((await rider("rider-events", "POST", hu23Event(id, "PICKED_UP", 2))).status, 200);
  assert.equal((await rider("rider-events", "POST", hu23Event(id, "ARRIVED_AT_CUSTOMER", 3))).status, 200);
  const done = await rider("rider-events", "POST", hu23Event(id, "COMPLETED_SUCCESSFULLY", 4, 1, cash(declared)));
  assert.equal(done.status, 200);
  return id;
}
const resolvePath = (id: string) => `/admin/collection-reviews/${id}/resolve`;
const resolve = (outcome: string, confirmedAmountMinor: number, operationId = randomUUID()) =>
  ({ action: "RESOLVE", operationId, outcome, confirmedAmountMinor, evidence: "Arqueo de caja revisado con el local" });
const refund = (refundedAmountMinor: number, operationId = randomUUID()) =>
  ({ action: "RECORD_EXCESS_REFUND", operationId, refundedAmountMinor, evidence: "Devolución en efectivo firmada por el cliente" });
const order = async (id: string) => (await db.doc(`food_orders/${id}`).get()).data()!;
/** Snapshot of Rider's original declaration in its three places. */
async function original(id: string) {
  const o = await order(id), r = o.collectionReview;
  const events = await db.collection(`food_orders/${id}/rider_events`).where("eventType", "==", "COMPLETED_SUCCESSFULLY").get();
  assert.equal(events.size, 1);
  return { event: events.docs[0].data(),
    history: (await db.doc(`food_orders/${id}/payment_history/rider_collection`).get()).data(),
    review: { expectedMinor: r.expectedMinor, declaredMinor: r.declaredMinor, method: r.method, currency: r.currency, eventId: r.eventId,
      assignmentId: r.assignmentId, assignmentVersion: r.assignmentVersion, openedAt: r.openedAt } };
}
const buyerView = async (id: string) => (await send(buyer.token, `/me/orders/${id}`, undefined, "GET")).body.order;

before(async () => {
  admin = await actor(true); await prepare(admin);
  admin2 = await actor(true); await prepare(admin2);
  buyer = await actor(); await prepare(buyer);
  plain = await actor(); await prepare(plain);
  unverifiedAdmin = await actor(true, false);
});
after(async () => { await db.terminate(); await deleteApp(app); });

test("E5.4a A: an exact declaration needs no reconciliation and is not reconcilable", async () => {
  const id = await delivered(1300);
  const o = await order(id);
  assert.equal(o.paymentStatus, "PAID"); assert.equal(o.paymentCollection.source, "RIDER"); assert.equal(o.collectionReview, undefined);
  const r = await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST");
  assert.equal(r.status, 409); assert.equal(r.body.error, "REVIEW_NOT_ELIGIBLE");
  assert.equal((await buyerView(id)).paymentSource, "RIDER");
});

test("E5.4a B: declared 600, evidence confirms 1300 → PAID (RECONCILED), original declaration intact, replay and conflict", async () => {
  const id = await delivered(600), before = await original(id);
  const list = await send(admin.token, "/admin/collection-reviews", undefined, "GET");
  assert.equal(list.status, 200);
  const row = list.body.items.find((i: any) => i.orderId === id);
  assert.deepEqual(Object.keys(row).sort(), ["currency", "declaredMinor", "expectedMinor", "kind", "method", "openedAt", "orderId",
    "resolution", "shortId", "storeId", "storeName"]);
  assert.equal(row.kind, "OPEN_REVIEW"); assert.equal(JSON.stringify(list.body).includes(buyer.uid), false);
  assert.equal(JSON.stringify(list.body).includes("Cliente laboratorio"), false);

  const body = resolve("FULLY_COLLECTED", 1300), first = await send(admin.token, resolvePath(id), body, "POST");
  assert.equal(first.status, 200);
  assert.deepEqual({ ...first.body }, { contractVersion: 1, orderId: id, action: "RESOLVE", outcome: "FULLY_COLLECTED", paymentStatus: "PAID",
    reviewStatus: "RESOLVED", expectedMinor: 1300, declaredMinor: 600, confirmedMinor: 1300, shortfallMinor: 0, excessMinor: 0,
    excessStatus: "NONE", replayed: false });
  // Lost response: the same operation returns the original result without writing again.
  const replay = await send(admin.token, resolvePath(id), body, "POST");
  assert.equal(replay.status, 200); assert.equal(replay.body.replayed, true); assert.equal(replay.body.outcome, "FULLY_COLLECTED");
  const conflict = await send(admin.token, resolvePath(id), { ...body, confirmedAmountMinor: 1400 }, "POST");
  assert.equal(conflict.status, 409); assert.equal(conflict.body.error, "IDEMPOTENCY_CONFLICT");
  assert.equal((await send(admin2.token, resolvePath(id), body, "POST")).body.error, "IDEMPOTENCY_CONFLICT"); // other actor, same op
  const again = await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST");
  assert.equal(again.status, 409); assert.equal(again.body.error, "REVIEW_NOT_OPEN");

  const o = await order(id), after = await original(id);
  assert.equal(o.status, "COMPLETED"); assert.equal(o.paymentStatus, "PAID"); assert.ok(o.paidAt instanceof Timestamp);
  assert.equal(o.paymentCollection.source, "RECONCILED"); assert.equal(o.paymentCollection.amountMinor, 1300);
  assert.equal(o.collectionReview.status, "RESOLVED"); assert.equal(o.collectionReview.resolution.actorUid, admin.uid);
  assert.deepEqual(after, before); // rider_events, payment_history/rider_collection and the original review fields
  assert.equal((await db.collection(`food_orders/${id}/collection_reconciliations`).get()).size, 1);
  const history = (await db.collection(`food_orders/${id}/payment_history`).get()).docs.map(d => d.data());
  assert.equal(history.length, 2); assert.equal(history.find(h => h.type === "COLLECTION_RECONCILIATION")!.nature, "ADJUSTMENT");
  assert.equal(JSON.stringify(history).includes("Arqueo"), false); // evidence only lives in the admin audit
  const detail = await rider(`orders/${id}`);
  assert.equal(detail.status, 200); assert.equal(detail.body.order.collectionStatus, "DISCREPANCY");
  assert.deepEqual(detail.body.order.collectionReview, { status: "RESOLVED", expectedMinor: 1300, declaredMinor: 600, method: "CASH",
    currency: "PEN", outcome: "FULLY_COLLECTED", confirmedMinor: 1300, shortfallMinor: 0, excessMinor: 0, excessStatus: "NONE" });
  assert.equal(JSON.stringify(detail.body).includes(admin.uid), false); assert.equal(JSON.stringify(detail.body).includes("Arqueo"), false);
  const view = await buyerView(id);
  assert.equal(view.paymentStatus, "PAID"); assert.equal(view.paymentSource, "RIDER"); assert.equal(view.paymentReview, null);
  assert.equal(view.paymentExcess, null);
});

test("E5.4a C: declared 600, confirmed 600 → stays PENDING with shortfall; no partial PAID; no refund possible", async () => {
  const id = await delivered(600), before = await original(id);
  const body = resolve("NOT_FULLY_COLLECTED", 600), r = await send(admin.token, resolvePath(id), body, "POST");
  assert.equal(r.status, 200); assert.equal(r.body.paymentStatus, "PENDING"); assert.equal(r.body.shortfallMinor, 700);
  const o = await order(id);
  assert.equal(o.paymentStatus, "PENDING"); assert.equal(o.paidAt, undefined); assert.equal(o.paymentCollection, undefined);
  assert.ok(o.collectionReview.resolution.resolvedAt instanceof Timestamp);
  assert.deepEqual({ ...o.collectionReview.resolution, resolvedAt: null }, { outcome: "NOT_FULLY_COLLECTED", confirmedMinor: 600,
    shortfallMinor: 700, excessMinor: 0, excessStatus: "NONE", actorUid: admin.uid, resolvedAt: null, operationId: body.operationId });
  assert.deepEqual(await original(id), before);
  assert.equal((await send(admin.token, resolvePath(id), refund(100), "POST")).body.error, "EXCESS_NOT_PENDING");
  assert.equal((await rider(`orders/${id}`)).body.order.collectionReview.shortfallMinor, 700);
  const view = await buyerView(id);
  assert.equal(view.paymentStatus, "PENDING"); assert.equal(view.paymentReview, "INCOMPLETE"); assert.equal(view.paymentSource, null);
  const list = await send(admin.token, "/admin/collection-reviews", undefined, "GET");
  assert.equal(list.body.items.some((i: any) => i.orderId === id), false);
});

test("E5.4a D+E: declared 1600 → PAID with 300 pending refund; the refund is recorded once with its own audit", async () => {
  const id = await delivered(1600), before = await original(id);
  assert.equal((await send(admin.token, resolvePath(id), resolve("NOT_FULLY_COLLECTED", 1600), "POST")).body.error, "AMOUNT_OUTCOME_MISMATCH");
  assert.equal((await send(admin.token, resolvePath(id), refund(300), "POST")).body.error, "EXCESS_NOT_PENDING"); // review still OPEN
  const d = await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1600), "POST");
  assert.equal(d.status, 200); assert.equal(d.body.excessMinor, 300); assert.equal(d.body.excessStatus, "PENDING_REFUND");
  let o = await order(id);
  assert.equal(o.paymentStatus, "PAID"); assert.equal(o.paymentCollection.amountMinor, 1300); assert.equal(o.paymentCollection.confirmedMinor, 1600);
  const pending = (await send(admin.token, "/admin/collection-reviews", undefined, "GET")).body.items.find((i: any) => i.orderId === id);
  assert.equal(pending.kind, "EXCESS_PENDING_REFUND"); assert.equal(pending.resolution.excessStatus, "PENDING_REFUND");
  assert.deepEqual((await buyerView(id)).paymentExcess, { amountMinor: 300, status: "PENDING_REFUND" });

  assert.equal((await send(admin.token, resolvePath(id), refund(100), "POST")).body.error, "REFUND_AMOUNT_MISMATCH"); // no partial refund
  const body = refund(300), e = await send(admin.token, resolvePath(id), body, "POST");
  assert.equal(e.status, 200); assert.equal(e.body.excessStatus, "REFUNDED");
  assert.equal((await send(admin.token, resolvePath(id), body, "POST")).body.replayed, true);
  assert.equal((await send(admin.token, resolvePath(id), refund(300), "POST")).body.error, "EXCESS_NOT_PENDING");
  o = await order(id);
  assert.equal(o.collectionReview.resolution.excessStatus, "REFUNDED"); assert.equal(o.collectionReview.resolution.refund.amountMinor, 300);
  assert.equal(o.collectionReview.resolution.outcome, "FULLY_COLLECTED"); assert.equal(o.paymentStatus, "PAID");
  assert.deepEqual(await original(id), before);
  const audit = (await db.collection(`food_orders/${id}/collection_reconciliations`).get()).docs.map(doc => doc.data().type).sort();
  assert.deepEqual(audit, ["COLLECTION_REVIEW_RESOLVED", "EXCESS_REFUND_RECORDED"]);
  assert.equal((await db.collection(`food_orders/${id}/payment_history`).get()).size, 3);
  assert.equal((await rider(`orders/${id}`)).body.order.collectionReview.excessStatus, "REFUNDED");
  assert.deepEqual((await buyerView(id)).paymentExcess, { amountMinor: 300, status: "REFUNDED" });
  const listed = (await send(admin.token, "/admin/collection-reviews", undefined, "GET")).body.items.some((i: any) => i.orderId === id);
  assert.equal(listed, false);
});

test("E5.4a: two admins resolving at once → exactly one resolution", async () => {
  const id = await delivered(600);
  const results = await Promise.all([send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST"),
    send(admin2.token, resolvePath(id), resolve("NOT_FULLY_COLLECTED", 600), "POST")]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal(results.find(r => r.status === 409)!.body.error, "REVIEW_NOT_OPEN");
  assert.equal((await db.collection(`food_orders/${id}/collection_reconciliations`).get()).size, 1);
  const winner = results.find(r => r.status === 200)!.body;
  assert.equal((await order(id)).paymentStatus, winner.paymentStatus);
});

test("E5.4a: only a verified Food admin may list or resolve; other roles and bad requests change nothing", async () => {
  const id = await delivered(600), before = await order(id);
  for (const who of [plain, buyer, unverifiedAdmin]) {
    assert.equal((await send(who.token, "/admin/collection-reviews", undefined, "GET")).status, 403);
    assert.equal((await send(who.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST")).status, 403);
  }
  assert.equal((await send(undefined as any, "/admin/collection-reviews", undefined, "GET")).status, 401);
  assert.equal((await send(admin.token, "/admin/collection-reviews", undefined, "POST")).status, 405);
  assert.equal((await send(admin.token, resolvePath(id), undefined, "GET")).status, 405);
  for (const bad of [{ ...resolve("FULLY_COLLECTED", 1300), evidence: "ok" }, { ...resolve("FULLY_COLLECTED", 1300), operationId: "x/y" },
    { ...resolve("FULLY_COLLECTED", 1300), extra: true }, { ...resolve("FULLY_COLLECTED", 1300), confirmedAmountMinor: -5 }])
    assert.equal((await send(admin.token, resolvePath(id), bad, "POST")).status, 400);
  assert.equal((await send(admin.token, resolvePath("f".repeat(64)), resolve("FULLY_COLLECTED", 1300), "POST")).status, 404);
  assert.equal((await send(admin.token, resolvePath("not-an-order"), resolve("FULLY_COLLECTED", 1300), "POST")).status, 404);
  assert.equal((await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1299), "POST")).body.error, "AMOUNT_OUTCOME_MISMATCH");
  assert.deepEqual(await order(id), before);
  assert.equal((await db.collection(`food_orders/${id}/collection_reconciliations`).get()).size, 0);
});

test("E5.4a: a failed delivery or an order still in flight is not reconcilable", async () => {
  const id = createHash("sha256").update(`${prefix}-inflight`).digest("hex");
  const { paymentSimulation: _ignored, ...o } = hu23Order(id, buyer.uid);
  await db.doc(`food_orders/${id}`).set({ ...o, paymentStatus: "PENDING", readyAt: Timestamp.fromMillis(Date.now() - 60_000) });
  assert.equal((await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST")).body.error, "REVIEW_NOT_ELIGIBLE");
  await db.doc(`food_orders/${id}`).update({ collectionReview: { status: "OPEN", expectedMinor: 1300, declaredMinor: 600, method: "CASH", currency: "PEN" } });
  assert.equal((await send(admin.token, resolvePath(id), resolve("FULLY_COLLECTED", 1300), "POST")).body.error, "REVIEW_NOT_ELIGIBLE");
});
