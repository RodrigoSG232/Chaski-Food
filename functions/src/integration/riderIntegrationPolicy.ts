import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { validCoordinates } from "../delivery/routeReader";
import { OrderHttpError } from "../orders/orderRequest";

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

export function payloadHash(value:unknown):string{return createHash("sha256").update(JSON.stringify(value)).digest("hex");}
export function riderOrderId(value:string):string|null{return /^[0-9a-f]{64}$/.test(value)?value:null;}

export function projectRiderOrder(data:Record<string,any>){
  if(data.schemaVersion!==1||!["READY_FOR_PICKUP","IN_DELIVERY","COMPLETED"].includes(data.status)||data.paymentStatus!=="PAID"||data.paymentSimulation!==true||
    typeof data.pickupCode!=="string"||!/^[0-9]{4}$/.test(data.pickupCode)||!(data.updatedAt instanceof Timestamp))
    throw new OrderHttpError("ORDER_NOT_READY",409);
  const pickup=data.pickupSnapshot,dropoff=data.dropoffSnapshot,buyer=data.buyerSnapshot,store=data.storeSnapshot;
  if(!pickup||!dropoff||!buyer||!store||!validCoordinates(pickup.location)||!validCoordinates(dropoff.location)||!Array.isArray(data.items))
    throw new OrderHttpError("DATA_INCONSISTENT",503,true);
  return {schemaVersion:1,orderId:data.orderId,updatedAt:data.updatedAt.toDate().toISOString(),status:data.status,
    customer:{id:data.buyerUid,displayName:buyer.displayName,phone:buyer.phone??null,address:dropoff.addressText,
      reference:dropoff.reference??null,instructions:dropoff.instructions??null,latitude:dropoff.location.latitude,longitude:dropoff.location.longitude},
    merchant:{id:data.storeId,name:store.name,phone:store.phone??null,address:pickup.addressText,
      instructions:pickup.instructions??null,latitude:pickup.location.latitude,longitude:pickup.location.longitude},
    items:data.items.map((item:Record<string,unknown>)=>({id:item.productId,name:item.name,quantity:item.quantity})),
    amounts:{subtotalMinor:data.subtotalMinor,deliveryFeeMinor:data.deliveryFeeMinor,tipMinor:data.tipMinor??0,totalMinor:data.totalMinor,currency:data.currency},
    payment:{flow:"PREPAID_SIMULATION",method:"SIMULATED",status:"PAID"},pickupCode:data.pickupCode,
    dispatch:data.riderIntegration??{status:"SEARCHING_RIDER",lastEventSequence:0,assignment:null,rider:null},
    tracking:data.riderTracking??null};
}
