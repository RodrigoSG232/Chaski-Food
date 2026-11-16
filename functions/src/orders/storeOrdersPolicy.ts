import { Timestamp } from "firebase-admin/firestore";
import { MAX_PICKUP_FAILURES, readPickupSecurity, verificationIsCurrent } from "./pickupSecurityPolicy";
import { MAX_ORDER_EPOCH_MILLIS, orderId, orderMinor, orderText } from "./orderModels";
import { OrderHttpError } from "./orderRequest";
import { validId, digest } from "../access/commercialAccessPolicy";

export const DEFAULT_INBOX_PAGE_SIZE = 20;
export const MAX_INBOX_PAGE_SIZE = 50; // Transport bound, never a business/order limit.
export type StoreOrderStatus = "PENDING_CONFIRMATION"|"PREPARING"|"READY_FOR_PICKUP"|"IN_DELIVERY";
export type InboxCursor = Readonly<{v:1;subject:string;storeId:string;status:StoreOrderStatus;anchorId:string}>;
export function encodeInboxCursor(uid:string,storeId:string,anchorId:string,status:StoreOrderStatus="PENDING_CONFIRMATION"):string {
  return Buffer.from(JSON.stringify({v:1,subject:digest(["inbox-cursor-v1",uid]),storeId,status,anchorId})).toString("base64url");
}
export function parseInboxQuery(query:Record<string,unknown>,uid:string,storeId:string):{pageSize:number;cursor:InboxCursor|null;status:StoreOrderStatus} {
  if(!validId(storeId)||Object.keys(query).some(k=>!["pageSize","cursor","status"].includes(k)))throw new OrderHttpError("INVALID_REQUEST",400);
  const status=query.status??"PENDING_CONFIRMATION";
  if(status!=="PENDING_CONFIRMATION"&&status!=="PREPARING"&&status!=="READY_FOR_PICKUP"&&status!=="IN_DELIVERY")throw new OrderHttpError("INVALID_REQUEST",400);
  const size=query.pageSize;
  if(size!==undefined && (typeof size!=="string"||!/^[1-9][0-9]?$/.test(size)||Number(size)>MAX_INBOX_PAGE_SIZE))
    throw new OrderHttpError("INVALID_REQUEST",400);
  let cursor:InboxCursor|null=null;
  if(query.cursor!==undefined) {
    try {
      if(typeof query.cursor!=="string"||query.cursor.length>1024||!/^[A-Za-z0-9_-]+$/.test(query.cursor))throw new Error();
      const raw=JSON.parse(Buffer.from(query.cursor,"base64url").toString("utf8"));
      if(!raw||Array.isArray(raw)||Object.keys(raw).sort().join(",")!=="anchorId,status,storeId,subject,v"||raw.v!==1||raw.subject!==digest(["inbox-cursor-v1",uid])||
        raw.storeId!==storeId||raw.status!==status||typeof raw.anchorId!=="string"||!/^[0-9a-f]{64}$/.test(raw.anchorId)||
        encodeInboxCursor(uid,storeId,raw.anchorId,status)!==query.cursor)throw new Error();
      cursor=Object.freeze(raw);
    }catch{throw new OrderHttpError("INVALID_CURSOR",400);}
  }
  return {pageSize:size===undefined?DEFAULT_INBOX_PAGE_SIZE:Number(size),cursor,status};
}
/** Shared order-document validation (HU16 inbox and HU17 buyer reads). Throws on any inconsistency; callers map it to 503. */
export function validatedOrderCore(id:string,data:Record<string,any>) {
  if(!/^[0-9a-f]{64}$/.test(id)||data.orderId!==id||data.schemaVersion!==1||
    !(data.createdAt instanceof Timestamp)||data.currency!=="PEN"||data.paymentMethod!=="CASH_ON_DELIVERY"||
    !Array.isArray(data.items)||data.items.length===0)throw new Error();
  const createdAtEpochMillis=Math.floor(data.createdAt.toMillis());
  if(createdAtEpochMillis<1||createdAtEpochMillis>MAX_ORDER_EPOCH_MILLIS)throw new Error();
  const seen=new Set<string>();let subtotal=0n;
  const items=data.items.map((item:Record<string,unknown>)=>{
    const productId=orderId(item.productId),name=orderText(item.name),quantity=item.quantity;
    if(seen.has(productId)||typeof quantity!=="number"||!Number.isInteger(quantity)||quantity<=0||quantity>2147483647)throw new Error();
    seen.add(productId);
    const unitPriceMinor=orderMinor(item.unitPriceMinor),lineTotalMinor=orderMinor(item.lineTotalMinor);
    if(unitPriceMinor===0||BigInt(quantity)*BigInt(unitPriceMinor)!==BigInt(lineTotalMinor))throw new Error();
    subtotal+=BigInt(lineTotalMinor);
    return {productId,name,quantity,unitPriceMinor,lineTotalMinor};
  });
  const subtotalMinor=orderMinor(data.subtotalMinor),deliveryFeeMinor=orderMinor(data.deliveryFeeMinor),tipMinor=orderMinor(data.tipMinor??0),totalMinor=orderMinor(data.totalMinor);
  if(subtotal!==BigInt(subtotalMinor)||subtotal+BigInt(deliveryFeeMinor)+BigInt(tipMinor)!==BigInt(totalMinor))throw new Error();
  return {createdAtEpochMillis,items,subtotalMinor,deliveryFeeMinor,tipMinor,totalMinor,currency:"PEN" as const,paymentMethod:"CASH_ON_DELIVERY" as const};
}
/** Explicit whitelist: no contacts, addresses, buyer identity, attempts or event state. */
export function projectPendingOrder(id:string,data:Record<string,any>) {
  try {
    if(data.status!=="PENDING_CONFIRMATION")throw new Error();
    const core=validatedOrderCore(id,data);
    return {orderId:id,createdAtEpochMillis:core.createdAtEpochMillis,status:"PENDING_CONFIRMATION" as const,items:core.items,
      subtotalMinor:core.subtotalMinor,deliveryFeeMinor:core.deliveryFeeMinor,tipMinor:core.tipMinor,totalMinor:core.totalMinor,
      currency:core.currency,paymentMethod:core.paymentMethod};
  }catch{throw new OrderHttpError("DATA_INCONSISTENT",503,true);}
}
/** HU20 counters: absent on orders that were never extended (or predate HU20) and read as 0; malformed values fail closed. */
export function optionalCounter(value:unknown):number {
  if(value===undefined||value===null)return 0;
  if(!Number.isSafeInteger(value)||(value as number)<0)throw new Error();
  return value as number;
}
/**
 * E2 (additive, for E3): what the store needs to verify the rider — current assignment, rider name and plate, and the
 * pickup security state. Never the rider id or phone, and never the pickup code (E5.2).
 */
