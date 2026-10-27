import { Firestore } from "firebase-admin/firestore";
import { checkStoreEligibility } from "../eligibility/storeEligibilityService";
import { InconsistentData } from "../eligibility/storeEligibilityPolicy";
import { deliveryFeeMinor, InvalidRouteDistance, quoteTtlMs, TARIFF_VERSION } from "./deliveryQuotePolicy";
import { Coordinates, RouteFailure, RouteReader, validCoordinates } from "./routeReader";

export type QuoteErrorCode = "INVALID_REQUEST" | "STORE_UNAVAILABLE" | "BUSINESS_SUSPENDED" | "BUSINESS_NOT_APPROVED" |
  "ADDRESS_NOT_FOUND" | "INVALID_LOCATION" | "OUTSIDE_COVERAGE" | "NO_ROUTE" |
  "ROUTING_CREDENTIALS_MISSING" | "ROUTING_CREDENTIALS_INVALID" | "ROUTING_QUOTA_EXCEEDED" |
  "ROUTING_UNAVAILABLE" | "ROUTING_INVALID_RESPONSE" | "DATA_INCONSISTENT";

export class QuoteError extends Error {
  constructor(readonly code: QuoteErrorCode, readonly status: number) { super(code); }
}

export interface DeliveryQuote {
  distanceMeters: number;
  deliveryFeeMinor: number;
  currency: "PEN";
  tariffVersion: string;
  quotedAt: string;
  expiresAt: string;
  storeLocation: Coordinates;
  deliveryLocation: Coordinates;
}

/** One local administrative read, one owned address read, then one external route request. */
export async function createDeliveryQuote(db: Firestore, routes: RouteReader, uid: string,
  storeId: string, addressId: string, now: () => Date = () => new Date(),
  signal?: AbortSignal): Promise<{ quote: DeliveryQuote; firestoreReads: number; routeMs: number }> {
  const ttlMs = quoteTtlMs(process.env.DELIVERY_QUOTE_TTL_SECONDS);
  const { result, storeData, reads } = await checkStoreEligibility(db, storeId);
  if (result.decision !== "ELIGIBLE") throw new QuoteError(result.decision, 409);
  if (!storeData) throw new QuoteError("DATA_INCONSISTENT", 503);

  const origin = coordinates(storeData.latitude, storeData.longitude);
  const rawRadius = storeData.deliveryRadiusKm ?? 5.0;
  if (typeof rawRadius !== "number" || !Number.isFinite(rawRadius) || rawRadius <= 0) {
    throw new QuoteError("DATA_INCONSISTENT", 503);
  }
  const address = await db.doc(`users/${uid}/addresses/${addressId}`).get();
  if (!address.exists) throw new QuoteError("ADDRESS_NOT_FOUND", 404);
  const location = address.data()?.location;
  if (!location || typeof location !== "object" || Array.isArray(location)) {
    throw new QuoteError("INVALID_LOCATION", 422);
  }
  const point = location as Record<string, unknown>;
  const destination = coordinates(point.lat, point.lng);
  if (haversineKm(origin, destination) > rawRadius) throw new QuoteError("OUTSIDE_COVERAGE", 422);

  const routeStarted = performance.now();
  let distanceMeters: number;
  try {
    distanceMeters = await routes.distanceMeters(origin, destination, signal);
  } catch (error) {
    if (error instanceof RouteFailure) throw routeQuoteError(error);
    throw error;
  }
  const routeMs = performance.now() - routeStarted;
  let fee: number;
  try { fee = deliveryFeeMinor(distanceMeters); }
  catch (error) {
    if (error instanceof InvalidRouteDistance) throw new QuoteError("ROUTING_INVALID_RESPONSE", 503);
    throw error;
  }
  const quotedAt = now();
  if (!Number.isFinite(quotedAt.getTime())) throw new QuoteError("DATA_INCONSISTENT", 503);
  return {
    quote: {
      distanceMeters, deliveryFeeMinor: fee, currency: "PEN", tariffVersion: TARIFF_VERSION,
      quotedAt: quotedAt.toISOString(),
      expiresAt: new Date(quotedAt.getTime() + ttlMs).toISOString(),
      storeLocation: origin, deliveryLocation: destination,
    },
    firestoreReads: reads + 1,
    routeMs,
  };
}

function coordinates(latitude: unknown, longitude: unknown): Coordinates {
  if (typeof latitude !== "number" || typeof longitude !== "number") throw new QuoteError("INVALID_LOCATION", 422);
  const point = { latitude, longitude };
  if (!validCoordinates(point)) throw new QuoteError("INVALID_LOCATION", 422);
  return point;
}

/** Matches the current Discover coverage radius, which is based on straight-line distance. */
export function haversineKm(first: Coordinates, second: Coordinates): number {
  const rad = (degrees: number) => degrees * Math.PI / 180;
  const dLat = rad(second.latitude - first.latitude);
  const dLon = rad(second.longitude - first.longitude);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(first.latitude)) * Math.cos(rad(second.latitude)) *
    Math.sin(dLon / 2) ** 2;
  return 6_371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
}

function routeQuoteError(error: RouteFailure): QuoteError {
  const status = error.code === "NO_ROUTE" || error.code === "INVALID_LOCATION" ? 422 : 503;
  return new QuoteError(error.code, status);
}
