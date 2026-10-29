import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { createDeliveryQuote, QuoteError } from "../src/delivery/deliveryQuoteService";
import { RouteFailure, RouteReader } from "../src/delivery/routeReader";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const projectId = "demo-chaski-hu15";
const app = initializeApp({ projectId }, "delivery-quote-tests");
const db = getFirestore(app);
const auth = getAuth(app);
const endpoint = `http://127.0.0.1:5001/${projectId}/southamerica-west1/foodApi/api/v1/delivery-quotes`;
const uid = "quote-buyer";
let token: string;

async function account() {
  await auth.deleteUser(uid).catch(() => undefined);
  await auth.createUser({ uid, email: "quote-buyer@example.invalid", password: "emulator-password" });
  const response = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "quote-buyer@example.invalid", password: "emulator-password", returnSecureToken: true }),
  });
  assert.equal(response.status, 200);
  return (await response.json() as { idToken: string }).idToken;
}

async function seed() {
  const old = await db.collection("delivery_quotes").where("customerUid", "==", uid).get();
  for (const doc of old.docs) await doc.ref.delete();
  await db.doc("business_requests/quote-business").set({ status: "APPROVED", privateNote: "DO_NOT_RETURN" });
  await db.doc("stores/quote-store").set({ businessId: "quote-business", status: "ACTIVE",
    latitude: -12.04596, longitude: -77.03054, deliveryRadiusKm: 5 });
  await db.doc(`users/${uid}/addresses/quote-address`).set({ location: { lat: -12.0515, lng: -77.0345 },
    addressText: "PRIVATE_ADDRESS" });
}

async function quote(idToken = token, body: unknown = { storeId: "quote-store", addressId: "quote-address" }) {
  const response = await fetch(endpoint, { method: "POST", headers: {
    "Content-Type": "application/json", ...(idToken ? { Authorization: `Bearer ${idToken}` } : {}),
  }, body: JSON.stringify(body), signal: AbortSignal.timeout(20_000) });
  return { response, body: await response.json() as Record<string, unknown> };
}

before(async () => { token = await account(); });
after(async () => { await db.terminate(); await deleteApp(app); });

test("local y dirección propios devuelven una cotización temporal simulada", async () => {
  await seed();
  const beforeOrders = (await db.collection("orders").get()).size;
  const beforeFoodOrders = (await db.collection("food_orders").get()).size;
  const beforeStore = (await db.doc("stores/quote-store").get()).data();
  const beforeAddress = (await db.doc(`users/${uid}/addresses/quote-address`).get()).data();
  const started = performance.now();
  const { response, body } = await quote();
  const elapsed = performance.now() - started;
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("x-route-source"), "SIMULATED");
  assert.equal(response.headers.get("x-quote-firestore-reads"), "8");
  assert.match(response.headers.get("server-timing") ?? "", /quote;dur=.+, route;dur=.+/);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(body.distanceMeters, 1_250.5);
  assert.equal(body.deliveryFeeMinor, 300);
  assert.equal(body.currency, "PEN");
  assert.match(String(body.quoteId), /^[A-Za-z0-9_-]+$/);
  assert.equal(response.headers.get("x-quote-reused"), "false");
  const persisted = (await db.doc(`delivery_quotes/${body.quoteId}`).get()).data()!;
  assert.equal(persisted.customerUid, uid);
  assert.equal(persisted.deliveryFeeMinor, body.deliveryFeeMinor);
  assert.equal(body.tariffVersion, "lima-driving-car-v1");
  assert.deepEqual(body.storeLocation, { latitude: -12.04596, longitude: -77.03054 });
  assert.deepEqual(body.deliveryLocation, { latitude: -12.0515, longitude: -77.0345 });
  assert.equal(Date.parse(String(body.expiresAt)) - Date.parse(String(body.quotedAt)), 300_000);
  assert.deepEqual(Object.keys(body).sort(), ["currency", "deliveryFeeMinor", "deliveryLocation", "distanceMeters",
    "expiresAt", "quoteId", "quotedAt", "storeLocation", "tariffVersion"]);
  assert.equal((await db.collection("orders").get()).size, beforeOrders);
  assert.equal((await db.collection("food_orders").get()).size, beforeFoodOrders);
  assert.deepEqual((await db.doc("stores/quote-store").get()).data(), beforeStore);
  assert.deepEqual((await db.doc(`users/${uid}/addresses/quote-address`).get()).data(), beforeAddress);
  console.info(`SIMULATED quote HTTP elapsedMs=${elapsed.toFixed(1)} reads=8 ` +
    `serverTiming=${response.headers.get("server-timing")} routeSource=SIMULATED`);
});

test("el servicio envía solo coordenadas de Firestore al lector y calcula el importe", async () => {
  await seed();
  const reader: RouteReader = { async distanceMeters(origin, destination) {
    assert.deepEqual(origin, { latitude: -12.04596, longitude: -77.03054 });
    assert.deepEqual(destination, { latitude: -12.0515, longitude: -77.0345 });
    return 1_837.5;
  } };
  const result = await createDeliveryQuote(db, reader, uid, "quote-store", "quote-address");
  assert.equal(result.quote.deliveryFeeMinor, 371);
  assert.deepEqual(result.quote.storeLocation, { latitude: -12.04596, longitude: -77.03054 });
  assert.deepEqual(result.quote.deliveryLocation, { latitude: -12.0515, longitude: -77.0345 });
  assert.equal(result.firestoreReads, 8);
});

