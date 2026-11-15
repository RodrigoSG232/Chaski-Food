import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { validCoordinates } from "../delivery/routeReader";
import { OrderHttpError } from "../orders/orderRequest";
import { readPickupSecurity, verificationIsCurrent } from "../orders/pickupSecurityPolicy";

const idPattern=/^[A-Za-z0-9_-]{1,100}$/;
const eventTypes=["ASSIGNMENT_ACCEPTED","ARRIVED_AT_MERCHANT","PICKED_UP","ARRIVED_AT_CUSTOMER",
  "COMPLETED_SUCCESSFULLY","COMPLETED_UNSUCCESSFULLY","ASSIGNMENT_RELEASED"] as const;
export type RiderEventType=typeof eventTypes[number];

function object(value:unknown):Record<string,unknown>{
  if(!value||typeof value!=="object"||Array.isArray(value))throw new OrderHttpError("INVALID_REQUEST",422);
  return value as Record<string,unknown>;
}
function exact(value:Record<string,unknown>,fields:string[]){
  if(Object.keys(value).sort().join(",")!==fields.sort().join(","))throw new OrderHttpError("INVALID_REQUEST",422);
}
function id(value:unknown):string{
  if(typeof value!=="string"||!idPattern.test(value))throw new OrderHttpError("INVALID_REQUEST",422);return value;
}
function integer(value:unknown,min=1):number{
  if(!Number.isSafeInteger(value)||Number(value)<min)throw new OrderHttpError("INVALID_REQUEST",422);return Number(value);
}
function instant(value:unknown):Timestamp{
  if(typeof value!=="string")throw new OrderHttpError("INVALID_REQUEST",422);
  const millis=Date.parse(value);if(!Number.isFinite(millis)||new Date(millis).toISOString()!==value)
    throw new OrderHttpError("INVALID_REQUEST",422);
  return Timestamp.fromMillis(millis);
}
function optionalText(value:unknown,max:number):string|null{
  if(value===null)return null;if(typeof value!=="string"||!value.trim()||value!==value.trim()||value.length>max)
    throw new OrderHttpError("INVALID_REQUEST",422);return value;
}

export type RiderEvent={eventId:string;eventType:RiderEventType;eventSequence:number;occurredAt:Timestamp;orderId:string;
  assignmentId:string;assignmentVersion:number;status:string;reason:string|null;rider:null|{id:string;displayName:string;phone:string|null;
  vehicleType:"MOTORCYCLE"|"BICYCLE";vehiclePlate:string|null};deliveryOutcome:null|"SUCCESS"|"UNSUCCESSFUL"};

export function parseRiderEvent(value:unknown):RiderEvent{
  const body=object(value);exact(body,["schemaVersion","eventId","eventType","eventSequence","occurredAt","orderId","assignment",
    "status","reason","rider","deliveryOutcome"]);
  if(body.schemaVersion!==1||typeof body.eventType!=="string"||!eventTypes.includes(body.eventType as RiderEventType))
    throw new OrderHttpError("INVALID_REQUEST",422);
  const assignment=object(body.assignment);exact(assignment,["id","version"]);
  let rider:RiderEvent["rider"]=null;
  if(body.rider!==null){const r=object(body.rider);exact(r,["id","displayName","phone","vehicleType","vehiclePlate"]);
    if(typeof r.displayName!=="string")throw new OrderHttpError("INVALID_REQUEST",422);
    if(r.vehicleType!=="MOTORCYCLE"&&r.vehicleType!=="BICYCLE")throw new OrderHttpError("INVALID_REQUEST",422);
    rider={id:id(r.id),displayName:optionalText(r.displayName,120)!,phone:r.phone===null?null:optionalText(r.phone,30),
      vehicleType:r.vehicleType,vehiclePlate:r.vehiclePlate===null?null:optionalText(r.vehiclePlate,20)};}
  const eventType=body.eventType as RiderEventType;
  if(eventType==="ASSIGNMENT_ACCEPTED"&&!rider||eventType!=="ASSIGNMENT_ACCEPTED"&&rider!==null)
    throw new OrderHttpError("INVALID_REQUEST",422);
  const expectedStatus:Record<RiderEventType,string>={ASSIGNMENT_ACCEPTED:"ACCEPTED",ARRIVED_AT_MERCHANT:"AT_MERCHANT",
    PICKED_UP:"ON_THE_WAY",ARRIVED_AT_CUSTOMER:"AT_CUSTOMER",COMPLETED_SUCCESSFULLY:"COMPLETED",
    COMPLETED_UNSUCCESSFULLY:"COMPLETED",ASSIGNMENT_RELEASED:"SEARCHING_RIDER"};
  if(body.status!==expectedStatus[eventType])throw new OrderHttpError("INVALID_REQUEST",422);
  const outcome=body.deliveryOutcome;
  if((eventType==="COMPLETED_SUCCESSFULLY"&&outcome!=="SUCCESS")||
    (eventType==="COMPLETED_UNSUCCESSFULLY"&&outcome!=="UNSUCCESSFUL")||
    (!eventType.startsWith("COMPLETED_")&&outcome!==null))throw new OrderHttpError("INVALID_REQUEST",422);
  return {eventId:id(body.eventId),eventType,eventSequence:integer(body.eventSequence),occurredAt:instant(body.occurredAt),
    orderId:id(body.orderId),assignmentId:id(assignment.id),assignmentVersion:integer(assignment.version),status:body.status as string,
    reason:body.reason===null?null:optionalText(body.reason,120),rider,deliveryOutcome:outcome as RiderEvent["deliveryOutcome"]};
}

