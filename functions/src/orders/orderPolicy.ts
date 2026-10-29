import { createHash } from "node:crypto";
import { AcceptedPurchase, OrderCoordinates, OrderIntent, OrderLocation, OrderReceipt, RecordedOrderAttempt,
  MAX_ORDER_EPOCH_MILLIS, OrderAttemptResult, TerminalOrderResult, intentId, orderId, orderMinor, parseAcceptedPurchase, parseOrderIntent } from "./orderModels";

/** Same hu16-v1 projection as Kotlin. No locale-dependent sorting or decimal coordinate formatting. */
function bits(value: number): string {
  const bytes = Buffer.alloc(8); bytes.writeDoubleBE(value === 0 ? 0 : value); return bytes.toString("hex");
}
function coordinates(v: OrderCoordinates) { return { latitudeBits: bits(v.latitude), longitudeBits: bits(v.longitude) }; }
function location(v: OrderLocation) { return { ...v, location: coordinates(v.location) }; }
function purchase(v: AcceptedPurchase) {
  return { ...v, pickup: location(v.pickup), delivery: { ...v.delivery, destination: location(v.delivery.destination) },
    deliveryQuote: { quoteId: v.deliveryQuote.quoteId, distanceBits: bits(v.deliveryQuote.distanceMeters),
      tariffVersion: v.deliveryQuote.tariffVersion, quotedAtEpochMillis: v.deliveryQuote.quotedAtEpochMillis,
      expiresAtEpochMillis: v.deliveryQuote.expiresAtEpochMillis } };
}
function canonical(value: unknown): string {
  if (value === null || typeof value === "string" || typeof value === "number") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  const v = value as Record<string, unknown>;
  return `{${Object.keys(v).sort().map(key => `${JSON.stringify(key)}:${canonical(v[key])}`).join(",")}}`;
}
function sha256(value: string): string { return createHash("sha256").update(value, "utf8").digest("hex"); }
export function canonicalConsent(value: AcceptedPurchase): string {
  return canonical({ version: "hu16-consent-v1", accepted: purchase(parseAcceptedPurchase(value)) });
}
export function canonicalLogistics(value: AcceptedPurchase): string {
  const v = parseAcceptedPurchase(value);
  return canonical({ version: "hu16-logistics-v1", ownerUid: v.ownerUid, storeId: v.storeId,
    addressId: v.delivery.addressId, origin: coordinates(v.pickup.location), destination: coordinates(v.delivery.destination.location) });
}
export function canonicalRequest(value: OrderIntent): string {
  const v = parseOrderIntent(value);
  return canonical({ version: "hu16-request-v1", intentId: v.intentId, accepted: purchase(v.accepted) });
}
export const consentHash = (value: AcceptedPurchase): string => sha256(canonicalConsent(value));
export const logisticsHash = (value: AcceptedPurchase): string => sha256(canonicalLogistics(value));
export const requestHash = (value: OrderIntent): string => sha256(canonicalRequest(value));
export function compareOrderReviews(accepted: AcceptedPurchase, current: AcceptedPurchase) {
  return Object.freeze({ requiresAcceptance: consentHash(accepted) !== consentHash(current),
    requiresNewRoute: logisticsHash(accepted) !== logisticsHash(current) });
}
export function orderReceipt(order: OrderReceipt): OrderReceipt {
  const accepted = parseAcceptedPurchase(order.accepted);
  if (order.status !== "PENDING_CONFIRMATION" || (orderMinor(order.createdAtEpochMillis) === 0 || order.createdAtEpochMillis > MAX_ORDER_EPOCH_MILLIS)) throw new Error("INVALID_ORDER_CONTRACT");
  return Object.freeze({ orderId: orderId(order.orderId), intentId: intentId(order.intentId), accepted,
    createdAtEpochMillis: order.createdAtEpochMillis, status: order.status });
}
const rejections = ["REVIEW_REQUIRED", "QUOTE_EXPIRED", "PURCHASE_FORBIDDEN", "BUSINESS_UNAVAILABLE", "STORE_UNAVAILABLE",
  "NO_RESPONSIBLE", "INVALID_LOCATION", "OUTSIDE_COVERAGE"];
export function recordedOrderAttempt(value: RecordedOrderAttempt): RecordedOrderAttempt {
  const uid = orderId(value.ownerUid), id = intentId(value.intentId);
  if (!/^[0-9a-f]{64}$/.test(value.requestHash)) throw new Error("INVALID_ORDER_CONTRACT");
  let result: TerminalOrderResult;
  if (value.result.outcome === "SUCCEEDED") {
    const receipt = orderReceipt(value.result.receipt);
    if (receipt.accepted.ownerUid !== uid || receipt.intentId !== id ||
        requestHash({ intentId: id, accepted: receipt.accepted }) !== value.requestHash) throw new Error("INVALID_ORDER_CONTRACT");
    result = Object.freeze({ outcome: "SUCCEEDED", receipt });
  } else if (value.result.outcome === "REJECTED_FINAL" && rejections.includes(value.result.reason)) {
    result = Object.freeze({ outcome: "REJECTED_FINAL", reason: value.result.reason });
  } else throw new Error("INVALID_ORDER_CONTRACT");
  return Object.freeze({ ownerUid: uid, intentId: id, requestHash: value.requestHash, result });
}
export type IntentResolution = Readonly<{ kind: "NEW_INTENT" | "CONFLICT" }> | Readonly<{ kind: "REPLAY"; result: TerminalOrderResult }>;
export function resolveOrderIntent(intent: OrderIntent, recorded: RecordedOrderAttempt | null): IntentResolution {
  const v = parseOrderIntent(intent);
  if (recorded === null) return { kind: "NEW_INTENT" }; // Not evidence of rollback or permission to replace an uncertain intent.
  const previous = recordedOrderAttempt(recorded);
  if (previous.ownerUid !== v.accepted.ownerUid || previous.intentId !== v.intentId || previous.requestHash !== requestHash(v)) return { kind: "CONFLICT" };
  return { kind: "REPLAY", result: previous.result };
}

/** Missing ledger entry remains uncertain; it is not a final rejection. */
export function recoverOrderAttempt(ownerUid: string, id: string, recorded: RecordedOrderAttempt | null): OrderAttemptResult {
  orderId(ownerUid); intentId(id);
  if (recorded === null) return Object.freeze({ outcome: "UNKNOWN", reason: "NOT_OBSERVED" });
  const previous = recordedOrderAttempt(recorded);
  if (previous.ownerUid !== ownerUid || previous.intentId !== id) throw new Error("INVALID_ORDER_CONTRACT");
  return previous.result;
}
