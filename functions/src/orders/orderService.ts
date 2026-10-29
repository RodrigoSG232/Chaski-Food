import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp, Transaction } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { CommercialAccessError, normalizedEmail } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { evaluateEligibility, businessIdOf } from "../eligibility/storeEligibilityPolicy";
import { readStoredQuote, haversineKm, QuoteContext } from "../delivery/deliveryQuoteStore";
import { QuoteError } from "../delivery/deliveryQuotePolicy";
import { validCoordinates } from "../delivery/routeReader";
import { AcceptedPurchase, OrderIntent, OrderItem, orderAmounts, parseAcceptedPurchase, intentId } from "./orderModels";
import { consentHash, logisticsContextHash, requestHash, recordedOrderAttempt } from "./orderPolicy";
import { OrderHttpError } from "./orderRequest";
import { effectivePriceMinor, inconsistent, optionalStorePhone, OrderRejected, trustedText, withinStoreHours } from "./orderValidation";
import { orderDocument, receiptFromDocument } from "./orderSnapshot";
import { createHash } from "node:crypto";

export type OrderResponse={status:number;body:Record<string,unknown>};
export const orderIdFor=(uid:string,id:string)=>createHash("sha256").update(JSON.stringify(["hu16-order-v1",uid,id])).digest("hex");
export const orderEventIdFor=(orderId:string)=>`new_order_${orderId}`;

