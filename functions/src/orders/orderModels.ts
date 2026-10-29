import { validCoordinates } from "../delivery/routeReader";

/** HU16 v1: pure contracts, not an HTTP handler or persistence adapter. */
export const MAX_ORDER_MINOR = Number.MAX_SAFE_INTEGER;
export const MAX_ORDER_EPOCH_MILLIS = 253_402_300_799_999;
export type OrderStatus = "PENDING_CONFIRMATION";
export type OrderItem = Readonly<{ productId: string; categoryId: string; name: string; quantity: number; unitPriceMinor: number }>;
export type OrderAmounts = Readonly<{ subtotalMinor: number; deliveryFeeMinor: number; totalMinor: number }>;
export type OrderCoordinates = Readonly<{ latitude: number; longitude: number }>;
export type OrderLocation = Readonly<{ addressText: string; location: OrderCoordinates; reference: string | null; instructions: string | null }>;
export type AcceptedPurchase = Readonly<{
  ownerUid: string; businessId: string; storeId: string; businessName: string; storeName: string; storePhone: string | null;
  buyerName: string; buyerPhone: string | null; pickup: OrderLocation;
  delivery: Readonly<{ addressId: string; label: string; destination: OrderLocation }>;
  items: readonly OrderItem[]; amounts: OrderAmounts; currency: "PEN"; paymentMethod: "CASH_ON_DELIVERY";
  deliveryQuote: Readonly<{ quoteId: string; distanceMeters: number; tariffVersion: string;
    quotedAtEpochMillis: number; expiresAtEpochMillis: number }>;
}>;
export type OrderIntent = Readonly<{ intentId: string; accepted: AcceptedPurchase }>;
export type OrderReceipt = Readonly<{ orderId: string; intentId: string; accepted: AcceptedPurchase;
  createdAtEpochMillis: number; status: OrderStatus }>;
export type OrderRejection = "REVIEW_REQUIRED" | "QUOTE_EXPIRED" | "PURCHASE_FORBIDDEN" | "BUSINESS_UNAVAILABLE" |
  "STORE_UNAVAILABLE" | "NO_RESPONSIBLE" | "INVALID_LOCATION" | "OUTSIDE_COVERAGE";
export type OrderAttemptResult = Readonly<{ outcome: "SUCCEEDED"; receipt: OrderReceipt }> |
  Readonly<{ outcome: "REJECTED_FINAL"; reason: OrderRejection }> |
  Readonly<{ outcome: "UNKNOWN"; reason: "NOT_OBSERVED" | "TIMEOUT" | "CONNECTIVITY" | "SERVER_UNAVAILABLE" }>;
export type TerminalOrderResult = Exclude<OrderAttemptResult, { outcome: "UNKNOWN" }>;
export type RecordedOrderAttempt = Readonly<{ ownerUid: string; intentId: string; requestHash: string; result: TerminalOrderResult }>;

