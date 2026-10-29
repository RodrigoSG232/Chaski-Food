import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { quoteDocument, readStoredQuote, QuoteContext } from "../src/delivery/deliveryQuoteStore";
import { parseDeliveryQuoteRequest } from "../src/delivery/deliveryQuoteRequest";
import { QuoteError } from "../src/delivery/deliveryQuotePolicy";
import { logisticsContextHash } from "../src/orders/orderPolicy";
const origin = { latitude: -12.0464, longitude: -77.0428 }, destination = { latitude: -12.05, longitude: -77.04 };
const context: QuoteContext = { uid: "buyer", storeId: "store", businessId: "business", addressId: "address", origin, destination,
  logisticsHash: logisticsContextHash("buyer", "store", "address", origin, destination) };
const now = new Date("2026-10-29T12:00:00.000Z");
const data = () => quoteDocument(context, 1250.5, now);
test("stored quote uses private identity, exact tariff and server timestamps", () => {
  const stored = data(), response = readStoredQuote("quote", stored, context, now);
  assert.equal(response.deliveryFeeMinor, 300); assert.equal(response.quoteId, "quote");
  assert.ok(stored.quotedAt instanceof Timestamp);
  assert.equal(Date.parse(response.expiresAt) - Date.parse(response.quotedAt), 300000);
  assert.ok(!("customerUid" in response));
});
test("expiry exact boundary rejects even when TTL has not deleted the document", () => {
  const stored = data();
  assert.doesNotThrow(() => readStoredQuote("quote", stored, context, new Date(now.getTime() + 299999)));
  assert.throws(() => readStoredQuote("quote", stored, context, new Date(now.getTime() + 300000)),
    (e: unknown) => e instanceof QuoteError && e.code === "QUOTE_EXPIRED");
});
test("wrong UID, logistics and tariff version never reuse evidence", () => {
  for (const patch of [{ customerUid: "other" }, { storeId: "other" }, { addressId: "other" }, { businessId: "other" },
    { logisticsHash: "bad" }, { tariffVersion: "old-version" }]) {
    assert.throws(() => readStoredQuote("quote", { ...data(), ...patch }, context, now));
  }
});
test("tampered amount, coordinates, dates and representation are rejected", () => {
  for (const patch of [{ deliveryFeeMinor: 0 }, { deliveryFeeMinor: 300.5 }, { deliveryFeeMinor: Number.MAX_SAFE_INTEGER + 1 },
    { deliveryFeeMinor: "300" }, { currency: "USD" }, { schemaVersion: 2 }, { distanceMeters: Infinity }, { distanceMeters: -1 },
    { distanceMeters: 1e17 }, { quotedAt: now.toISOString() }, { quotedAt: Timestamp.fromMillis(now.getTime() + 1) },
    { expiresAt: Timestamp.fromMillis(now.getTime() + 300001) },
    { storeLocation: { latitude: origin.latitude + 0.01, longitude: origin.longitude } },
    { deliveryLocation: { latitude: 91, longitude: -77 } }]) {
    assert.throws(() => readStoredQuote("quote", { ...data(), ...patch }, context, now));
  }
});
test("request stays compatible and quote reference cannot smuggle coordinates or money", () => {
  assert.deepEqual(parseDeliveryQuoteRequest({ storeId: "store", addressId: "address" }), { storeId: "store", addressId: "address" });
  assert.equal(parseDeliveryQuoteRequest({ storeId: "store", addressId: "address", quoteId: "quote" }).quoteId, "quote");
  for (const patch of [{ quoteId: null }, { quoteId: "a/b" }, { quoteId: "" }, { deliveryFeeMinor: 0 }, { origin }]) {
    assert.throws(() => parseDeliveryQuoteRequest({ storeId: "store", addressId: "address", ...patch }));
  }
});

test("malformed persisted reference is never emitted to Android", () => {
  assert.throws(() => readStoredQuote("bad/reference", data(), context, now));
});
