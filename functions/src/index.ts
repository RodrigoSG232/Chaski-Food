import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { AuthenticationRequired, verifyBuyerToken } from "./auth/verifyBuyerToken";
import { InconsistentData } from "./eligibility/storeEligibilityPolicy";
import { checkStoreEligibility } from "./eligibility/storeEligibilityService";
import { createDeliveryQuote, QuoteError } from "./delivery/deliveryQuoteService";
import { parseDeliveryQuoteRequest } from "./delivery/deliveryQuoteRequest";
import { OpenRouteServiceReader } from "./delivery/openRouteServiceReader";
import { RouteReader } from "./delivery/routeReader";
import { initializeRuntimeApp, resolveRuntimeEnvironment, routeSourceForRuntime } from "./runtimeEnvironment";

const heigitKey = defineSecret("HEIGIT_API_KEY");

export const foodApi = onRequest({ region: "us-central1", timeoutSeconds: 15, minInstances: 0, maxInstances: 1,
  concurrency: 10, invoker: "public", secrets: [heigitKey] },
  async (request, response) => {
    const started = performance.now();
    const isQuoteRequest = /^\/api\/v1\/delivery-quotes\/?$/.test(request.path);
    response.set("Cache-Control", "no-store");
    try {
      const runtime = resolveRuntimeEnvironment(process.env);
      if (isQuoteRequest) {
        if (request.method !== "POST") { response.status(405).set("Allow", "POST").json({ error: "METHOD_NOT_ALLOWED" }); return; }
        const { storeId, addressId } = parseDeliveryQuoteRequest(request.body);
        const app = getRuntimeApp(runtime);
        const uid = await verifyBuyerToken(getAuth(app), request.get("authorization"));
        const controller = new AbortController();
        response.once("close", () => { if (!response.writableEnded) controller.abort(); });
        const { reader, source } = routeReader(runtime);
        const { quote, firestoreReads, routeMs } = await createDeliveryQuote(getFirestore(app), reader,
          uid, storeId, addressId, undefined, controller.signal);
        response.set("Server-Timing", `quote;dur=${(performance.now() - started).toFixed(2)}, route;dur=${routeMs.toFixed(2)}`);
        response.set("X-Quote-Firestore-Reads", String(firestoreReads));
        response.set("X-Route-Source", source);
        response.status(200).json(quote);
        return;
      }
      const match = /^\/api\/v1\/stores\/([^/]+)\/purchase-eligibility\/?$/.exec(request.path);
      if (!match) { response.status(404).json({ error: "NOT_FOUND" }); return; }
      if (request.method !== "GET") { response.status(405).set("Allow", "GET").json({ error: "METHOD_NOT_ALLOWED" }); return; }
      const storeId = decodeURIComponent(match[1]);
      if (!/^[A-Za-z0-9_-]{1,128}$/.test(storeId)) { response.status(400).json({ error: "INVALID_STORE_ID" }); return; }
      const app = getRuntimeApp(runtime);
      await verifyBuyerToken(getAuth(app), request.get("authorization"));
      const { result, reads } = await checkStoreEligibility(getFirestore(app), storeId);
      // Métricas de laboratorio; no incluyen documentos ni datos administrativos.
      response.set("Server-Timing", `eligibility;dur=${(performance.now() - started).toFixed(2)}`);
      response.set("X-Eligibility-Reads", String(reads));
      response.status(200).json({ ...result, checkedAt: new Date().toISOString() });
    } catch (error) {
      if (error instanceof AuthenticationRequired) response.status(401).json({ error: "AUTHENTICATION_REQUIRED" });
      else if (error instanceof QuoteError) response.status(error.status).json({ error: error.code });
      else if (error instanceof InconsistentData) response.status(503).json({ error: "DATA_INCONSISTENT" });
      else if (error instanceof URIError) response.status(400).json({ error: "INVALID_STORE_ID" });
      else response.status(503).json({ error: isQuoteRequest ? "QUOTE_UNAVAILABLE" : "ELIGIBILITY_UNAVAILABLE" });
    }
  });

let runtimeApp: ReturnType<typeof initializeRuntimeApp> | undefined;
function getRuntimeApp(runtime: ReturnType<typeof resolveRuntimeEnvironment>) {
  return runtimeApp ??= initializeRuntimeApp(runtime);
}

function routeReader(runtime: ReturnType<typeof resolveRuntimeEnvironment>): { reader: RouteReader; source: "HEIGIT" | "SIMULATED" } {
  const source = routeSourceForRuntime(runtime, process.env.CHASKI_ROUTE_FIXTURE);
  if (source === "SIMULATED") {
    return { reader: { async distanceMeters() { return 1_250.5; } }, source: "SIMULATED" };
  }
  const key = heigitKey.value();
  if (!key?.trim()) throw new QuoteError("ROUTING_CREDENTIALS_MISSING", 503);
  return { reader: new OpenRouteServiceReader(key), source: "HEIGIT" };
}