export type RiderTracking={assignmentId:string;assignmentVersion:number;sequence:number;riderId:string;latitude:number;longitude:number;
  accuracyMeters:number;recordedAt:Timestamp;estimatedArrivalAt:Timestamp|null};
export function parseRiderTracking(value:unknown):RiderTracking{
  const body=object(value);exact(body,["assignmentId","assignmentVersion","sequence","riderId","latitude","longitude","accuracyMeters","recordedAt",
    ...("estimatedArrivalAt" in body ? ["estimatedArrivalAt"] : [])]);
  const point={latitude:typeof body.latitude==="number"?body.latitude:NaN,
    longitude:typeof body.longitude==="number"?body.longitude:NaN};
  if(!validCoordinates(point)||typeof body.accuracyMeters!=="number"||!Number.isFinite(body.accuracyMeters)||body.accuracyMeters<0||body.accuracyMeters>500)
    throw new OrderHttpError("INVALID_REQUEST",422);
  const recordedAt=instant(body.recordedAt);
  const estimatedArrivalAt=body.estimatedArrivalAt==null?null:instant(body.estimatedArrivalAt);
  if(recordedAt.toMillis()<=0 || (estimatedArrivalAt && (estimatedArrivalAt.toMillis()<recordedAt.toMillis() ||
      estimatedArrivalAt.toMillis()-recordedAt.toMillis()>24*60*60*1000)))throw new OrderHttpError("INVALID_REQUEST",422);
  return {assignmentId:id(body.assignmentId),assignmentVersion:integer(body.assignmentVersion),sequence:integer(body.sequence),riderId:id(body.riderId),
    latitude:point.latitude,longitude:point.longitude,accuracyMeters:body.accuracyMeters,recordedAt,estimatedArrivalAt};
}

/** E1.3 listing limits: page size requested by Rider, Firestore batch size and documents examined per request. */
export const RIDER_PAGE_MAX=50,RIDER_SCAN_BATCH=50,RIDER_SCAN_MAX=200;
/** Exact Firestore ordering values of the LAST EXAMINED document (readyAt seconds + nanoseconds, then document id). */
export type RiderOrdersCursor=Readonly<{seconds:number;nanoseconds:number;orderId:string}>;
const maxTimestampSeconds=253_402_300_799;

export function encodeRiderOrdersCursor(cursor:RiderOrdersCursor):string{
  return Buffer.from(JSON.stringify({v:1,s:cursor.seconds,n:cursor.nanoseconds,id:cursor.orderId})).toString("base64url");
}

