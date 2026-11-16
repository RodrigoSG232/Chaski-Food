import { connectedHttp } from "./connectedHandler.fixture";
import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { createHash, randomUUID } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";
import { hu23Event, hu23Sample } from "./lab/hu23Fixture";

/**
 * E5.5 — end-to-end over the LOCAL emulators (synthetic data, no real Chaski Rider backend).
 * HTTP-verified stages (real foodApi/riderApi handlers with their auth): manager ACCEPT and READY, Rider discovery
 * (paginated listing), detail, assignment, manager PIN verification, PICKED_UP, tracking, arrival, completion with
 * collection, admin reconciliation, buyer detail and the HU28 dashboard.
 * SIMULATED stages: order creation (seeded document instead of checkout) and the Rider backend itself (this test calls
 * riderApi as Rider would; the emulator runs with CHASKI_RIDER_ACTIVATION=general and no Cloud Run IAM).
 */
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15", prefix = `e2e-${Date.now()}`;
const app = initializeApp({ projectId: project }, prefix), db = getFirestore(app), auth = getAuth(app);
const access = new CommercialAccessService(db, auth), send = connectedHttp(app);
const riderBase = `http://127.0.0.1:5001/${project}/southamerica-west1/riderApi/integration/v1/`;
type Actor = CommercialIdentity & { token: string };
let n = 0, admin: Actor;

async function actor(isAdmin = false): Promise<Actor> {
  const uid = `${prefix}-${++n}`, email = `${uid}@example.test`;
  await auth.createUser({ uid, email, password: "emulator-password", emailVerified: true });
  if (isAdmin) await auth.setCustomUserClaims(uid, { admin: true });
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200);
  return { uid, email, emailVerified: true, admin: isAdmin, token: (await r.json() as { idToken: string }).idToken };
}
const prepare = async (who: Actor, revision = 0) =>
  (await access.execute(admin, { action: "PREPARE", operationId: `prepare-${++n}`, email: who.email, expectedRevision: revision })).revision;
async function rider(path: string, method = "GET", body?: unknown) {
  const r = await fetch(riderBase + path, { method, headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) });
  return { status: r.status, body: await r.json() as any };
}

