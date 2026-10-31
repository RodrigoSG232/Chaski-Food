import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";
import { encodeBuyerOrdersCursor } from "../src/orders/buyerOrdersPolicy";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "demo-chaski-hu15", app = initializeApp({ projectId: project }, "hu17-buyer-orders-tests"), db = getFirestore(app), auth = getAuth(app);
const access = new CommercialAccessService(db, auth), base = `http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1/me/orders`;
type Actor = CommercialIdentity & { token: string };
let n = 0; const prefix = `hu17-${Date.now()}`; let admin: Actor;
const ACTIVE = ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP", "IN_DELIVERY"], HISTORICAL = ["COMPLETED", "CANCELLED", "REJECTED"];

async function login(uid: string): Promise<Actor> {
  const user = await auth.getUser(uid);
  const r = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: user.email, password: "emulator-password", returnSecureToken: true }) });
  assert.equal(r.status, 200);
  return { uid, email: user.email!, emailVerified: user.emailVerified, admin: user.customClaims?.admin === true, token: (await r.json() as { idToken: string }).idToken };
}
async function actor(isAdmin = false): Promise<Actor> {
  const uid = `${prefix}-${++n}`;
  await auth.createUser({ uid, email: `${uid}@example.test`, password: "emulator-password", emailVerified: true });
  if (isAdmin) await auth.setCustomUserClaims(uid, { admin: true });
  return login(uid);
}
const orderIdFor = (key: string) => createHash("sha256").update(`${prefix}:${key}`).digest("hex");
type Seed = { id: string; buyer: string; status: string | null; millis: number };
async function seed(orders: Seed[]) {
  for (let i = 0; i < orders.length; i += 200) {
    const batch = db.batch();
    for (const o of orders.slice(i, i + 200)) {
      const createdAt = Timestamp.fromMillis(o.millis), ref = db.doc(`food_orders/${o.id}`);
      batch.set(ref, { schemaVersion: 1, orderId: o.id, intentId: `i-${o.id.slice(0, 8)}`, requestHash: "PRIVATE_HASH", buyerUid: o.buyer,
        businessId: "biz", storeId: "store", status: o.status, createdAt, updatedAt: createdAt,
        buyerSnapshot: { uid: o.buyer, displayName: "PRIVATE_NAME", phone: "PRIVATE_PHONE" },
        businessSnapshot: { id: "biz", name: "Negocio ficticio" }, storeSnapshot: { id: "store", name: "Local ficticio", phone: null },
        pickupSnapshot: { addressText: "Av. Local 1", location: { latitude: -12, longitude: -77 }, reference: null, instructions: null },
        dropoffSnapshot: { sourceAddressId: "addr", label: "Casa", addressText: "Calle Cliente 2", location: { latitude: -12.1, longitude: -77.1 },
          reference: "Puerta verde", instructions: null },
        items: [{ productId: "p", categoryId: "c", name: "Plato ficticio", quantity: 1, unitPriceMinor: 1000, lineTotalMinor: 1000 }],
        subtotalMinor: 1000, deliveryFeeMinor: 300, totalMinor: 1300, currency: "PEN", paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PENDING",
        quoteSnapshot: { quoteId: "PRIVATE_QUOTE" } });
      batch.set(ref.collection("status_history").doc("initial"), { schemaVersion: 1, fromStatus: null, toStatus: "PENDING_CONFIRMATION",
        actorUid: "PRIVATE_ACTOR", occurredAt: createdAt });
    }
    await batch.commit();
  }
}
async function call(a: Actor | undefined, path = "", init: RequestInit = {}) {
  const r = await fetch(`${base}${path}`, { ...init, headers: a ? { Authorization: `Bearer ${a.token}` } : {}, signal: AbortSignal.timeout(30000) });
  return { status: r.status, body: await r.json().catch(() => null) as any };
}
/** Walks a phase to the end; returns every id in order, page sizes and whether an empty page carried a cursor. */
async function walk(a: Actor, phase: string, pageSize = 20) {
  const ids: string[] = [], sizes: number[] = []; let cursor: string | null = null, emptyWithCursor = 0, guard = 0;
  do {
    const q = new URLSearchParams({ phase, pageSize: String(pageSize), ...(cursor ? { cursor } : {}) });
    const r = await call(a, `?${q}`); assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(Object.keys(r.body).sort(), ["contractVersion", "phase", "orders", "nextCursor"].sort());
    for (const o of r.body.orders) { assert.equal(o.phase, phase); ids.push(o.orderId); }
    sizes.push(r.body.orders.length); if (r.body.orders.length === 0 && r.body.nextCursor) emptyWithCursor++;
    assert.ok(r.body.orders.length <= pageSize);
    cursor = r.body.nextCursor; assert.ok(++guard < 100, "unbounded walk");
  } while (cursor);
  return { ids, sizes, emptyWithCursor };
}
const expected = (orders: Seed[], buyer: string, phase: string) => orders
  .filter(o => o.buyer === buyer && (phase === "HISTORICAL" ? HISTORICAL.includes(o.status!) : !HISTORICAL.includes(o.status!)))
  .sort((a, b) => b.millis - a.millis || (a.id < b.id ? 1 : -1)).map(o => o.id);

