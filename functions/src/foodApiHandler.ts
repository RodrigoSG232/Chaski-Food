import { DeviceRegistry } from "./notifications/deviceRegistry";
import { StoreOrdersReader } from "./orders/storeOrdersReader";
import { OrderService } from "./orders/orderService";
import { OrderHttpError, parseOrderSubmission } from "./orders/orderRequest";
import { intentId as validateIntentId } from "./orders/orderModels";
import { CommercialAccessService } from "./access/commercialAccessService";
import { CommercialAccessError, parseCommercialCommand } from "./access/commercialAccessPolicy";
import { verifyCommercialIdentity } from "./auth/verifyBuyerToken";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import type { onRequest } from "firebase-functions/v2/https";
type FoodHttpHandler = Parameters<typeof onRequest>[0];
import type { App } from "firebase-admin/app";
import { AuthenticationRequired, verifyBuyerToken } from "./auth/verifyBuyerToken";
import { InconsistentData } from "./eligibility/storeEligibilityPolicy";
import { checkStoreEligibility } from "./eligibility/storeEligibilityService";
import { createDeliveryQuote, QuoteError } from "./delivery/deliveryQuoteService";
import { parseDeliveryQuoteRequest } from "./delivery/deliveryQuoteRequest";
import { RouteReader } from "./delivery/routeReader";
import { RuntimeEnvironment, routeSourceForRuntime } from "./runtimeEnvironment";