/** Strict parser: any malformed, tampered or unknown-version cursor is a client error, never a silent restart. */
export function parseRiderOrdersQuery(query:Record<string,unknown>):{limit:number;cursor:RiderOrdersCursor|null}{
  if(Object.keys(query).some(key=>key!=="limit"&&key!=="cursor"))throw new OrderHttpError("INVALID_REQUEST",400);
  const single=(value:unknown)=>Array.isArray(value)?null:value;
  const rawLimit=single(query.limit),rawCursor=single(query.cursor);
  if(rawLimit===null||rawCursor===null)throw new OrderHttpError("INVALID_REQUEST",400);
  let limit=20;
  if(rawLimit!==undefined){
    if(typeof rawLimit!=="string"||!/^[1-9][0-9]?$/.test(rawLimit)||Number(rawLimit)>RIDER_PAGE_MAX)throw new OrderHttpError("INVALID_REQUEST",400);
    limit=Number(rawLimit);
  }
  if(rawCursor===undefined)return {limit,cursor:null};
  try{
    if(typeof rawCursor!=="string"||rawCursor.length>256||!/^[A-Za-z0-9_-]+$/.test(rawCursor))throw new Error();
    const raw=JSON.parse(Buffer.from(rawCursor,"base64url").toString("utf8"));
    if(!raw||typeof raw!=="object"||Array.isArray(raw)||Object.keys(raw).sort().join(",")!=="id,n,s,v"||raw.v!==1||
      !Number.isSafeInteger(raw.s)||raw.s<0||raw.s>maxTimestampSeconds||!Number.isSafeInteger(raw.n)||raw.n<0||raw.n>999_999_999||
      typeof raw.id!=="string"||!/^[0-9a-f]{64}$/.test(raw.id))throw new Error();
    const cursor=Object.freeze({seconds:raw.s,nanoseconds:raw.n,orderId:raw.id});
    if(encodeRiderOrdersCursor(cursor)!==rawCursor)throw new Error();
    return {limit,cursor};
  }catch{throw new OrderHttpError("INVALID_REQUEST",400);}
}

export function payloadHash(value:unknown):string{return createHash("sha256").update(JSON.stringify(value)).digest("hex");}
export function riderOrderId(value:string):string|null{return /^[0-9a-f]{64}$/.test(value)?value:null;}

/** LIST = public availability (never carries the pickup code); DETAIL = reconciliation of one known order. */
export type RiderOrderView="LIST"|"DETAIL";

function isoOrNull(value:unknown):string|null{return value instanceof Timestamp?value.toDate().toISOString():null;}

/** Payment as seen by the delivery partner: what the rider must collect, never internal simulation flags. */
function riderPayment(data:Record<string,any>){
  const pending=data.paymentStatus==="PENDING";
  return {method:data.paymentMethod,status:data.paymentStatus,amountToCollectMinor:pending?data.totalMinor:0,currency:data.currency};
}

/** Explicit dispatch contract; the stored document may carry more internal fields. */
function riderDispatch(stored:Record<string,any>|undefined){
  const assignment=stored?.assignment;
  return {status:stored?.status??"SEARCHING_RIDER",lastEventSequence:stored?.lastEventSequence??0,
    lastAssignmentVersion:stored?.lastAssignmentVersion??0,
    assignment:assignment?{id:assignment.id,version:assignment.version}:null,
    rider:stored?.rider?{id:stored.rider.id,displayName:stored.rider.displayName,phone:stored.rider.phone??null,
      vehicleType:stored.rider.vehicleType,vehiclePlate:stored.rider.vehiclePlate??null}:null,
    deliveryOutcome:stored?.deliveryOutcome??null,reason:stored?.reason??null,updatedAt:isoOrNull(stored?.updatedAt)};
}

/** Last accepted sample without idempotency internals (payloadHash, receivedAt) or the rider identifier. */
function riderTrackingView(stored:Record<string,any>|undefined){
  if(!stored)return null;
  return {assignmentId:stored.assignmentId,assignmentVersion:stored.assignmentVersion,sequence:stored.sequence,
    latitude:stored.latitude,longitude:stored.longitude,accuracyMeters:stored.accuracyMeters,
    recordedAt:isoOrNull(stored.recordedAt),estimatedArrivalAt:isoOrNull(stored.estimatedArrivalAt)};
}

