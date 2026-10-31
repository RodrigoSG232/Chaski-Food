import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { AcceptedPurchase, MAX_ORDER_MINOR, OrderAttemptResult, OrderIntent, OrderReceipt, RecordedOrderAttempt,
  minorFromBigInt, orderAmounts, orderMinor, parseAcceptedPurchase, parseOrderIntent } from "../src/orders/orderModels";
import { canonicalConsent, compareOrderReviews, consentHash, logisticsHash, requestHash, recordedOrderAttempt,
  resolveOrderIntent, recoverOrderAttempt, orderReceipt } from "../src/orders/orderPolicy";

// One versioned public fixture consumed by JVM and Node, no workstation-specific paths.
const vectors = JSON.parse(readFileSync(resolve(__dirname, "../../../app/src/test/resources/hu16/confirmation-v1.json"), "utf8"));
const id = vectors.intentId as string;
const raw = (name = "base") => structuredClone(vectors.validCases.find((v: { name: string }) => v.name === name).purchase);
const purchase = (name = "base") => parseAcceptedPurchase(raw(name));
const intent = (name = "base"): OrderIntent => parseOrderIntent({ intentId: id, accepted: raw(name) });
const receipt = (): OrderReceipt => ({ orderId: "order-demo", intentId: id, accepted: purchase(),
  createdAtEpochMillis: 1800000001000, status: "PENDING_CONFIRMATION" });
const recorded = (): RecordedOrderAttempt => recordedOrderAttempt({ ownerUid: purchase().ownerUid, intentId: id,
  requestHash: requestHash(intent()), result: { outcome: "SUCCEEDED", receipt: receipt() } });