function check(condition: unknown): asserts condition { if (!condition) throw new Error("INVALID_ORDER_CONTRACT"); }
export function orderMinor(value: unknown): number {
  check(typeof value === "number" && Number.isSafeInteger(value) && value >= 0);
  return value === 0 ? 0 : value;
}
/** bigint remains internal; only checked JSON-safe integer numbers cross the contract. */
export function minorFromBigInt(value: bigint): number {
  check(value >= 0n && value <= BigInt(MAX_ORDER_MINOR)); return Number(value);
}
export function orderAmounts(items: readonly OrderItem[], deliveryFeeMinor: number): OrderAmounts {
  check(items.length > 0);
  const subtotal = items.reduce((sum, item) => {
    check(Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 2_147_483_647);
    check(orderMinor(item.unitPriceMinor) > 0);
    return sum + BigInt(item.quantity) * BigInt(item.unitPriceMinor);
  }, 0n);
  return Object.freeze({ subtotalMinor: minorFromBigInt(subtotal), deliveryFeeMinor: orderMinor(deliveryFeeMinor),
    totalMinor: minorFromBigInt(subtotal + BigInt(deliveryFeeMinor)) });
}
function record(value: unknown, keys: string[]): Record<string, unknown> {
  check(value !== null && typeof value === "object" && !Array.isArray(value));
  const data = value as Record<string, unknown>;
  check(Object.keys(data).sort().join(",") === [...keys].sort().join(",")); return data;
}
export function orderText(value: unknown, optional = false): string {
  check(typeof value === "string" && (optional || value.trim().length > 0));
  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdbff) { const next = value.charCodeAt(++i); check(next >= 0xdc00 && next <= 0xdfff); }
    else check(c < 0xdc00 || c > 0xdfff);
  }
  return value;
}
export function orderId(value: unknown): string {
  const id = orderText(value); check(id !== "." && id !== ".." && !id.includes("/")); return id;
}
export function intentId(value: unknown): string {
  const id = orderText(value);
  check(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)); return id;
}
function nullableText(value: unknown): string | null { return value === null ? null : orderText(value, true); }
function phone(value: unknown): string | null {
  if (value === null) return null;
  const result = orderText(value); check(/^[+][1-9][0-9]{7,14}$/.test(result)); return result;
}
function location(value: unknown): OrderLocation {
  const v = record(value, ["addressText", "location", "reference", "instructions"]);
  const c = record(v.location, ["latitude", "longitude"]);
  check(typeof c.latitude === "number" && typeof c.longitude === "number");
  check(validCoordinates({ latitude: c.latitude, longitude: c.longitude }));
  return Object.freeze({ addressText: orderText(v.addressText), location: Object.freeze({ latitude: c.latitude, longitude: c.longitude }),
    reference: nullableText(v.reference), instructions: nullableText(v.instructions) });
}
export function parseAcceptedPurchase(value: unknown): AcceptedPurchase {
  const v = record(value, ["ownerUid", "businessId", "storeId", "businessName", "storeName", "storePhone", "buyerName", "buyerPhone",
    "pickup", "delivery", "items", "amounts", "deliveryQuote", "currency", "paymentMethod"]);
  check(v.currency === "PEN" && v.paymentMethod === "CASH_ON_DELIVERY");
  check(Array.isArray(v.items));
  const items = v.items.map(raw => {
    const i = record(raw, ["productId", "categoryId", "name", "quantity", "unitPriceMinor"]);
    check(typeof i.quantity === "number" && Number.isInteger(i.quantity) && i.quantity > 0 && i.quantity <= 2_147_483_647);
    check(orderMinor(i.unitPriceMinor) > 0);
    return Object.freeze({ productId: orderId(i.productId), categoryId: orderId(i.categoryId), name: orderText(i.name),
      quantity: i.quantity, unitPriceMinor: i.unitPriceMinor as number });
  }).sort((a, b) => a.productId < b.productId ? -1 : a.productId > b.productId ? 1 : 0);
  check(new Set(items.map(i => i.productId)).size === items.length);
  const q = record(v.deliveryQuote, ["quoteId", "distanceMeters", "tariffVersion", "quotedAtEpochMillis", "expiresAtEpochMillis"]);
  check(typeof q.distanceMeters === "number" && Number.isFinite(q.distanceMeters) && q.distanceMeters >= 0);
  const quotedAt = orderMinor(q.quotedAtEpochMillis), expiresAt = orderMinor(q.expiresAtEpochMillis);
  check(quotedAt > 0 && expiresAt > quotedAt && expiresAt <= MAX_ORDER_EPOCH_MILLIS);
  const a = record(v.amounts, ["subtotalMinor", "deliveryFeeMinor", "totalMinor"]);
  const amounts = orderAmounts(items, orderMinor(a.deliveryFeeMinor));
  check(orderMinor(a.subtotalMinor) === amounts.subtotalMinor && orderMinor(a.totalMinor) === amounts.totalMinor);
  const d = record(v.delivery, ["addressId", "label", "destination"]);
  return Object.freeze({ ownerUid: orderId(v.ownerUid), businessId: orderId(v.businessId), storeId: orderId(v.storeId),
    businessName: orderText(v.businessName), storeName: orderText(v.storeName), storePhone: phone(v.storePhone),
    buyerName: orderText(v.buyerName), buyerPhone: phone(v.buyerPhone), pickup: location(v.pickup),
    delivery: Object.freeze({ addressId: orderId(d.addressId), label: orderText(d.label), destination: location(d.destination) }),
    items: Object.freeze(items), amounts, currency: v.currency, paymentMethod: v.paymentMethod,
    deliveryQuote: Object.freeze({ quoteId: orderId(q.quoteId), distanceMeters: q.distanceMeters, tariffVersion: orderText(q.tariffVersion),
      quotedAtEpochMillis: quotedAt, expiresAtEpochMillis: expiresAt }) });
}
export function parseOrderIntent(value: unknown): OrderIntent {
  const v = record(value, ["intentId", "accepted"]);
  return Object.freeze({ intentId: intentId(v.intentId), accepted: parseAcceptedPurchase(v.accepted) });
}