function storePickupView(data:Record<string,any>){
  const security=readPickupSecurity(data.pickupSecurity),assignment=data.riderIntegration?.assignment,rider=data.riderIntegration?.rider;
  return {assignment:assignment?{assignmentId:assignment.id,assignmentVersion:assignment.version,
      riderDisplayName:rider?.displayName??null,vehicleType:rider?.vehicleType??null,vehiclePlate:rider?.vehiclePlate??null}:null,
    verified:verificationIsCurrent(security,assignment),locked:!!security.lockedAt,
    attemptsRemaining:security.lockedAt?0:MAX_PICKUP_FAILURES-security.failures};
}

function storePayment(data:Record<string,any>,totalMinor:number){
  // Older orders may predate paymentStatus: nothing is projected (the app keeps its neutral text). Unknown values are corrupt.
  if(data.paymentStatus===undefined)return {};
  if(data.paymentStatus!=="PENDING"&&data.paymentStatus!=="PAID")throw new Error();
  return {paymentStatus:data.paymentStatus as "PENDING"|"PAID",amountToCollectMinor:data.paymentStatus==="PENDING"?totalMinor:0};
}

export function projectStoreOrder(id:string,data:Record<string,any>,status:StoreOrderStatus) {
  if(status==="PENDING_CONFIRMATION")return projectPendingOrder(id,data);
  try {
    if(data.status!==status)throw new Error();
    if(status!=="IN_DELIVERY" && (!(data.estimatedReadyAt instanceof Timestamp)||
      !Number.isInteger(data.estimatedPreparationMinutes)||data.estimatedPreparationMinutes<5||data.estimatedPreparationMinutes>180))throw new Error();
    const core=validatedOrderCore(id,data);
    const ready=status==="READY_FOR_PICKUP";
    if(ready&&(!(data.readyAt instanceof Timestamp)||typeof data.pickupCode!=="string"||!/^\d{4}$/.test(data.pickupCode)))throw new Error();
    const estimateRevision=status!=="IN_DELIVERY" ? optionalCounter(data.estimateRevision) : 0;
    const preparationExtendedMinutes=status!=="IN_DELIVERY" ? optionalCounter(data.preparationExtendedMinutes) : 0;
    const simulationStage=status==="IN_DELIVERY" ? data.fulfillmentSimulation?.stage ?? null : null;
    if(simulationStage!==null && !["ON_THE_WAY","AT_CUSTOMER"].includes(simulationStage))throw new Error();
    return {orderId:id,createdAtEpochMillis:core.createdAtEpochMillis,status,items:core.items,
      subtotalMinor:core.subtotalMinor,deliveryFeeMinor:core.deliveryFeeMinor,tipMinor:core.tipMinor,totalMinor:core.totalMinor,currency:core.currency,
      paymentMethod:core.paymentMethod,
      // E4 (additive): what the rider must still collect; a PAID order (legacy prepaid or collected) owes nothing.
      ...storePayment(data,core.totalMinor),
      ...(status!=="IN_DELIVERY"?{estimatedPreparationMinutes:data.estimatedPreparationMinutes,
        estimatedReadyAtEpochMillis:data.estimatedReadyAt.toMillis(),estimateRevision,preparationExtendedMinutes}:{}),
      ...(status==="IN_DELIVERY"?{simulationStage}:{}),
      // E5.2: the pickup code is never sent to the store; the rider dictates it (verification endpoint).
      ...(ready?{readyAtEpochMillis:data.readyAt.toMillis(),pickup:storePickupView(data)}:{})};
  }catch{throw new OrderHttpError("DATA_INCONSISTENT",503,true);}
}
