export interface Coordinates { latitude: number; longitude: number }

export type RouteFailureCode = "NO_ROUTE" | "INVALID_LOCATION" | "ROUTING_CREDENTIALS_MISSING" |
  "ROUTING_CREDENTIALS_INVALID" | "ROUTING_QUOTA_EXCEEDED" | "ROUTING_UNAVAILABLE" | "ROUTING_INVALID_RESPONSE";

export class RouteFailure extends Error {
  constructor(readonly code: RouteFailureCode) { super(code); }
}

export interface RouteReader {
  distanceMeters(origin: Coordinates, destination: Coordinates, signal?: AbortSignal): Promise<number>;
}

export function validCoordinates(point: Coordinates): boolean {
  return Number.isFinite(point.latitude) && Number.isFinite(point.longitude) &&
    point.latitude >= -90 && point.latitude <= 90 && point.longitude >= -180 && point.longitude <= 180;
}