before(async () => { admin = await actor(true); await access.execute(admin, { action: "PREPARE", operationId: `prepare-${++n}`, email: admin.email, expectedRevision: 0 }); });
after(async () => { await db.terminate(); await deleteApp(app); });

test("full walk of both phases is chronological, complete and without duplicates across budgets, ties and unknown statuses", async () => {
  const buyer = await actor(), other = await actor(); const orders: Seed[] = []; let t = 1_900_000_000_000;
  // Newest first: 60 consecutive ACTIVE (>50, one batch), then 210 HISTORICAL (>200 budget), then 5 old ACTIVE incl. an unknown status.
  for (let i = 0; i < 60; i++) orders.push({ id: orderIdFor(`a${i}`), buyer: buyer.uid, status: ACTIVE[i % ACTIVE.length], millis: t - i * 1000 });
  for (let i = 0; i < 210; i++) orders.push({ id: orderIdFor(`h${i}`), buyer: buyer.uid, status: HISTORICAL[i % 3], millis: t - 100_000 - i * 1000 });
  const old = t - 1_000_000;
  for (let i = 0; i < 4; i++) orders.push({ id: orderIdFor(`o${i}`), buyer: buyer.uid, status: ACTIVE[i], millis: old });
  orders.push({ id: orderIdFor("unknown"), buyer: buyer.uid, status: "AWAITING_RIDER", millis: old }); // identical createdAt group
  // Identical timestamps across phases (in the old group, so the newest 60 ACTIVE fill exactly three pages of 20)
  // and another buyer interleaved at the same instants.
  for (let i = 0; i < 12; i++) orders.push({ id: orderIdFor(`tie${i}`), buyer: buyer.uid, status: i % 2 ? "COMPLETED" : "PREPARING", millis: old });
  for (let i = 0; i < 40; i++) orders.push({ id: orderIdFor(`x${i}`), buyer: other.uid, status: i % 2 ? "PREPARING" : "CANCELLED", millis: t - i * 1000 });
  await seed(orders);

  const active = await walk(buyer, "ACTIVE");
  assert.deepEqual(active.ids, expected(orders, buyer.uid, "ACTIVE"));
  assert.ok(active.emptyWithCursor >= 1, "a page crossing >200 historical orders must return 0 rows with a cursor");
  assert.ok(active.ids.includes(orderIdFor("unknown")));
  const historical = await walk(buyer, "HISTORICAL");
  assert.deepEqual(historical.ids, expected(orders, buyer.uid, "HISTORICAL"));
  assert.equal(new Set([...active.ids, ...historical.ids]).size, orders.filter(o => o.buyer === buyer.uid).length);
  // Small pages on a long run of the same phase never skip the documents left in a fetched batch.
  assert.deepEqual((await walk(buyer, "ACTIVE", 7)).ids, expected(orders, buyer.uid, "ACTIVE"));
  assert.deepEqual((await walk(buyer, "HISTORICAL", 50)).ids, expected(orders, buyer.uid, "HISTORICAL"));
  const first = await call(buyer, "?phase=ACTIVE&pageSize=50");
  assert.equal(first.body.orders.every((o: any) => o.statusKnown === true), true);
});

test("unknown valid status is ACTIVE with statusKnown=false; missing status is an inconsistency for the whole page", async () => {
  const buyer = await actor(), broken = await actor();
  await seed([{ id: orderIdFor("u1"), buyer: buyer.uid, status: "AWAITING_RIDER", millis: 1_900_000_000_000 },
    { id: orderIdFor("n1"), buyer: broken.uid, status: null, millis: 1_900_000_000_000 }]);
  const r = await call(buyer, "?phase=ACTIVE");
  assert.equal(r.status, 200); assert.equal(r.body.orders[0].statusKnown, false); assert.equal(r.body.orders[0].status, "AWAITING_RIDER");
  assert.equal((await call(buyer, "?phase=HISTORICAL")).body.orders.length, 0);
  assert.deepEqual(await call(broken, "?phase=ACTIVE"), { status: 503, body: { error: "DATA_INCONSISTENT" } });
});

