import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { AuthenticationRequired, verifyBuyerToken } from "./auth/verifyBuyerToken";
import { InconsistentData } from "./eligibility/storeEligibilityPolicy";
import { checkStoreEligibility } from "./eligibility/storeEligibilityService";

// Esta entrega solo se ejecuta con los tres emuladores y el proyecto ficticio.
function requireLocalEnvironment() {
  if (process.env.FUNCTIONS_EMULATOR !== "true" || process.env.GCLOUD_PROJECT !== "demo-chaski-hu15" ||
      process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8089" ||
      process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099") {
    throw new Error("LOCAL_ENVIRONMENT_REQUIRED");
  }
}

export const foodApi = onRequest({ region: "us-central1", timeoutSeconds: 15, minInstances: 0, maxInstances: 1 },
  async (request, response) => {
    const started = performance.now();
    response.set("Cache-Control", "no-store");
    try {
      requireLocalEnvironment();
      if (request.method !== "GET") { response.status(405).set("Allow", "GET").json({ error: "METHOD_NOT_ALLOWED" }); return; }
      const match = /^\/api\/v1\/stores\/([^/]+)\/purchase-eligibility\/?$/.exec(request.path);
      if (!match) { response.status(404).json({ error: "NOT_FOUND" }); return; }
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
      else if (error instanceof InconsistentData) response.status(503).json({ error: "DATA_INCONSISTENT" });
      else if (error instanceof URIError) response.status(400).json({ error: "INVALID_STORE_ID" });
      else response.status(503).json({ error: "ELIGIBILITY_UNAVAILABLE" });
    }
  });

let localApp: ReturnType<typeof initializeApp> | undefined;
function getLocalApp() {
  return localApp ??= initializeApp({ projectId: "demo-chaski-hu15" });
}
