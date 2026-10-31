/** CLOUD policy with explicit demo SDK adapters. No Cloud initialization, credentials or routing calls. */
import assert from "node:assert/strict";
import { App } from "firebase-admin/app";
import { createFoodApiHandler } from "../src/foodApiHandler";
import { resolveRuntimeEnvironment } from "../src/runtimeEnvironment";

export function connectedHttp(app: App) {
  assert.equal(app.options.projectId, "demo-chaski-hu15");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8089");
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099");
  const handler = createFoodApiHandler({
    runtime: () => resolveRuntimeEnvironment({ K_SERVICE: "foodapi", FUNCTION_TARGET: "foodApi", GCLOUD_PROJECT: "chaski-food-9adba" }, "foodApi"),
    app: () => app,
    routeReader: () => { throw new Error("REAL_ROUTE_FORBIDDEN_IN_CONNECTED_TEST"); },
  });
  return async (token: string | undefined, route: string, body?: unknown, method?: string) => {
    const url = new URL(`http://localhost/api/v1${route}`);
    const result: { status: number; body: Record<string, any> } = { status: 200, body: {} };
    const response = { set() { return this; }, status(value: number) { result.status = value; return this; },
      json(value: Record<string, any>) { result.body = value; return this; }, end() { return this; } };
    const request = { path: url.pathname, method: method ?? (body === undefined ? "GET" : "POST"), body,
      query: Object.fromEntries(url.searchParams), get: () => token ? `Bearer ${token}` : undefined };
    await handler(request as unknown as Parameters<typeof handler>[0], response as unknown as Parameters<typeof handler>[1]);
    return result;
  };
}