/** Payment situations Rider may handle: legacy simulated prepayment (nothing to collect) or cash still to be collected. */
export type RiderPaymentEligibility="LEGACY_PREPAID"|"CASH_PENDING";
export function riderPaymentEligibility(data:Record<string,any>):RiderPaymentEligibility|null{
  if(typeof data.pickupCode!=="string"||!/^[0-9]{4}$/.test(data.pickupCode))return null;
  if(data.paymentStatus==="PAID"&&data.paymentSimulation===true)return "LEGACY_PREPAID";
  if(data.paymentMethod==="CASH_ON_DELIVERY"&&data.paymentStatus==="PENDING"&&data.paymentSimulation!==true)return "CASH_PENDING";
  return null;
}

/** E2: cash orders follow the verified pickup flow; completion stays blocked until the collection rules of E4. */
const cashEventsEnabled:readonly RiderEventType[]=["ASSIGNMENT_ACCEPTED","ARRIVED_AT_MERCHANT","ASSIGNMENT_RELEASED","PICKED_UP","ARRIVED_AT_CUSTOMER"];
export function riderEventAllowedForPayment(eligibility:RiderPaymentEligibility,eventType:RiderEventType):boolean{
  return eligibility==="LEGACY_PREPAID"||cashEventsEnabled.includes(eventType);
}

export function projectRiderOrder(data:Record<string,any>,view:RiderOrderView){
  if(data.schemaVersion!==1||!["READY_FOR_PICKUP","IN_DELIVERY","COMPLETED"].includes(data.status)||
    riderPaymentEligibility(data)===null||!(data.updatedAt instanceof Timestamp))
    throw new OrderHttpError("ORDER_NOT_READY",409);
  const pickup=data.pickupSnapshot,dropoff=data.dropoffSnapshot,buyer=data.buyerSnapshot,store=data.storeSnapshot;
  if(!pickup||!dropoff||!buyer||!store||!validCoordinates(pickup.location)||!validCoordinates(dropoff.location)||!Array.isArray(data.items))
    throw new OrderHttpError("DATA_INCONSISTENT",503,true);
  const dispatch=riderDispatch(data.riderIntegration);
  const order={schemaVersion:1,orderId:data.orderId,updatedAt:data.updatedAt.toDate().toISOString(),status:data.status,
    readyAt:isoOrNull(data.readyAt),
    customer:{displayName:buyer.displayName,phone:buyer.phone??null,address:dropoff.addressText,
      reference:dropoff.reference??null,instructions:dropoff.instructions??null,latitude:dropoff.location.latitude,longitude:dropoff.location.longitude},
    merchant:{id:data.storeId,name:store.name,phone:store.phone??null,address:pickup.addressText,
      instructions:pickup.instructions??null,latitude:pickup.location.latitude,longitude:pickup.location.longitude},
    items:data.items.map((item:Record<string,unknown>)=>({id:item.productId,name:item.name,quantity:item.quantity})),
    amounts:{subtotalMinor:data.subtotalMinor,deliveryFeeMinor:data.deliveryFeeMinor,tipMinor:data.tipMinor??0,totalMinor:data.totalMinor,currency:data.currency},
    payment:riderPayment(data),dispatch,tracking:riderTrackingView(data.riderTracking)};
  if(view==="LIST")return order;
  const security=readPickupSecurity(data.pickupSecurity),current=verificationIsCurrent(security,dispatch.assignment);
  // Only the backend of the current assignment receives the code, and only while it is still useful (not verified, not locked).
  // Rider must still restrict it to that rider.
  const pickupCode=data.status==="READY_FOR_PICKUP"&&dispatch.assignment!==null&&!security.lockedAt&&!current?data.pickupCode:null;
  return {...order,pickupCode,
    pickupVerification:current?{assignmentId:security.verification!.assignmentId,assignmentVersion:security.verification!.assignmentVersion,
      verifiedAt:security.verification!.verifiedAt.toDate().toISOString()}:null,
    pickupLock:security.lockedAt?{lockedAt:security.lockedAt.toDate().toISOString(),reason:"MAX_ATTEMPTS"}:null};
}
