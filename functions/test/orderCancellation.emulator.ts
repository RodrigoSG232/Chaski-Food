import { after, test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { connectedHttp } from "./connectedHandler.fixture";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15", app = initializeApp({ projectId: project }, "hu18-cancellation-tests");
const db = getFirestore(app), auth = getAuth(app), http = connectedHttp(app);
const prefix = `cancel-${Date.now()}`; let n = 0;
const id = (key: string) => createHash("sha256").update(`${prefix}:${key}`).digest("hex");
type Actor = { uid: string; token: string };
async function actor(): Promise<Actor> {
  const uid = `${prefix}-${++n}`, email = `${uid}@example.test`;
  await auth.createUser({ uid, email, password: "emulator-password", emailVerified: true });
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200);
  return { uid, token: (await r.json() as { idToken: string }).idToken };
}
async function seed(key: string, buyerUid: string, status: string) {
  const orderId = id(key), at = Timestamp.fromMillis(1_800_000_000_000);
  const ref = db.doc(`food_orders/${orderId}`);
  await ref.set({ schemaVersion: 1, orderId, buyerUid, storeId: "store", businessId: "business", status,
    createdAt: at, updatedAt: at,
    storeSnapshot: { id: "store", name: "Local ficticio", phone: null },
    businessSnapshot: { id: "business", name: "Negocio ficticio" },
    pickupSnapshot: { addressText: "Local 1" }, dropoffSnapshot: { addressText: "Cliente 2", label: "Casa" },
    items: [{ productId: "p", name: "Plato", quantity: 1, unitPriceMinor: 1000, lineTotalMinor: 1000 }],
    subtotalMinor: 1000, deliveryFeeMinor: 300, totalMinor: 1300, currency: "PEN", paymentMethod: "CASH_ON_DELIVERY" });
  await ref.collection("status_history").doc("initial").set({ schemaVersion: 1, fromStatus: null,
    toStatus: "PENDING_CONFIRMATION", actorUid: buyerUid, occurredAt: at });
  const eventRef = db.doc(`order_events/new_order_${orderId}`);
  await eventRef.set({ schemaVersion: 1, type: "NEW_ORDER", orderId, storeId: "store", businessId: "business",
    status: "PENDING", attemptCount: 0, createdAt: at, updatedAt: at });
  return { orderId, ref, eventRef };
}
after(async () => { await db.terminate(); await deleteApp(app); });

test("only the buyer cancels PENDING_CONFIRMATION; repeated calls keep one historical event", async () => {
  const buyer = await actor(), other = await actor(), { orderId, ref, eventRef } = await seed("direct", buyer.uid, "PENDING_CONFIRMATION");
  const path = `/me/orders/${orderId}/cancel`;
  assert.equal((await http(other.token, path, {}, "POST")).status, 404);
  const first = await http(buyer.token, path, {}, "POST");
  const repeated = await http(buyer.token, path, {}, "POST");
  assert.equal(first.status, 200); assert.equal(repeated.status, 200);
  assert.equal(first.body.outcome, "CANCELLED"); assert.equal((await ref.get()).get("status"), "CANCELLED");
  const history = await ref.collection("status_history").get();
  assert.equal(history.size, 2); assert.equal(history.docs.find(d => d.id === "buyer_cancelled")?.get("toStatus"), "CANCELLED");
  assert.equal((await eventRef.get()).get("status"), "FAILED_PERMANENT");
  assert.equal((await eventRef.get()).get("lastError"), "ORDER_CANCELLED_BY_BUYER");
  assert.equal((await http(buyer.token, `/me/orders/${orderId}`, undefined, "GET")).body.order.status, "CANCELLED");
});

test("PREPARING and every later stage reject cancellation without creating requests or history", async () => {
  const buyer = await actor();
  for (const status of ["PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "IN_DELIVERY", "COMPLETED"]) {
    const { orderId, ref, eventRef } = await seed(status, buyer.uid, status);
    const response = await http(buyer.token, `/me/orders/${orderId}/cancel`, {}, "POST");
    assert.equal(response.status, 409); assert.equal(response.body.error, "CANCELLATION_NOT_ALLOWED");
    assert.equal((await ref.get()).get("status"), status);
    assert.equal((await ref.collection("status_history").get()).size, 1);
    assert.equal((await eventRef.get()).get("status"), "PENDING");
    assert.equal((await db.doc(`order_cancellation_requests/${orderId}`).get()).exists, false);
  }
  const linked = await seed("linked", buyer.uid, "PENDING_CONFIRMATION");
  await linked.ref.update({ deliveryId: "future-delivery" });
  assert.equal((await http(buyer.token, `/me/orders/${linked.orderId}/cancel`, {}, "POST")).body.error,
    "LOGISTICS_CANCELLATION_REQUIRED");
  assert.equal((await linked.ref.get()).get("status"), "PENDING_CONFIRMATION");
});