test("detail exposes only the buyer's own snapshots; foreign and missing orders are indistinguishable", async () => {
  const buyer = await actor(), other = await actor(), mine = orderIdFor("mine"), theirs = orderIdFor("theirs");
  await seed([{ id: mine, buyer: buyer.uid, status: "PENDING_CONFIRMATION", millis: 1_900_000_000_000 },
    { id: theirs, buyer: other.uid, status: "PENDING_CONFIRMATION", millis: 1_900_000_000_000 }]);
  const r = await call(buyer, `/${mine}`); assert.equal(r.status, 200);
  assert.equal(r.body.order.delivery.addressText, "Calle Cliente 2"); assert.equal(r.body.order.statusHistory.length, 1);
  for (const secret of ["PRIVATE", "latitude", "buyerUid", "quote", "actor"]) assert.equal(JSON.stringify(r.body).includes(secret), false, secret);
  const foreign = await call(buyer, `/${theirs}`), missing = await call(buyer, `/${orderIdFor("nope")}`), malformed = await call(buyer, "/..%2Fx");
  assert.deepEqual(foreign, { status: 404, body: { error: "ORDER_NOT_FOUND" } });
  assert.deepEqual(missing, foreign); assert.equal(malformed.status, 404);
  assert.equal((await call(buyer, `/${mine}?x=1`)).status, 400);
});

test("cursors are bound to account and phase; foreign or deleted anchors are rejected", async () => {
  const a = await actor(), b = await actor(); const orders: Seed[] = [];
  for (let i = 0; i < 25; i++) orders.push({ id: orderIdFor(`c${i}`), buyer: a.uid, status: "PREPARING", millis: 1_900_000_000_000 - i });
  orders.push({ id: orderIdFor("b0"), buyer: b.uid, status: "PREPARING", millis: 1_900_000_000_000 });
  await seed(orders);
  const page = await call(a, "?phase=ACTIVE&pageSize=10"); const cursor = page.body.nextCursor; assert.ok(cursor);
  const invalid = { status: 400, body: { error: "INVALID_CURSOR" } };
  assert.deepEqual(await call(b, `?phase=ACTIVE&cursor=${cursor}`), invalid);          // account switched
  assert.deepEqual(await call(a, `?phase=HISTORICAL&cursor=${cursor}`), invalid);      // other phase
  assert.deepEqual(await call(a, `?phase=ACTIVE&cursor=${encodeBuyerOrdersCursor(a.uid, "ACTIVE", orderIdFor("b0"))}`), invalid); // foreign anchor
  const anchor = JSON.parse(Buffer.from(cursor, "base64url").toString("utf8")).anchorId;
  await db.doc(`food_orders/${anchor}`).delete();
  assert.deepEqual(await call(a, `?phase=ACTIVE&cursor=${cursor}`), invalid);          // deleted anchor
});

test("owner and manager read their personal purchases; authentication and method are enforced", async () => {
  const owner = await actor(), manager = await actor(), store = `${owner.uid}-store`;
  await db.doc(`business_requests/${owner.uid}`).set({ ownerUid: owner.uid, status: "APPROVED", businessName: "Negocio ficticio" });
  await db.doc(`stores/${store}`).set({ businessId: owner.uid, name: "Local ficticio", status: "ACTIVE", operationalStatus: "OPEN" });
  for (const x of [owner, manager]) await access.execute(admin, { action: "PREPARE", operationId: `prepare-${++n}`, email: x.email, expectedRevision: 0 });
  await access.execute(owner, { action: "ASSIGN", operationId: `assign-${++n}`, businessId: owner.uid, email: manager.email, expectedRevision: 1,
    storeIds: [store], fullName: "Responsable ficticio", phone: "000" });
  await seed([{ id: orderIdFor("own"), buyer: owner.uid, status: "COMPLETED", millis: 1 }, { id: orderIdFor("man"), buyer: manager.uid, status: "PENDING_CONFIRMATION", millis: 1 }]);
  assert.deepEqual((await call(owner, "?phase=HISTORICAL")).body.orders.map((o: any) => o.orderId), [orderIdFor("own")]);
  assert.deepEqual((await call(manager, "?phase=ACTIVE")).body.orders.map((o: any) => o.orderId), [orderIdFor("man")]);
  assert.equal((await call(undefined, "?phase=ACTIVE")).status, 401);
  assert.equal((await call(owner, "?phase=ACTIVE", { method: "POST" })).status, 405);
  assert.equal((await call(owner, "?phase=ACTIVE&buyerUid=other")).status, 400);
});