/** Auth is sampled outside Firestore; the two systems do not share an atomic transaction. */
export class OrderService {
  private readonly access:CommercialAccessService;
  constructor(private readonly db:Firestore,private readonly auth:Auth,private readonly now:()=>Date=()=>new Date()) {
    this.access=new CommercialAccessService(db,auth);
  }
  async recover(uid:string,id:string):Promise<OrderResponse> {
    intentId(id);
    return this.db.runTransaction(async tx=>{
      const previous=await this.readAttempt(tx,uid,id);
      return previous ? {...previous,status:200} : {
        status:404,body:{contractVersion:1,intentId:id,outcome:"UNKNOWN",reason:"NOT_OBSERVED",error:{code:"ATTEMPT_NOT_OBSERVED",retryable:true}},
      };
    },{readOnly:true});
  }
  async confirm(intent:OrderIntent):Promise<OrderResponse> {
    const uid=intent.accepted.ownerUid,id=intent.intentId,hash=requestHash(intent);
    const known=await this.db.runTransaction(tx=>this.readAttempt(tx,uid,id,hash),{readOnly:true});
    if(known)return known;
    const user=await this.auth.getUser(uid); // Never in the retryable callback.
    if(user.disabled)throw new OrderHttpError("AUTHENTICATION_REQUIRED",401);
    const actor:CommercialIdentity | null=user.email?{uid,email:normalizedEmail(user.email),emailVerified:user.emailVerified,admin:user.customClaims?.admin===true}:null;
    return this.db.runTransaction(async tx=>{
      const previous=await this.readAttempt(tx,uid,id,hash);if(previous)return previous;
      const attempt=this.db.doc(`order_attempts/${uid}/intents/${id}`);
      try {
        if(!actor)throw new OrderHttpError("IDENTITY_UNAVAILABLE",503,true);
        if(!await this.access.canStartPurchase(tx,actor))throw new OrderRejected("PURCHASE_FORBIDDEN","PURCHASE_FORBIDDEN",403);
        const expected=intent.accepted;
        const store=(await tx.get(this.db.doc(`stores/${expected.storeId}`))).data();
        if(!store)throw new OrderRejected("STORE_UNAVAILABLE","STORE_UNAVAILABLE");
        const businessId=businessIdOf(store);
        const business=(await tx.get(this.db.doc(`business_requests/${businessId}`))).data();
        const eligibility=evaluateEligibility(store,business);
        if(eligibility!=="ELIGIBLE")throw new OrderRejected(eligibility==="STORE_UNAVAILABLE"?"STORE_UNAVAILABLE":"BUSINESS_UNAVAILABLE",eligibility);
        if(!["OPEN","PAUSED","CLOSED"].includes(store.operationalStatus))return inconsistent();
        if(store.operationalStatus!=="OPEN")throw new OrderRejected("STORE_UNAVAILABLE","STORE_NOT_OPERATIONAL");
        if(!validCoordinates({latitude:store.latitude,longitude:store.longitude}) || typeof store.deliveryRadiusKm!=="number" ||
          !Number.isFinite(store.deliveryRadiusKm)||store.deliveryRadiusKm<=0)return inconsistent();
        const address=(await tx.get(this.db.doc(`users/${uid}/addresses/${expected.delivery.addressId}`))).data();
        if(!address)throw new OrderRejected("REVIEW_REQUIRED","ADDRESS_NOT_FOUND");
        validateAddress(address);
        const origin={latitude:store.latitude,longitude:store.longitude},destination={latitude:address.location.lat,longitude:address.location.lng};
        if(haversineKm(origin,destination)>store.deliveryRadiusKm)throw new OrderRejected("OUTSIDE_COVERAGE","OUTSIDE_COVERAGE",422);
        const quoteData=(await tx.get(this.db.doc(`delivery_quotes/${expected.deliveryQuote.quoteId}`))).data();
        const profile=(await tx.get(this.db.doc(`users/${uid}`))).data();
        const items:OrderItem[]=[],categories=new Map<string,Record<string,any>>();
        for(const line of expected.items) {
          const product=(await tx.get(this.db.doc(`products/${line.productId}`))).data();
          if(!product || product.isActive===false)throw new OrderRejected("REVIEW_REQUIRED","PRODUCT_UNAVAILABLE");
          if(product.isActive!==true || typeof product.categoryId!=="string" || !product.categoryId || product.categoryId.includes("/"))return inconsistent();
          if(product.businessId!==businessId)throw new OrderRejected("REVIEW_REQUIRED","PRODUCT_UNAVAILABLE");
          let category=categories.get(product.categoryId);
          if(!category) {
            category=(await tx.get(this.db.doc(`categories/${product.categoryId}`))).data();
            if(!category)throw new OrderRejected("REVIEW_REQUIRED","CATEGORY_UNAVAILABLE");
            categories.set(product.categoryId,category);
          }
          if(category.businessId!==businessId || typeof category.isActive!=="boolean")return inconsistent();
          if(!category.isActive)throw new OrderRejected("REVIEW_REQUIRED","CATEGORY_UNAVAILABLE");
          const config=(await tx.get(this.db.doc(`store_product_configs/${expected.storeId}_${line.productId}`))).data();
          if(!config)throw new OrderRejected("REVIEW_REQUIRED","PRODUCT_UNAVAILABLE");
          if(config.businessId!==businessId || config.storeId!==expected.storeId || config.productId!==line.productId ||
            !(config.updatedAt instanceof Timestamp) || typeof config.updatedBy!=="string" || !config.updatedBy.trim() ||
            !["AVAILABLE","OUT_OF_STOCK"].includes(config.availability))return inconsistent();
          if(config.availability!=="AVAILABLE")throw new OrderRejected("REVIEW_REQUIRED","OUT_OF_STOCK");
          items.push({productId:line.productId,categoryId:product.categoryId,name:trustedText(product.name),quantity:line.quantity,unitPriceMinor:effectivePriceMinor(config.price)});
        }
        if(!await this.access.hasReceivingManager(tx,businessId,expected.storeId))throw new OrderRejected("NO_RESPONSIBLE","NO_RESPONSIBLE");
        // All required reads finished. Re-evaluate time on every retry, immediately before queuing writes.
        const decisionAt=this.now();
        if(!Number.isFinite(decisionAt.getTime()))return inconsistent();
        if(!withinStoreHours(store.operatingHours,decisionAt))throw new OrderRejected("STORE_UNAVAILABLE","OUTSIDE_OPERATING_HOURS");
        const qc:QuoteContext={uid,storeId:expected.storeId,addressId:expected.delivery.addressId,businessId,origin,destination,
          logisticsHash:logisticsContextHash(uid,expected.storeId,expected.delivery.addressId,origin,destination)};
        const quote=readStoredQuote(expected.deliveryQuote.quoteId,quoteData,qc,decisionAt);
        const actual:AcceptedPurchase=parseAcceptedPurchase({ownerUid:uid,businessId,storeId:expected.storeId,businessName:trustedText(business!.businessName),
          storeName:trustedText(store.name),storePhone:optionalStorePhone(store.phone),buyerName:trustedText(profile?.displayName || user.displayName),
          buyerPhone:user.phoneNumber??null,pickup:{addressText:trustedText(store.address),location:origin,reference:null,instructions:null},
          delivery:{addressId:expected.delivery.addressId,label:address.label,destination:{addressText:address.addressText,location:destination,
            reference:address.reference,instructions:address.instructions}},items,amounts:orderAmounts(items,quote.deliveryFeeMinor),
          currency:"PEN",paymentMethod:"CASH_ON_DELIVERY",deliveryQuote:{quoteId:quote.quoteId,distanceMeters:quote.distanceMeters,
            tariffVersion:quote.tariffVersion,quotedAtEpochMillis:Date.parse(quote.quotedAt),expiresAtEpochMillis:Date.parse(quote.expiresAt)}});
        if(consentHash(actual)!==consentHash(expected))throw new OrderRejected("REVIEW_REQUIRED","REVIEW_REQUIRED");
        const acceptedIntent:OrderIntent={intentId:id,accepted:actual};
        const orderId=orderIdFor(uid,id),orderRef=this.db.doc(`food_orders/${orderId}`),timestamp=Timestamp.fromDate(decisionAt);
        const document=orderDocument(acceptedIntent,orderId,decisionAt);
        tx.create(orderRef,document);
        tx.create(attempt,{schemaVersion:1,buyerUid:uid,requestHash:hash,outcome:"SUCCEEDED",orderId,createdAt:timestamp,completedAt:timestamp});
        tx.create(orderRef.collection("status_history").doc("initial"),{schemaVersion:1,fromStatus:null,toStatus:"PENDING_CONFIRMATION",actorUid:uid,occurredAt:timestamp});
        tx.create(this.db.doc(`order_events/${orderEventIdFor(orderId)}`),{schemaVersion:1,type:"NEW_ORDER",orderId,businessId,storeId:expected.storeId,
          status:"PENDING",attemptCount:0,nextAttemptAt:timestamp,createdAt:timestamp});
        return {status:201,body:{contractVersion:1,intentId:id,outcome:"SUCCEEDED",receipt:receiptFromDocument(document,uid,id,hash)}};
      } catch(error) {
        let rejection:OrderRejected|undefined;
        if(error instanceof OrderRejected)rejection=error;
        else if(error instanceof QuoteError && error.status<500)rejection=new OrderRejected(error.code==="QUOTE_EXPIRED"?"QUOTE_EXPIRED":
          error.code==="OUTSIDE_COVERAGE"?"OUTSIDE_COVERAGE":"REVIEW_REQUIRED",error.code,error.status===404?409:error.status);
        if(!rejection) {
          if(error instanceof CommercialAccessError)throw new OrderHttpError("ACCESS_FACTS_UNAVAILABLE",503,true);
          throw error;
        }
        tx.create(attempt,{schemaVersion:1,buyerUid:uid,requestHash:hash,outcome:"REJECTED_FINAL",reason:rejection.reason,
          code:rejection.code,httpStatus:rejection.status,createdAt:Timestamp.fromDate(this.now()),completedAt:Timestamp.fromDate(this.now())});
        return {status:rejection.status,body:{contractVersion:1,intentId:id,outcome:"REJECTED_FINAL",reason:rejection.reason,
          error:{code:rejection.code,retryable:false}}};
      }
    });
  }
  private async readAttempt(tx:Transaction,uid:string,id:string,hash?:string):Promise<OrderResponse|null> {
    const attempt=(await tx.get(this.db.doc(`order_attempts/${uid}/intents/${id}`))).data();
    if(!attempt)return null;
    if(attempt.schemaVersion!==1 || attempt.buyerUid!==uid || typeof attempt.requestHash!=="string" || !/^[0-9a-f]{64}$/.test(attempt.requestHash))return inconsistent();
    if(hash && attempt.requestHash!==hash)return {status:409,body:{contractVersion:1,intentId:id,error:{code:"INTENT_PAYLOAD_MISMATCH",retryable:false}}};
    if(attempt.outcome==="SUCCEEDED") {
      if(typeof attempt.orderId!=="string" || attempt.orderId!==orderIdFor(uid,id))return inconsistent();
      const order=(await tx.get(this.db.doc(`food_orders/${attempt.orderId}`))).data();
      const initial=(await tx.get(this.db.doc(`food_orders/${attempt.orderId}/status_history/initial`))).data();
      if(!initial || initial.schemaVersion!==1 || initial.fromStatus!==null || initial.toStatus!=="PENDING_CONFIRMATION" ||
        initial.actorUid!==uid || !(initial.occurredAt instanceof Timestamp) || !(order?.createdAt instanceof Timestamp) ||
        !initial.occurredAt.isEqual(order.createdAt))return inconsistent();
      const receipt=receiptFromDocument(order,uid,id,attempt.requestHash);
      recordedOrderAttempt({ownerUid:uid,intentId:id,requestHash:attempt.requestHash,result:{outcome:"SUCCEEDED",receipt}});
      return {status:200,body:{contractVersion:1,intentId:id,outcome:"SUCCEEDED",receipt}};
    }
    if(attempt.outcome!=="REJECTED_FINAL" || !Number.isInteger(attempt.httpStatus) || attempt.httpStatus<400 || attempt.httpStatus>=500 ||
      typeof attempt.code!=="string")return inconsistent();
    recordedOrderAttempt({ownerUid:uid,intentId:id,requestHash:attempt.requestHash,result:{outcome:"REJECTED_FINAL",reason:attempt.reason}});
    return {status:attempt.httpStatus,body:{contractVersion:1,intentId:id,outcome:"REJECTED_FINAL",reason:attempt.reason,error:{code:attempt.code,retryable:false}}};
  }
}
function validateAddress(a:Record<string,any>):void {
  if(Object.keys(a).sort().join(",")!=="addressText,createdAt,instructions,label,location,reference,updatedAt" ||
    typeof a.label!=="string" || a.label.length>40 || typeof a.addressText!=="string" || !a.addressText.trim() ||
    a.addressText!==a.addressText.trim() || a.addressText.length>250 || typeof a.reference!=="string" || a.reference.length>250 ||
    typeof a.instructions!=="string" || a.instructions.length>500 || !a.location ||
    !validCoordinates({latitude:a.location.lat,longitude:a.location.lng}) || !(a.createdAt instanceof Timestamp) ||
    !(a.updatedAt instanceof Timestamp) || a.updatedAt.toMillis()<a.createdAt.toMillis())return inconsistent();
}