export interface FoodApiDependencies {
  runtime(): RuntimeEnvironment;
  app(runtime: RuntimeEnvironment): App;
  routeFixture?: string;
  routeReader(runtime: RuntimeEnvironment): { reader: RouteReader };
}
/** Same authoritative services in LOCAL and CLOUD; tests inject only platform boundaries. */
export function createFoodApiHandler(dependencies: FoodApiDependencies): FoodHttpHandler {
  return async (request, response) => {
    const started = performance.now();
    const isQuoteRequest = /^\/api\/v1\/delivery-quotes\/?$/.test(request.path);
    response.set("Cache-Control", "no-store");
    try {
      const runtime = dependencies.runtime();
      const orderPath = /^\/api\/v1\/orders\/?$/.test(request.path);
      const attemptPath = /^\/api\/v1\/order-attempts\/([^/]+)\/?$/.exec(request.path);
      const devicePath = /^\/api\/v1\/devices\/([^/]+)\/?$/.exec(request.path);
      if(devicePath) {
        if(runtime.mode!=="LOCAL"){response.status(404).json({error:"NOT_FOUND"});return;}
        if(!["PUT","DELETE"].includes(request.method)){response.status(405).set("Allow","PUT, DELETE").end();return;}
        try {
          const app=dependencies.app(runtime),uid=await verifyBuyerToken(getAuth(app),request.get("authorization"));
          const registry=new DeviceRegistry(getFirestore(app)),id=decodeURIComponent(devicePath[1]);
          if(Object.keys(request.query).some(k=>request.method!=="DELETE"||k!=="bindingVersion"))throw new OrderHttpError("INVALID_REQUEST",400);
          if(request.method==="PUT")await registry.put(uid,id,request.body);else await registry.remove(uid,id,Number(request.query.bindingVersion));
          response.status(204).end();
        }catch(error){
          if(error instanceof AuthenticationRequired)response.status(401).json({error:"AUTHENTICATION_REQUIRED"});
          else if(error instanceof OrderHttpError)response.status(error.status).json({error:error.code});
          else if(error instanceof URIError)response.status(400).json({error:"INVALID_REQUEST"});
          else response.status(503).json({error:"DEVICE_REGISTRY_UNAVAILABLE"});
        }
        return;
      }
      const inboxPath = /^\/api\/v1\/stores\/([^/]+)\/orders\/?$/.exec(request.path);
      if (inboxPath) {
        if (request.method !== "GET") { response.status(405).set("Allow","GET").json({error:"METHOD_NOT_ALLOWED"}); return; }
        try {
          const app=dependencies.app(runtime),auth=getAuth(app);
          const actor=await verifyCommercialIdentity(auth,request.get("authorization"));
          const storeId=decodeURIComponent(inboxPath[1]);
          response.status(200).json(await new StoreOrdersReader(getFirestore(app),auth).page(actor,storeId,request.query));
        } catch(error) {
          if(error instanceof AuthenticationRequired) response.status(401).json({error:"AUTHENTICATION_REQUIRED"});
          else if(error instanceof OrderHttpError) response.status(error.status).json({error:error.code});
          else if(error instanceof CommercialAccessError) {
            const denied=["FORBIDDEN","EMAIL_VERIFICATION_REQUIRED","AUTHORIZATION_CONFLICT"].includes(error.code);
            response.status(denied?403:503).json({error:denied?"ACCESS_DENIED":"AUTHORIZATION_UNAVAILABLE"});
          } else if(error instanceof URIError) response.status(400).json({error:"INVALID_REQUEST"});
          else response.status(503).json({error:"INBOX_UNAVAILABLE"});
        }
        return;
      }
      if (orderPath || attemptPath) {
        const method = orderPath ? "POST" : "GET";
        if (request.method !== method) { response.status(405).set("Allow",method).json({error:{code:"METHOD_NOT_ALLOWED",retryable:false}}); return; }
        try {
          const app=dependencies.app(runtime),auth=getAuth(app);
          const uid=await verifyBuyerToken(auth,request.get("authorization"));
          const service=new OrderService(getFirestore(app),auth);
          let result;
          if(orderPath) result=await service.confirm(parseOrderSubmission(request.body,uid));
          else {
            let id:string;
            try { id=validateIntentId(decodeURIComponent(attemptPath![1])); }
            catch { throw new OrderHttpError("INVALID_REQUEST",400); }
            result=await service.recover(uid,id);
          }
          response.status(result.status).json(result.body);
        } catch(error) {
          if(error instanceof AuthenticationRequired) response.status(401).json({contractVersion:1,error:{code:"AUTHENTICATION_REQUIRED",retryable:false}});
          else if(error instanceof OrderHttpError) response.status(error.status).json({contractVersion:1,error:{code:error.code,retryable:error.retryable},
            ...(error.retryable?{outcome:"UNKNOWN",reason:"SERVER_UNAVAILABLE"}:{})});
          else response.status(503).json({contractVersion:1,outcome:"UNKNOWN",reason:"SERVER_UNAVAILABLE",error:{code:"TEMPORARILY_UNAVAILABLE",retryable:true}});
        }
        return;
      }
      if (request.path.startsWith("/api/v1/access/")) {
        const app = dependencies.app(runtime);
        const actor = await verifyCommercialIdentity(getAuth(app), request.get("authorization"));
        const service = new CommercialAccessService(getFirestore(app), getAuth(app));
        if (request.path === "/api/v1/access/operations" && request.method === "POST") {
          response.status(200).json(await service.execute(actor, parseCommercialCommand(request.body))); return;
        }
        if (request.path === "/api/v1/access/assignment-revision" && request.method === "POST") {
          const body = request.body;
          if (!body || typeof body !== "object" || Array.isArray(body) || !["businessId", "businessId,email"].includes(Object.keys(body).sort().join(","))) throw new CommercialAccessError("INVALID_REQUEST", 400);
          response.status(200).json(await service.assignmentRevision(actor, body.businessId, body.email)); return;
        }
        if (request.path === "/api/v1/access/revision" && request.method === "GET") {
          response.status(200).json(await service.ownRevision(actor)); return;
        }
        if (request.path === "/api/v1/access/facts" && request.method === "GET") {
          response.status(200).json(await service.facts(actor)); return;
        }
        response.status(404).json({ error: "NOT_FOUND" }); return;
      }
      if (isQuoteRequest) {
        if (request.method !== "POST") { response.status(405).set("Allow", "POST").json({ error: "METHOD_NOT_ALLOWED" }); return; }
        const { storeId, addressId, quoteId } = parseDeliveryQuoteRequest(request.body);
        const app = dependencies.app(runtime);
        const uid = await verifyBuyerToken(getAuth(app), request.get("authorization"));
        const controller = new AbortController();
        response.once("close", () => { if (!response.writableEnded) controller.abort(); });
        const source = routeSourceForRuntime(runtime, dependencies.routeFixture);
        const reader: RouteReader = { async distanceMeters(origin, destination, signal) {
          return dependencies.routeReader(runtime).reader.distanceMeters(origin, destination, signal);
        } };
        const { quote, firestoreReads, routeMs, reused } = await createDeliveryQuote(getFirestore(app), reader,
          uid, storeId, addressId, undefined, controller.signal, quoteId);
        response.set("Server-Timing", `quote;dur=${(performance.now() - started).toFixed(2)}, route;dur=${routeMs.toFixed(2)}`);
        response.set("X-Quote-Firestore-Reads", String(firestoreReads));
        response.set("X-Route-Source", source);
        response.set("X-Quote-Reused", String(reused));
        response.status(200).json(quote);
        return;
      }
      const match = /^\/api\/v1\/stores\/([^/]+)\/purchase-eligibility\/?$/.exec(request.path);
      if (!match) { response.status(404).json({ error: "NOT_FOUND" }); return; }
      if (request.method !== "GET") { response.status(405).set("Allow", "GET").json({ error: "METHOD_NOT_ALLOWED" }); return; }
      const storeId = decodeURIComponent(match[1]);
      if (!/^[A-Za-z0-9_-]{1,128}$/.test(storeId)) { response.status(400).json({ error: "INVALID_STORE_ID" }); return; }
      const app = dependencies.app(runtime);
      await verifyBuyerToken(getAuth(app), request.get("authorization"));
      const { result, reads } = await checkStoreEligibility(getFirestore(app), storeId);
      // Métricas de laboratorio; no incluyen documentos ni datos administrativos.
      response.set("Server-Timing", `eligibility;dur=${(performance.now() - started).toFixed(2)}`);
      response.set("X-Eligibility-Reads", String(reads));
      response.status(200).json({ ...result, checkedAt: new Date().toISOString() });
    } catch (error) {
      if (error instanceof AuthenticationRequired) response.status(401).json({ error: "AUTHENTICATION_REQUIRED" });
      else if (error instanceof CommercialAccessError) response.status(error.status).json({ error: error.code });
      else if (error instanceof QuoteError) response.status(error.status).json({ error: error.code });
      else if (error instanceof InconsistentData) response.status(503).json({ error: "DATA_INCONSISTENT" });
      else if (error instanceof URIError) response.status(400).json({ error: "INVALID_STORE_ID" });
      else response.status(503).json({ error: isQuoteRequest ? "QUOTE_UNAVAILABLE" : "ELIGIBILITY_UNAVAILABLE" });
    }
   };
}
