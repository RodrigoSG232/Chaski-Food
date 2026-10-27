import { test } from "node:test";
import assert from "node:assert/strict";
import { OpenRouteServiceReader } from "../src/delivery/openRouteServiceReader";
import { RouteFailure } from "../src/delivery/routeReader";

const origin = { latitude: -12.04596, longitude: -77.03054 };
const destination = { latitude: -12.0515, longitude: -77.0345 };

test("envía solo coordenadas en orden longitud, latitud y lee la distancia", async () => {
  let called = false;
  const request: typeof fetch = async (input, init) => {
    called = true;
    assert.equal(input, "https://api.heigit.org/openrouteservice/v2/directions/driving-car/json");
    assert.deepEqual(JSON.parse(String(init?.body)), { coordinates: [
      [-77.03054, -12.04596], [-77.0345, -12.0515],
    ] });
    assert.equal((init?.headers as Record<string, string>).Authorization, "test-only-key");
    return Response.json({ routes: [{ summary: { distance: 1_837.5, duration: 260 } }] });
  };
  assert.equal(await new OpenRouteServiceReader("test-only-key", request).distanceMeters(origin, destination), 1_837.5);
  assert.equal(called, true);
});

test("sin clave y con coordenadas inválidas no consulta la red", async () => {
  const never: typeof fetch = async () => { throw new Error("red consultada"); };
  await assert.rejects(new OpenRouteServiceReader(undefined, never).distanceMeters(origin, destination),
    (error: unknown) => error instanceof RouteFailure && error.code === "ROUTING_CREDENTIALS_MISSING");
  await assert.rejects(new OpenRouteServiceReader("key", never).distanceMeters(origin, { latitude: 91, longitude: 0 }),
    (error: unknown) => error instanceof RouteFailure && error.code === "INVALID_LOCATION");
});

for (const [status, body, expected] of [
  [404, { error: { code: 2009 } }, "NO_ROUTE"],
  [400, { error: { code: 2010 } }, "INVALID_LOCATION"],
  [401, {}, "ROUTING_CREDENTIALS_INVALID"],
  [403, {}, "ROUTING_QUOTA_EXCEEDED"],
  [429, {}, "ROUTING_QUOTA_EXCEEDED"],
  [503, {}, "ROUTING_UNAVAILABLE"],
] as const) {
  test(`respuesta HTTP ${status} se traduce a ${expected}`, async () => {
    const reader = new OpenRouteServiceReader("key", async () => Response.json(body, { status }));
    await assert.rejects(reader.distanceMeters(origin, destination),
      (error: unknown) => error instanceof RouteFailure && error.code === expected);
  });
}

for (const body of [{}, { routes: [] }, { routes: [{}] },
  { routes: [{ summary: { distance: -1 } }] }, { routes: [{ summary: { distance: "100" } }] }]) {
  test("respuesta incompleta o malformada se rechaza", async () => {
    const reader = new OpenRouteServiceReader("key", async () => Response.json(body));
    await assert.rejects(reader.distanceMeters(origin, destination),
      (error: unknown) => error instanceof RouteFailure && error.code === "ROUTING_INVALID_RESPONSE");
  });
}

test("timeout y cancelación externa", async () => {
  let requests = 0;
  let aborted = 0;
  const hanging: typeof fetch = async (_input, init) => new Promise((_resolve, reject) => {
    requests++;
    init?.signal?.addEventListener("abort", () => {
      aborted++;
      reject(new Error("aborted"));
    }, { once: true });
  });
  // AbortSignal.timeout usa un temporizador sin referencia; el test necesita mantener vivo Node.
  const keepAlive = setInterval(() => undefined, 1_000);
  try {
    await assert.rejects(new OpenRouteServiceReader("key", hanging, 5).distanceMeters(origin, destination),
      (error: unknown) => error instanceof RouteFailure && error.code === "ROUTING_UNAVAILABLE");
    assert.equal(aborted, 1);
    const controller = new AbortController();
    const pending = new OpenRouteServiceReader("key", hanging).distanceMeters(origin, destination, controller.signal);
    controller.abort(new Error("cancelled by client"));
    await assert.rejects(pending, /cancelled by client/);
    assert.equal(aborted, 2);
    assert.equal(requests, 2); // Sin reintentos.
  } finally {
    clearInterval(keepAlive);
  }
});
