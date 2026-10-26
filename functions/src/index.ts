import { initializeApp } from "firebase-admin/app";
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

const heigitKey = defineSecret("HEIGIT_API_KEY");

// Esta entrega solo se ejecuta con los tres emuladores y el proyecto ficticio.
function requireLocalEnvironment() {
  if (process.env.FUNCTIONS_EMULATOR !== "true" || process.env.GCLOUD_PROJECT !== "demo-chaski-hu15" ||
      process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8089" ||
      process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099") {
    throw new Error("LOCAL_ENVIRONMENT_REQUIRED");
  }
}

export const foodApi = onRequest({ region: "us-central1", timeoutSeconds: 15, minInstances: 0, maxInstances: 1,
  secrets: [heigitKey] },
  async (request, response) => {
    const started = performance.now();
    const isQuoteRequest = /^\/api\/v1\/delivery-quotes\/?$/.test(request.path);
    response.set("Cache-Control", "no-store");
    try {
      requireLocalEnvironment();
      if (isQuoteRequest) {
        if (request.method !== "POST") { response.status(405).set("Allow", "POST").json({ error: "METHOD_NOT_ALLOWED" }); return; }
        const { storeId, addressId } = parseDeliveryQuoteRequest(request.body);
        const app = getLocalApp();
        const uid = await verifyBuyerToken(getAuth(app), request.get("authorization"));
        const controller = new AbortController();
        response.once("close", () => { if (!response.writableEnded) controller.abort(); });
        const { reader, source } = routeReader();
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
      const app = getLocalApp();
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

let localApp: ReturnType<typeof initializeApp> | undefined;
function getLocalApp() {
  return localApp ??= initializeApp({ projectId: "demo-chaski-hu15" });
}

function routeReader(): { reader: RouteReader; source: "HEIGIT" | "SIMULATED" } {
  // Explicit fixture for the isolated demo project only; never a fallback after a real route fails.
  if (process.env.CHASKI_ROUTE_FIXTURE === "enabled") {
    return { reader: { async distanceMeters() { return 1_250.5; } }, source: "SIMULATED" };
  }
  return { reader: new OpenRouteServiceReader(heigitKey.value()), source: "HEIGIT" };
}
