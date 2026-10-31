import { beforeEach, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { createDeliveryQuote, QuoteError } from "../src/delivery/deliveryQuoteService";
import { RouteReader, RouteFailure } from "../src/delivery/routeReader";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
const app = initializeApp({ projectId: "demo-chaski-hu15" }, "hu16-quote-persistence");
const db = getFirestore(app);
const uid = "hu16-quote-buyer", store = "hu16-quote-store", address = "hu16-quote-address";
let tick: number, calls: number;
const now = () => new Date(tick);
const reader: RouteReader = { async distanceMeters() { calls++; return 1250; } };
const unavailable: RouteReader = { async distanceMeters() { calls++; throw new RouteFailure("ROUTING_UNAVAILABLE"); } };
const create = (routes = reader, quoteId?: string) => createDeliveryQuote(db, routes, uid, store, address, now, undefined, quoteId);
const code = (expected: string) => (e: unknown) => e instanceof QuoteError && e.code === expected;
beforeEach(async () => {
  tick = Date.parse("2026-10-29T12:00:00.000Z"); calls = 0;
  const quotes = await db.collection("delivery_quotes").where("customerUid", "==", uid).get();
  for (const doc of quotes.docs) await doc.ref.delete();
  await db.doc("business_requests/hu16-quote-business").set({ status: "APPROVED" });
  await db.doc(`stores/${store}`).set({ businessId: "hu16-quote-business", status: "ACTIVE", latitude: -12.0464,
    longitude: -77.0428, deliveryRadiusKm: 5 });
  await db.doc(`users/${uid}/addresses/${address}`).set({ location: { lat: -12.05, lng: -77.04 },
    addressText: "Synthetic public destination", instructions: "Initial" });
});
after(async () => { await db.terminate(); await deleteApp(app); });
test("first calculation persists one immutable quote and subsequent requests never call route provider", async () => {
  const first = await create(); assert.equal(calls, 1); assert.equal(first.reused, false);
  const doc = await db.doc(`delivery_quotes/${first.quote.quoteId}`).get();
  assert.equal(doc.data()?.customerUid, uid); assert.equal(doc.data()?.deliveryFeeMinor, 300);
  assert.ok(doc.data()?.expiresAt instanceof Timestamp);
  const again = await create(unavailable); assert.equal(again.reused, true); assert.equal(calls, 1);
  assert.deepEqual(again.quote, first.quote); assert.equal(again.routeMs, 0);
  const explicit = await create(unavailable, first.quote.quoteId); assert.deepEqual(explicit.quote, first.quote);
  assert.equal(calls, 1);
});
test("new service invocation for same UID rediscovers reference without any Android session generation", async () => {
  const first = await create(); tick += 1000;
  const second = await createDeliveryQuote(db, unavailable, uid, store, address, now);
  assert.equal(second.quote.quoteId, first.quote.quoteId); assert.equal(calls, 1);
});
test("text and instructions or products do not change logistics and do not call HeiGIT", async () => {
  const first = await create();
  await db.doc(`users/${uid}/addresses/${address}`).update({ addressText: "New text", instructions: "New instructions" });
  await db.doc("products/hu16-unrelated-product").set({ name: "New name", price: 1234 });
  const second = await create(unavailable); assert.deepEqual(second.quote, first.quote); assert.equal(calls, 1);
});
test("foreign UID and another store or address cannot reuse explicit reference", async () => {
  const first = await create();
  await db.doc(`users/hu16-other-buyer/addresses/${address}`).set({ location: { lat: -12.05, lng: -77.04 } });
  await assert.rejects(createDeliveryQuote(db, reader, "hu16-other-buyer", store, address, now, undefined, first.quote.quoteId), code("QUOTE_NOT_FOUND"));
  await db.doc("stores/hu16-other-store").set((await db.doc(`stores/${store}`).get()).data()!);
  await assert.rejects(createDeliveryQuote(db, reader, uid, "hu16-other-store", address, now, undefined, first.quote.quoteId), code("QUOTE_INCOMPATIBLE"));
  await db.doc(`users/${uid}/addresses/other-address`).set({ location: { lat: -12.05, lng: -77.04 } });
  await assert.rejects(createDeliveryQuote(db, reader, uid, store, "other-address", now, undefined, first.quote.quoteId), code("QUOTE_INCOMPATIBLE"));
  assert.equal(calls, 1);
});
test("changed destination or origin rejects old reference and fresh context gets a new route", async () => {
  const first = await create();
  await db.doc(`users/${uid}/addresses/${address}`).update({ location: { lat: -12.051, lng: -77.04 } });
  await assert.rejects(create(reader, first.quote.quoteId), code("QUOTE_INCOMPATIBLE"));
  const second = await create(); assert.notEqual(second.quote.quoteId, first.quote.quoteId); assert.equal(calls, 2);
  await db.doc(`stores/${store}`).update({ latitude: -12.0465 });
  await assert.rejects(create(reader, second.quote.quoteId), code("QUOTE_INCOMPATIBLE"));
});
test("exact expiry rejects surviving document, automatic new quote never overwrites original", async () => {
  const first = await create(); tick += 299999;
  assert.equal((await create(unavailable)).quote.quoteId, first.quote.quoteId); tick++;
  await assert.rejects(create(reader, first.quote.quoteId), code("QUOTE_EXPIRED"));
  assert.ok((await db.doc(`delivery_quotes/${first.quote.quoteId}`).get()).exists);
  await assert.rejects(create(unavailable), code("ROUTING_UNAVAILABLE"));
  const second = await create(); assert.notEqual(second.quote.quoteId, first.quote.quoteId);
  assert.equal((await db.doc(`delivery_quotes/${first.quote.quoteId}`).get()).data()?.quotedAt.toMillis(), tick - 300000);
});
test("invalid tariff version cannot be reused even if provider is down", async () => {
  const first = await create(); await db.doc(`delivery_quotes/${first.quote.quoteId}`).update({ tariffVersion: "obsolete" });
  await assert.rejects(create(unavailable, first.quote.quoteId), code("QUOTE_VERSION_INVALID"));
  await assert.rejects(create(unavailable), code("ROUTING_UNAVAILABLE"));
});
test("tampered fee and coordinates are not accepted or silently repaired", async () => {
  const first = await create(); const ref = db.doc(`delivery_quotes/${first.quote.quoteId}`);
  await ref.update({ deliveryFeeMinor: 0 }); await assert.rejects(create(), code("QUOTE_INVALID"));
  await ref.update({ deliveryFeeMinor: 300, storeLocation: { latitude: 0, longitude: 0 } });
  await assert.rejects(create(reader, first.quote.quoteId), code("QUOTE_INVALID"));
  assert.equal(calls, 1);
});
test("source changes during route lookup cannot persist an obsolete quote", async () => {
  const delayed: RouteReader = { async distanceMeters() {
    await db.doc(`stores/${store}`).update({ latitude: -12.047 }); return 1250;
  } };
  await assert.rejects(create(delayed), code("QUOTE_INCOMPATIBLE"));
  assert.equal((await db.collection("delivery_quotes").where("customerUid", "==", uid).get()).size, 0);
});
test("suspension and reduced coverage invalidate reuse without another route call", async () => {
  await create(); await db.doc("business_requests/hu16-quote-business").update({ status: "SUSPENDED" });
  await assert.rejects(create(unavailable), code("BUSINESS_SUSPENDED"));
  await db.doc("business_requests/hu16-quote-business").update({ status: "APPROVED" });
  await db.doc(`stores/${store}`).update({ deliveryRadiusKm: 0.001 });
  await assert.rejects(create(unavailable), code("OUTSIDE_COVERAGE")); assert.equal(calls, 1);
});
test("cancelled request cannot persist and provider failures never create free quotes", async () => {
  await assert.rejects(create(unavailable), code("ROUTING_UNAVAILABLE"));
  const controller = new AbortController();
  const cancel: RouteReader = { async distanceMeters() { controller.abort(); return 1250; } };
  await assert.rejects(createDeliveryQuote(db, cancel, uid, store, address, now, controller.signal));
  assert.equal((await db.collection("delivery_quotes").where("customerUid", "==", uid).get()).size, 0);
});

test("new tariff evidence replaces discovery result without reusing an invalid version", async () => {
  const first = await create(); await db.doc(`delivery_quotes/${first.quote.quoteId}`).update({ tariffVersion: "obsolete" });
  const current = await create(); assert.notEqual(current.quote.quoteId, first.quote.quoteId);
  const again = await create(unavailable); assert.equal(again.quote.quoteId, current.quote.quoteId); assert.equal(calls, 2);
});
test("destination change or business suspension during route prevents persistence", async () => {
  const changeAddress: RouteReader = { async distanceMeters() {
    await db.doc(`users/${uid}/addresses/${address}`).update({ location: { lat: -12.051, lng: -77.04 } }); return 1250;
  } };
  await assert.rejects(create(changeAddress), code("QUOTE_INCOMPATIBLE"));
  const suspend: RouteReader = { async distanceMeters() {
    await db.doc("business_requests/hu16-quote-business").update({ status: "SUSPENDED" }); return 1250;
  } };
  await assert.rejects(create(suspend), code("BUSINESS_SUSPENDED"));
  assert.equal((await db.collection("delivery_quotes").where("customerUid", "==", uid).get()).size, 0);
});
test("text changes during route need no second route and no commercial consent is stored", async () => {
  const textChange: RouteReader = { async distanceMeters() {
    calls++; await db.doc(`users/${uid}/addresses/${address}`).update({ instructions: "Changed during route" }); return 1250;
  } };
  const quote = await create(textChange); assert.equal(calls, 1);
  const doc = (await db.doc(`delivery_quotes/${quote.quote.quoteId}`).get()).data()!;
  assert.ok(!("instructions" in doc) && !("acceptedPriceChanges" in doc));
  assert.equal((await create(unavailable)).quote.quoteId, quote.quote.quoteId); assert.equal(calls, 1);
});
