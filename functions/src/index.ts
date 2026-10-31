import { getApps } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { createFoodApiHandler } from "./foodApiHandler";
import { QuoteError } from "./delivery/deliveryQuoteService";
import { OpenRouteServiceReader } from "./delivery/openRouteServiceReader";
import { RouteReader } from "./delivery/routeReader";
import { initializeRuntimeApp, resolveRuntimeEnvironment, routeSourceForRuntime, RuntimeConfigurationError } from "./runtimeEnvironment";

const heigitKey = defineSecret("HEIGIT_API_KEY");
export const foodApi = onRequest({ region: "southamerica-west1", timeoutSeconds: 15, minInstances: 0, maxInstances: 1,
  concurrency: 10, invoker: "public", secrets: [heigitKey] }, createFoodApiHandler({
    runtime: () => resolveRuntimeEnvironment(process.env, "foodApi"),
    app: runtime => {
      const app = getApps().find(candidate => candidate.name === "[DEFAULT]") ?? initializeRuntimeApp(runtime);
      if (app.options.projectId !== runtime.projectId) throw new RuntimeConfigurationError();
      return app;
    },
    routeFixture: process.env.CHASKI_ROUTE_FIXTURE,
    routeReader,
  }));

function routeReader(runtime: ReturnType<typeof resolveRuntimeEnvironment>): { reader: RouteReader; source: "HEIGIT" | "SIMULATED" } {
  const source = routeSourceForRuntime(runtime, process.env.CHASKI_ROUTE_FIXTURE);
  if (source === "SIMULATED") {
    return { reader: { async distanceMeters() { return 1_250.5; } }, source: "SIMULATED" };
  }
  const key = heigitKey.value();
  if (!key?.trim()) throw new QuoteError("ROUTING_CREDENTIALS_MISSING", 503);
  return { reader: new OpenRouteServiceReader(key), source: "HEIGIT" };
}

export { newOrderNotifier } from "./notifications/newOrderNotifier";