/** Business with one store, its verified manager and a buyer; plus a seeded PENDING_CONFIRMATION cash order (SIMULATED creation). */
async function scenario() {
  const owner = await actor(), manager = await actor(), buyer = await actor(), store = `${owner.uid}-store`;
  await db.doc(`business_requests/${owner.uid}`).set({ ownerUid: owner.uid, status: "APPROVED", businessName: "Negocio ficticio" });
  await db.doc(`stores/${store}`).set({ businessId: owner.uid, name: "Local ficticio", status: "ACTIVE", operationalStatus: "OPEN" });
  await prepare(owner); await prepare(buyer); await prepare(manager);
  await access.execute(owner, { action: "ASSIGN", operationId: `assign-${++n}`, businessId: owner.uid, email: manager.email,
    expectedRevision: 1, storeIds: [store], fullName: "Responsable ficticio", phone: "000" });
  const id = createHash("sha256").update(`${prefix}-order-${++n}`).digest("hex"), now = Timestamp.now();
  const location = (lat: number) => ({ latitude: lat, longitude: -77.1 });
  await db.doc(`food_orders/${id}`).set({ schemaVersion: 1, orderId: id, businessId: owner.uid, storeId: store, buyerUid: buyer.uid,
    status: "PENDING_CONFIRMATION", createdAt: now, updatedAt: now, intentId: randomUUID(), requestHash: "LOCAL_ONLY",
    businessSnapshot: { id: owner.uid, name: "Negocio ficticio" }, storeSnapshot: { id: store, name: "Local ficticio", phone: null },
    buyerSnapshot: { uid: buyer.uid, displayName: "Cliente ficticio", phone: null },
    pickupSnapshot: { addressText: "Local ficticio", location: location(-12.1) },
    dropoffSnapshot: { label: "Casa", addressText: "Destino ficticio", location: location(-12.11), reference: null, instructions: null },
    items: [{ productId: "p1", categoryId: "c1", name: "Plato ficticio", quantity: 2, unitPriceMinor: 4200, lineTotalMinor: 8400 }],
    subtotalMinor: 8400, deliveryFeeMinor: 300, tipMinor: 0, totalMinor: 8700, currency: "PEN",
    paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PENDING" });
  return { owner, manager, buyer, store, id };
}

/** Pedido → READY_FOR_PICKUP → discovery → assignment → PIN verification → PICKED_UP → arrival → completion (all HTTP). */
async function deliver(s: Awaited<ReturnType<typeof scenario>>, declaredMinor: number) {
  const orders = (path: string, method = "POST", body: unknown = {}) => send(s.manager.token, `/stores/${s.store}/orders/${s.id}${path}`, body, method);
  assert.equal((await orders("/decision", "POST", { action: "ACCEPT", estimatedPreparationMinutes: 20 })).status, 200);
  const ready = await orders("/ready-for-pickup"); assert.equal(ready.status, 200); assert.equal("pickupCode" in ready.body, false);
  // Discovery: walk the paginated listing exactly as Rider would until the order appears.
  let cursor: string | null = null, offered: any = null;
  for (let page = 0; page < 200 && !offered; page++) {
    const r = await rider("orders?limit=50" + (cursor ? "&cursor=" + cursor : "")); assert.equal(r.status, 200);
    offered = r.body.orders.find((o: any) => o.orderId === s.id) ?? null; cursor = r.body.nextCursor;
    if (!cursor) break;
  }
  assert.ok(offered, "order discovered through the listing");
  assert.equal("pickupCode" in offered, false); assert.equal(offered.payment.amountToCollectMinor, 8700);
  assert.equal((await rider(`orders/${s.id}`)).body.order.pickupCode, null); // no assignment yet
  assert.equal((await rider("rider-events", "POST", hu23Event(s.id, "ASSIGNMENT_ACCEPTED", 1))).status, 200);
  const code = (await rider(`orders/${s.id}`)).body.order.pickupCode as string; // only the assigned Rider backend gets it
  assert.match(code, /^\d{4}$/);
  assert.equal((await rider("rider-events", "POST", hu23Event(s.id, "PICKED_UP", 2))).body.error.code, "PICKUP_NOT_VERIFIED");
  const verified = await orders("/pickup-verifications", "POST",
    { operationId: randomUUID(), code, assignmentId: "hu23-assignment-1", assignmentVersion: 1 });
  assert.equal(verified.status, 200); assert.equal(verified.body.outcome, "VERIFIED");
  assert.equal((await rider("rider-events", "POST", hu23Event(s.id, "PICKED_UP", 2))).status, 200);
  assert.equal((await rider(`orders/${s.id}/tracking`, "PUT", hu23Sample(1))).status, 200);
  assert.equal((await rider("rider-events", "POST", hu23Event(s.id, "ARRIVED_AT_CUSTOMER", 3))).status, 200);
  const done = await rider("rider-events", "POST",
    hu23Event(s.id, "COMPLETED_SUCCESSFULLY", 4, 1, { method: "CASH", amountMinor: declaredMinor, currency: "PEN" }));
  assert.equal(done.status, 200);
  return done.body;
}
const buyerView = async (s: Awaited<ReturnType<typeof scenario>>) => {
  const r = await send(s.buyer.token, `/me/orders/${s.id}`, undefined, "GET"); assert.equal(r.status, 200); return r.body.order;
};
const sales = async (s: Awaited<ReturnType<typeof scenario>>) => {
  const r = await send(s.owner.token, `/commercial/dashboard?businessId=${s.owner.uid}&days=7`, undefined, "GET");
  assert.equal(r.status, 200); return r.body.salesMinor as number;
};

before(async () => { admin = await actor(true); await prepare(admin); });
after(async () => { await db.terminate(); await deleteApp(app); });

test("E5.5 A: exact collection — ready → discovery → assignment → PIN → delivery → PAID, buyer text data and one sale", async () => {
  const s = await scenario();
  const done = await deliver(s, 8700);
  assert.equal(done.collectionStatus, "RECORDED");
  const view = await buyerView(s);
  assert.equal(view.status, "COMPLETED"); assert.equal(view.paymentStatus, "PAID"); assert.equal(view.paymentSource, "RIDER");
  assert.equal(view.paymentReview, null); assert.equal(view.paymentExcess, null);
  assert.equal(await sales(s), 8400); // HU28 counts the subtotal once
  const list = await send(admin.token, "/admin/collection-reviews", undefined, "GET");
  assert.equal(list.body.items.some((i: any) => i.orderId === s.id), false);
});

test("E5.5 B: discrepancy — PENDING review, no sale; admin reconciles with excess and refund; one sale, excess never counted", async () => {
  const s = await scenario();
  const done = await deliver(s, 9000);
  assert.equal(done.collectionStatus, "DISCREPANCY"); assert.equal(done.collectionReview.status, "OPEN");
  let view = await buyerView(s);
  assert.equal(view.paymentStatus, "PENDING"); assert.equal(view.paymentReview, "OPEN");
  assert.equal(await sales(s), 0); // under review: not a sale, no 503

  const listed = (await send(admin.token, "/admin/collection-reviews", undefined, "GET")).body.items.find((i: any) => i.orderId === s.id);
  assert.equal(listed.kind, "OPEN_REVIEW"); assert.equal(listed.expectedMinor, 8700); assert.equal(listed.declaredMinor, 9000);
  const path = `/admin/collection-reviews/${s.id}/resolve`;
  assert.equal((await send(s.owner.token, path, { action: "RESOLVE", operationId: randomUUID(), outcome: "FULLY_COLLECTED",
    confirmedAmountMinor: 9000, evidence: "Intento de un rol no autorizado" }, "POST")).status, 403);
  const resolved = await send(admin.token, path, { action: "RESOLVE", operationId: randomUUID(), outcome: "FULLY_COLLECTED",
    confirmedAmountMinor: 9000, evidence: "Arqueo de caja revisado con el local" }, "POST");
  assert.equal(resolved.status, 200); assert.equal(resolved.body.excessMinor, 300); assert.equal(resolved.body.excessStatus, "PENDING_REFUND");
  view = await buyerView(s);
  assert.equal(view.paymentStatus, "PAID"); assert.equal(view.paymentSource, "RIDER");
  assert.deepEqual(view.paymentExcess, { amountMinor: 300, status: "PENDING_REFUND" });
  assert.equal(JSON.stringify(view).includes("Arqueo"), false); assert.equal(JSON.stringify(view).includes(admin.uid), false);
  assert.equal(await sales(s), 8400);

  const refund = await send(admin.token, path, { action: "RECORD_EXCESS_REFUND", operationId: randomUUID(), refundedAmountMinor: 300,
    evidence: "Devolución en efectivo firmada por el cliente" }, "POST");
  assert.equal(refund.status, 200); assert.equal(refund.body.excessStatus, "REFUNDED");
  assert.deepEqual((await buyerView(s)).paymentExcess, { amountMinor: 300, status: "REFUNDED" });
  assert.equal(await sales(s), 8400); // still one sale; neither the excess nor the refund changes it
  const detail = (await rider(`orders/${s.id}`)).body.order;
  assert.equal(detail.collectionStatus, "DISCREPANCY"); assert.equal(detail.collectionReview.status, "RESOLVED");
  assert.equal(detail.collectionReview.excessStatus, "REFUNDED"); assert.equal(detail.collectionReview.declaredMinor, 9000);
});
