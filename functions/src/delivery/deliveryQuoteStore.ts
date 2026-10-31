import { Firestore, Timestamp, Transaction } from "firebase-admin/firestore";
import { businessIdOf, evaluateEligibility } from "../eligibility/storeEligibilityPolicy";
import { logisticsContextHash } from "../orders/orderPolicy";
import { deliveryFeeMinor, QUOTE_TTL_MS, QuoteError, TARIFF_VERSION } from "./deliveryQuotePolicy";
import { Coordinates, validCoordinates } from "./routeReader";

export interface DeliveryQuote {
  quoteId: string; distanceMeters: number; deliveryFeeMinor: number; currency: "PEN"; tariffVersion: string;
  quotedAt: string; expiresAt: string; storeLocation: Coordinates; deliveryLocation: Coordinates;
}
export interface QuoteContext {
  uid: string; storeId: string; addressId: string; businessId: string;
  origin: Coordinates; destination: Coordinates; logisticsHash: string;
}

export async function readQuoteContext(db: Firestore, tx: Transaction, uid: string, storeId: string,
  addressId: string, read: () => void): Promise<QuoteContext> {
  read(); const store = (await tx.get(db.doc(`stores/${storeId}`))).data();
  if (!store) throw new QuoteError("STORE_UNAVAILABLE", 409);
  const businessId = businessIdOf(store);
  read(); const business = (await tx.get(db.doc(`business_requests/${businessId}`))).data();
  const eligibility = evaluateEligibility(store, business);
  if (eligibility !== "ELIGIBLE") throw new QuoteError(eligibility, 409);
  const origin = coordinates(store.latitude, store.longitude);
  const radius = store.deliveryRadiusKm ?? 5;
  if (typeof radius !== "number" || !Number.isFinite(radius) || radius <= 0) throw new QuoteError("DATA_INCONSISTENT", 503);
  read(); const address = (await tx.get(db.doc(`users/${uid}/addresses/${addressId}`))).data();
  if (!address) throw new QuoteError("ADDRESS_NOT_FOUND", 404);
  const destination = coordinates(address.location?.lat, address.location?.lng);
  if (haversineKm(origin, destination) > radius) throw new QuoteError("OUTSIDE_COVERAGE", 422);
  return { uid, storeId, addressId, businessId, origin, destination,
    logisticsHash: logisticsContextHash(uid, storeId, addressId, origin, destination) };
}

/** Checks private persisted evidence, not client-supplied prices/coordinates. */
export function readStoredQuote(quoteId: string, data: Record<string, unknown> | undefined,
  context: QuoteContext, now: Date): DeliveryQuote {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(quoteId)) throw new QuoteError("QUOTE_INVALID", 503);
  if (!data || data.customerUid !== context.uid) throw new QuoteError("QUOTE_NOT_FOUND", 404);
  if (data.storeId !== context.storeId || data.addressId !== context.addressId || data.businessId !== context.businessId ||
      data.logisticsHash !== context.logisticsHash) throw new QuoteError("QUOTE_INCOMPATIBLE", 409);
  if (data.tariffVersion !== TARIFF_VERSION) throw new QuoteError("QUOTE_VERSION_INVALID", 409);
  if (data.schemaVersion !== 1 || data.currency !== "PEN" || !sameCoordinates(data.storeLocation, context.origin) ||
      !sameCoordinates(data.deliveryLocation, context.destination) || !(data.quotedAt instanceof Timestamp) ||
      !(data.expiresAt instanceof Timestamp) || typeof data.distanceMeters !== "number" ||
      !Number.isFinite(data.distanceMeters) || data.distanceMeters < 0 ||
      !Number.isSafeInteger(data.deliveryFeeMinor) || (data.deliveryFeeMinor as number) < 0) throw new QuoteError("QUOTE_INVALID", 503);
  let fee: number;
  try { fee = deliveryFeeMinor(data.distanceMeters); } catch { throw new QuoteError("QUOTE_INVALID", 503); }
  const issued = data.quotedAt.toMillis(), expires = data.expiresAt.toMillis(), current = now.getTime();
  if (!Number.isFinite(current) || !Number.isSafeInteger(issued) || !Number.isSafeInteger(expires) ||
      issued <= 0 || expires - issued !== QUOTE_TTL_MS || current < issued || fee !== data.deliveryFeeMinor) {
    throw new QuoteError("QUOTE_INVALID", 503);
  }
  if (current >= expires) throw new QuoteError("QUOTE_EXPIRED", 409);
  return { quoteId, distanceMeters: data.distanceMeters, deliveryFeeMinor: fee, currency: "PEN", tariffVersion: TARIFF_VERSION,
    quotedAt: data.quotedAt.toDate().toISOString(), expiresAt: data.expiresAt.toDate().toISOString(),
    storeLocation: { ...context.origin }, deliveryLocation: { ...context.destination } };
}

export function quoteDocument(context: QuoteContext, distanceMeters: number, now: Date): Record<string, unknown> {
  if (!Number.isFinite(now.getTime()) || now.getTime() <= 0) throw new QuoteError("DATA_INCONSISTENT", 503);
  return { schemaVersion: 1, customerUid: context.uid, storeId: context.storeId, addressId: context.addressId,
    businessId: context.businessId, logisticsHash: context.logisticsHash, storeLocation: context.origin,
    deliveryLocation: context.destination, distanceMeters, deliveryFeeMinor: deliveryFeeMinor(distanceMeters),
    currency: "PEN", tariffVersion: TARIFF_VERSION, quotedAt: Timestamp.fromDate(now),
    expiresAt: Timestamp.fromMillis(now.getTime() + QUOTE_TTL_MS) };
}
function coordinates(latitude: unknown, longitude: unknown): Coordinates {
  if (typeof latitude !== "number" || typeof longitude !== "number" || !validCoordinates({ latitude, longitude })) {
    throw new QuoteError("INVALID_LOCATION", 422);
  }
  return { latitude, longitude };
}
function sameCoordinates(value: unknown, expected: Coordinates): boolean {
  if (!value || typeof value !== "object") return false;
  const v = value as Coordinates;
  return v.latitude === expected.latitude && v.longitude === expected.longitude;
}
/** Coverage radius is not street routing; preserves the existing Discover policy. */
export function haversineKm(first: Coordinates, second: Coordinates): number {
  const rad = (degrees: number) => degrees * Math.PI / 180;
  const a = Math.sin(rad(second.latitude - first.latitude) / 2) ** 2 + Math.cos(rad(first.latitude)) *
    Math.cos(rad(second.latitude)) * Math.sin(rad(second.longitude - first.longitude) / 2) ** 2;
  return 6_371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
}