test("no se puede usar una dirección de otro UID", async () => {
  await seed();
  await db.doc("users/other-buyer/addresses/other-address").set({ location: { lat: -12.05, lng: -77.03 } });
  const { response, body } = await quote(token, { storeId: "quote-store", addressId: "other-address" });
  assert.equal(response.status, 404); assert.equal(body.error, "ADDRESS_NOT_FOUND");
});

test("mismo ID con coordenadas editadas devuelve las ubicaciones realmente leídas", async () => {
  await seed();
  await db.doc(`users/${uid}/addresses/quote-address`).update({ location: { lat: -12.052, lng: -77.035 } });
  await db.doc("stores/quote-store").update({ latitude: -12.046, longitude: -77.031 });
  const { response, body } = await quote();
  assert.equal(response.status, 200);
  assert.deepEqual(body.storeLocation, { latitude: -12.046, longitude: -77.031 });
  assert.deepEqual(body.deliveryLocation, { latitude: -12.052, longitude: -77.035 });
});

test("suspensión administrativa y local inactivo bloquean la cotización", async () => {
  await seed();
  await db.doc("business_requests/quote-business").update({ status: "SUSPENDED" });
  assert.equal((await quote()).body.error, "BUSINESS_SUSPENDED");
  await db.doc("business_requests/quote-business").update({ status: "APPROVED" });
  await db.doc("stores/quote-store").update({ status: "INACTIVE" });
  assert.equal((await quote()).body.error, "STORE_UNAVAILABLE");
});

test("cobertura y coordenadas erróneas se rechazan antes de consultar rutas", async () => {
  await seed();
  await db.doc(`users/${uid}/addresses/quote-address`).update({ location: { lat: -12.12, lng: -77.03 } });
  assert.equal((await quote()).body.error, "OUTSIDE_COVERAGE");
  await db.doc(`users/${uid}/addresses/quote-address`).update({ location: { lat: 91, lng: -77.03 } });
  assert.equal((await quote()).body.error, "INVALID_LOCATION");
  await seed();
  await db.doc("stores/quote-store").update({ longitude: "bad-coordinate" });
  assert.equal((await quote()).body.error, "INVALID_LOCATION");
});

test("token y solicitud inválidos no entregan cotización", async () => {
  await seed();
  assert.equal((await quote("")).response.status, 401);
  assert.equal((await quote("invalid-token")).response.status, 401);
  assert.equal((await quote(token, { storeId: "quote-store", addressId: "quote-address", deliveryFeeMinor: 0 })).response.status, 400);
});

test("token revocado no entrega cotización", async () => {
  await seed();
  await new Promise(resolve => setTimeout(resolve, 1_100));
  await auth.revokeRefreshTokens(uid);
  assert.equal((await quote()).response.status, 401);
  token = await account();
});

test("errores del proveedor no se convierten en reparto gratuito", async () => {
  await seed();
  for (const code of ["NO_ROUTE", "ROUTING_QUOTA_EXCEEDED", "ROUTING_UNAVAILABLE", "ROUTING_INVALID_RESPONSE"] as const) {
    const reader: RouteReader = { async distanceMeters() { throw new RouteFailure(code); } };
    await assert.rejects(createDeliveryQuote(db, reader, uid, "quote-store", "quote-address"),
      (error: unknown) => error instanceof QuoteError && error.code === code);
  }
});

test("HTTP recupera la misma referencia tras un nuevo login y deniega acceso Firestore cliente", async () => {
  await seed(); const first = await quote(); assert.equal(first.response.status, 200);
  const login = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "quote-buyer@example.invalid", password: "emulator-password", returnSecureToken: true }),
  });
  assert.equal(login.status, 200);
  const reloginToken = (await login.json() as { idToken: string }).idToken;
  const second = await quote(reloginToken);
  assert.equal(second.response.status, 200); assert.equal(second.body.quoteId, first.body.quoteId);
  assert.equal(second.body.deliveryFeeMinor, first.body.deliveryFeeMinor);
  assert.equal(second.response.headers.get("x-quote-reused"), "true");
  assert.equal(second.response.headers.get("x-quote-firestore-reads"), "4");
  const docUrl = `http://127.0.0.1:8089/v1/projects/${projectId}/databases/(default)/documents/delivery_quotes/${first.body.quoteId}`;
  assert.equal((await fetch(docUrl, { headers: { Authorization: `Bearer ${reloginToken}` } })).status, 403);
  assert.equal((await fetch(docUrl, { method: "PATCH", headers: { Authorization: `Bearer ${reloginToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields: { deliveryFeeMinor: { integerValue: "0" } } }) })).status, 403);
  assert.equal((await quote(token, { storeId: "quote-store", addressId: "quote-address", quoteId: first.body.quoteId })).body.quoteId,
    first.body.quoteId);
});
