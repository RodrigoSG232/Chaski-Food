export const TARIFF_VERSION = "lima-driving-car-v1";
export const QUOTE_TTL_MS = 5 * 60 * 1000;
const BASE_MINOR = 150n;
const PER_KILOMETER_MINOR = 120n;
const MILLIMETERS_PER_KILOMETER = 1_000_000n;
const MAX_MINOR = BigInt(Number.MAX_SAFE_INTEGER);

export class InvalidRouteDistance extends Error {}

export function quoteTtlMs(configuredSeconds: string | undefined): number {
  if (configuredSeconds === undefined) return QUOTE_TTL_MS;
  if (!/^\d+$/.test(configuredSeconds)) throw new Error("INVALID_QUOTE_TTL");
  const seconds = Number(configuredSeconds);
  if (!Number.isSafeInteger(seconds) || seconds < 60 || seconds > 900) throw new Error("INVALID_QUOTE_TTL");
  return seconds * 1_000;
}

/** Convert the provider's finite decimal meter value to whole millimeters without money arithmetic in Number. */
export function metersToMillimeters(meters: number): bigint {
  if (!Number.isFinite(meters) || meters < 0) throw new InvalidRouteDistance();
  const match = /^(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/i.exec(meters.toString());
  if (!match) throw new InvalidRouteDistance();
  const fractional = match[2] ?? "";
  const exponent = Number(match[3] ?? 0) - fractional.length + 3;
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 100) throw new InvalidRouteDistance();
  const digits = BigInt(match[1] + fractional);
  if (exponent >= 0) return digits * 10n ** BigInt(exponent);
  const divisor = 10n ** BigInt(-exponent);
  return (digits + divisor / 2n) / divisor;
}

/** Round once, at the final cent, with half-cent ties upward. */
export function deliveryFeeMinor(distanceMeters: number): number {
  const distanceMm = metersToMillimeters(distanceMeters);
  const fee = BASE_MINOR +
    (PER_KILOMETER_MINOR * distanceMm + MILLIMETERS_PER_KILOMETER / 2n) / MILLIMETERS_PER_KILOMETER;
  if (fee > MAX_MINOR) throw new InvalidRouteDistance();
  return Number(fee);
}

export type QuoteErrorCode = "INVALID_REQUEST" | "STORE_UNAVAILABLE" | "BUSINESS_SUSPENDED" | "BUSINESS_NOT_APPROVED" |
  "ADDRESS_NOT_FOUND" | "INVALID_LOCATION" | "OUTSIDE_COVERAGE" | "NO_ROUTE" |
  "ROUTING_CREDENTIALS_MISSING" | "ROUTING_CREDENTIALS_INVALID" | "ROUTING_QUOTA_EXCEEDED" |
  "ROUTING_UNAVAILABLE" | "ROUTING_INVALID_RESPONSE" | "DATA_INCONSISTENT" |
  "QUOTE_NOT_FOUND" | "QUOTE_EXPIRED" | "QUOTE_INCOMPATIBLE" | "QUOTE_VERSION_INVALID" | "QUOTE_INVALID";
export class QuoteError extends Error {
  constructor(readonly code: QuoteErrorCode, readonly status: number) { super(code); }
}
