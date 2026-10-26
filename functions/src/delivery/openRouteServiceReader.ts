import { Coordinates, RouteFailure, RouteReader, validCoordinates } from "./routeReader";

const URL = "https://api.heigit.org/openrouteservice/v2/directions/driving-car/json";

/** The API key never leaves Functions; neither coordinates nor the key are logged. */
export class OpenRouteServiceReader implements RouteReader {
  constructor(private readonly key: string | undefined, private readonly request: typeof fetch = fetch,
    private readonly timeoutMs = 5_000) {}

  async distanceMeters(origin: Coordinates, destination: Coordinates, signal?: AbortSignal): Promise<number> {
    if (!validCoordinates(origin) || !validCoordinates(destination)) throw new RouteFailure("INVALID_LOCATION");
    if (!this.key?.trim()) throw new RouteFailure("ROUTING_CREDENTIALS_MISSING");
    const timeout = AbortSignal.timeout(this.timeoutMs);
    const combined = signal ? AbortSignal.any([signal, timeout]) : timeout;
    let response: Response;
    try {
      response = await this.request(URL, {
        method: "POST",
        headers: { Authorization: this.key, "Content-Type": "application/json" },
        body: JSON.stringify({ coordinates: [
          [origin.longitude, origin.latitude], [destination.longitude, destination.latitude],
        ] }),
        signal: combined,
      });
    } catch (error) {
      if (signal?.aborted) throw signal.reason ?? error;
      throw new RouteFailure("ROUTING_UNAVAILABLE");
    }
    if (response.status === 401) throw new RouteFailure("ROUTING_CREDENTIALS_INVALID");
    if (response.status === 429) throw new RouteFailure("ROUTING_QUOTA_EXCEEDED");
    if (response.status === 403) {
      // HeiGIT documents 403 for exhausted daily quota; other 403 responses remain closed.
      throw new RouteFailure("ROUTING_QUOTA_EXCEEDED");
    }
    let body: unknown;
    try { body = await response.json(); }
    catch (error) {
      if (signal?.aborted) throw signal.reason ?? error;
      throw new RouteFailure(combined.aborted ? "ROUTING_UNAVAILABLE" : "ROUTING_INVALID_RESPONSE");
    }
    if (!response.ok) {
      const code = routeErrorCode(body);
      if (code === 2009 || code === 2016) throw new RouteFailure("NO_ROUTE");
      if (code === 2002 || code === 2003 || code === 2010 || code === 2013 || code === 2014 || code === 2015) {
        throw new RouteFailure("INVALID_LOCATION");
      }
      throw new RouteFailure("ROUTING_UNAVAILABLE");
    }
    const route = record(body)?.routes;
    const summary = Array.isArray(route) && route.length === 1 ? record(route[0])?.summary : undefined;
    const distance = record(summary)?.distance;
    if (typeof distance !== "number" || !Number.isFinite(distance) || distance < 0) {
      throw new RouteFailure("ROUTING_INVALID_RESPONSE");
    }
    return distance;
  }
}

function record(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function routeErrorCode(value: unknown): number | undefined {
  const code = record(record(value)?.error)?.code;
  return typeof code === "number" ? code : undefined;
}