for (const v of vectors.validCases) test(`shared valid vector: ${v.name}`, () => {
  const p = parseAcceptedPurchase(v.purchase);
  assert.equal(canonicalConsent(p), v.expected.canonicalConsent);
  assert.equal(consentHash(p), v.expected.consentHash);
  assert.equal(logisticsHash(p), v.expected.logisticsHash);
  assert.equal(requestHash({ intentId: id, accepted: p }), v.expected.requestHash);
  assert.deepEqual(compareOrderReviews(purchase(), p), { requiresAcceptance: v.requiresAcceptance, requiresNewRoute: v.requiresNewRoute });
});
for (const v of vectors.invalidCases) test(`shared invalid vector: ${v.name}`, () => {
  assert.throws(() => parseAcceptedPurchase(v.purchase));
});
test("defensive deeply frozen purchase and intent", () => {
  const input = raw(); const p = parseAcceptedPurchase(input); const before = consentHash(p);
  input.items[0].name = "changed"; input.delivery.destination.instructions = "changed";
  assert.equal(consentHash(p), before);
  assert.throws(() => (p.items as unknown as unknown[]).pop());
  assert.throws(() => { (p.delivery.destination as { instructions: string }).instructions = "changed"; });
  assert.ok(Object.isFrozen(parseOrderIntent({ intentId: id, accepted: p })));
});
test("replay success ignores current date and newly changed permissions", () => {
  assert.deepEqual(resolveOrderIntent(intent(), recorded()), { kind: "REPLAY", result: recorded().result });
});
test("same intent incompatible payload conflicts; new ID is not deduplicated by cart", () => {
  assert.deepEqual(resolveOrderIntent(intent("price-up"), recorded()), { kind: "CONFLICT" });
  assert.notEqual(requestHash(intent()), requestHash({ ...intent(), intentId: "22345678-1234-4234-8234-123456789abc" }));
});
test("final rejection replays", () => {
  const r = recordedOrderAttempt({ ...recorded(), result: { outcome: "REJECTED_FINAL", reason: "QUOTE_EXPIRED" } });
  assert.deepEqual(resolveOrderIntent(intent(), r), { kind: "REPLAY", result: r.result });
});
test("uncertainty cannot become a persisted terminal result", () => {
  for (const reason of ["NOT_OBSERVED", "TIMEOUT", "CONNECTIVITY", "SERVER_UNAVAILABLE"]) {
    const result = { outcome: "UNKNOWN", reason } as OrderAttemptResult;
    assert.throws(() => recordedOrderAttempt({ ...recorded(), result } as RecordedOrderAttempt));
  }
  assert.deepEqual(resolveOrderIntent(intent(), null), { kind: "NEW_INTENT" });
});
test("stored success must match UID, intent and content", () => {
  assert.throws(() => recordedOrderAttempt({ ...recorded(), ownerUid: "another-user" }));
  assert.throws(() => recordedOrderAttempt({ ...recorded(), requestHash: "0".repeat(64) }));
  assert.throws(() => recordedOrderAttempt({ ...recorded(), intentId: "22345678-1234-4234-8234-123456789abc" }));
});
test("only exact safe money numbers cross JSON, bigint remains internal", () => {
  assert.equal(minorFromBigInt(BigInt(MAX_ORDER_MINOR)), MAX_ORDER_MINOR);
  for (const v of [MAX_ORDER_MINOR + 1, 1.5, NaN, Infinity, -1, "150", 150n]) assert.throws(() => orderMinor(v));
  assert.throws(() => minorFromBigInt(BigInt(MAX_ORDER_MINOR) + 1n));
  assert.throws(() => orderAmounts([{ ...purchase().items[0], quantity: 2, unitPriceMinor: MAX_ORDER_MINOR }], 0));
  assert.doesNotThrow(() => JSON.stringify(intent()));
});
test("unknown fields and incorrect JSON types fail instead of coercing", () => {
  for (const patch of [{ extra: true }, { amounts: { ...raw().amounts, totalMinor: "2200" } }, { buyerPhone: false }]) {
    assert.throws(() => parseAcceptedPurchase({ ...raw(), ...patch }));
  }
});
test("missing phone is representable and never a commercial blocker here", () => {
  assert.equal(purchase().buyerPhone, null);
  assert.equal(purchase("contact-present").buyerPhone, "+51999999999");
});
test("unpaired Unicode, identifiers and nonfinite coordinates are invalid", () => {
  assert.throws(() => parseOrderIntent({ intentId: "bad-id", accepted: raw() }));
  for (const name of [String.fromCharCode(0xd800), String.fromCharCode(0xdc00)]) assert.throws(() => parseAcceptedPurchase({ ...raw(), buyerName: name }));
  const v = raw(); v.pickup.location.latitude = NaN; assert.throws(() => parseAcceptedPurchase(v));
  v.pickup.location.latitude = 0; v.deliveryQuote.distanceMeters = Infinity; assert.throws(() => parseAcceptedPurchase(v));
});
test("negative zero has exactly one fingerprint", () => {
  const v = raw("negative-zero"); const p = parseAcceptedPurchase(v); v.pickup.location.latitude = 0;
  assert.equal(consentHash(p), consentHash(parseAcceptedPurchase(v)));
});

test("recovery missing entry is uncertain and another UID cannot recover it", () => {
  assert.deepEqual(recoverOrderAttempt(purchase().ownerUid, id, null), { outcome: "UNKNOWN", reason: "NOT_OBSERVED" });
  assert.deepEqual(recoverOrderAttempt(purchase().ownerUid, id, recorded()), recorded().result);
  assert.throws(() => recoverOrderAttempt("another-uid", id, recorded()));
  const rejection = recordedOrderAttempt({ ...recorded(), ownerUid: "another-uid",
    result: { outcome: "REJECTED_FINAL", reason: "REVIEW_REQUIRED" } });
  assert.deepEqual(resolveOrderIntent(intent(), rejection), { kind: "CONFLICT" });
});
test("timestamps must remain representable by Firestore independently of money", () => {
  const v = raw(); v.deliveryQuote.expiresAtEpochMillis = 253_402_300_800_000;
  assert.throws(() => parseAcceptedPurchase(v));
  assert.throws(() => orderReceipt({ ...receipt(), createdAtEpochMillis: 253_402_300_800_000 }));
});
