import assert from "node:assert/strict";
import test from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { encodeRiderOrdersCursor,parseRiderEvent,parseRiderOrdersQuery,parseRiderTracking,projectRiderOrder,riderEventAllowedForPayment,riderPaymentEligibility } from "../src/integration/riderIntegrationPolicy";

const event={schemaVersion:1,eventId:"EV_1",eventType:"ASSIGNMENT_ACCEPTED",eventSequence:1,
  occurredAt:"2026-11-08T15:00:00.000Z",orderId:"a".repeat(64),assignment:{id:"ASG_1",version:1},status:"ACCEPTED",reason:null,
  rider:{id:"RIDER_1",displayName:"Ana",phone:"+51999999999",vehicleType:"MOTORCYCLE",vehiclePlate:"ABC-123"},deliveryOutcome:null};
test("accepts a strict assignment event and rejects forged combinations",()=>{
  assert.equal(parseRiderEvent(event).rider?.id,"RIDER_1");
  assert.throws(()=>parseRiderEvent({...event,status:"COMPLETED"}));
  assert.throws(()=>parseRiderEvent({...event,unexpected:true}));
  assert.throws(()=>parseRiderEvent({...event,rider:{...event.rider,displayName:null}}));
});
test("validates tracking coordinates and accuracy",()=>{
  const tracking={assignmentId:"ASG_1",assignmentVersion:1,sequence:2,riderId:"RIDER_1",latitude:-12.1,longitude:-77.1,
    accuracyMeters:12,recordedAt:"2026-11-08T15:01:00.000Z"};
  assert.equal(parseRiderTracking(tracking).sequence,2);
  assert.throws(()=>parseRiderTracking({...tracking,latitude:120}));
});
const legacyOrder:any={schemaVersion:1,orderId:"a".repeat(64),status:"READY_FOR_PICKUP",paymentMethod:"CASH_ON_DELIVERY",
  paymentStatus:"PAID",paymentSimulation:true,pickupCode:"0042",updatedAt:Timestamp.fromMillis(1_800_000_000_000),
  readyAt:Timestamp.fromMillis(1_799_999_000_000),buyerUid:"PRIVATE_BUYER_UID",storeId:"store",
  buyerSnapshot:{uid:"PRIVATE_BUYER_UID",displayName:"Cliente",phone:null},
  storeSnapshot:{name:"Local",phone:null},pickupSnapshot:{addressText:"Local 1",location:{latitude:-12.1,longitude:-77.1}},
  dropoffSnapshot:{addressText:"Casa",location:{latitude:-12.2,longitude:-77.2}},items:[{productId:"p",name:"Plato",quantity:2,unitPriceMinor:1000}],
  subtotalMinor:2000,deliveryFeeMinor:300,tipMinor:100,totalMinor:2400,currency:"PEN"};
const assigned={...legacyOrder,riderIntegration:{lastEventSequence:1,lastAssignmentVersion:1,status:"ACCEPTED",
  assignment:{id:"ASG_1",version:1},rider:{id:"RIDER_1",displayName:"Ana",phone:null,vehicleType:"BICYCLE",vehiclePlate:null},
  deliveryOutcome:null,reason:null,updatedAt:Timestamp.fromMillis(1_800_000_001_000)}};
test("E1.1: list projection never carries the pickup code nor the buyer UID",()=>{
  const listed:any=projectRiderOrder(assigned,"LIST");
  assert.equal("pickupCode" in listed,false);assert.equal("id" in listed.customer,false);
  assert.deepEqual(Object.keys(listed).sort(),["amounts","customer","dispatch","items","merchant","orderId","payment","readyAt",
    "schemaVersion","status","tracking","updatedAt"]);
  assert.deepEqual(listed.items,[{id:"p",name:"Plato",quantity:2}]);
  assert.equal(JSON.stringify(listed).includes("PRIVATE_BUYER_UID"),false);
});
test("E1.1: detail exposes the code only to a current assignment in READY_FOR_PICKUP",()=>{
  assert.equal((projectRiderOrder(legacyOrder,"DETAIL") as any).pickupCode,null);
  assert.equal((projectRiderOrder(assigned,"DETAIL") as any).pickupCode,"0042");
  const released={...assigned,riderIntegration:{...assigned.riderIntegration,status:"SEARCHING_RIDER",assignment:null,rider:null}};
  assert.equal((projectRiderOrder(released,"DETAIL") as any).pickupCode,null);
  assert.equal((projectRiderOrder({...assigned,status:"IN_DELIVERY"},"DETAIL") as any).pickupCode,null);
  assert.equal((projectRiderOrder({...assigned,status:"COMPLETED"},"DETAIL") as any).pickupCode,null);
});
test("E1.1: legacy simulated prepayment owes nothing and keeps its persisted method",()=>{
  assert.deepEqual(projectRiderOrder(legacyOrder,"DETAIL").payment,
    {method:"CASH_ON_DELIVERY",status:"PAID",amountToCollectMinor:0,currency:"PEN"});
  assert.throws(()=>projectRiderOrder({...legacyOrder,paymentStatus:"PENDING"},"LIST"),/ORDER_NOT_READY/);
});
test("E1.1: dispatch and tracking are explicit, ISO dated and free of internal fields",()=>{
  const delivered:any=projectRiderOrder({...assigned,status:"IN_DELIVERY",riderTracking:{assignmentId:"ASG_1",assignmentVersion:1,
    sequence:3,riderId:"RIDER_1",latitude:-12.15,longitude:-77.15,accuracyMeters:10,recordedAt:Timestamp.fromMillis(1_800_000_002_000),
    estimatedArrivalAt:null,payloadHash:"HASH",receivedAt:Timestamp.fromMillis(1_800_000_002_100)}},"DETAIL");
  assert.equal(delivered.readyAt,"2027-01-15T07:43:20.000Z");
  assert.equal(delivered.dispatch.updatedAt,"2027-01-15T08:00:01.000Z");
  assert.deepEqual(delivered.tracking,{assignmentId:"ASG_1",assignmentVersion:1,sequence:3,latitude:-12.15,longitude:-77.15,
    accuracyMeters:10,recordedAt:"2027-01-15T08:00:02.000Z",estimatedArrivalAt:null});
  const json=JSON.stringify(delivered);
  for(const secret of ["_seconds","payloadHash","receivedAt","PRIVATE_BUYER_UID","paymentSimulation"])assert.equal(json.includes(secret),false,secret);
  const fresh:any=projectRiderOrder({...legacyOrder,readyAt:undefined},"DETAIL");
  assert.equal(fresh.readyAt,null);assert.equal(fresh.tracking,null);
  assert.deepEqual(fresh.dispatch,{status:"SEARCHING_RIDER",lastEventSequence:0,lastAssignmentVersion:0,assignment:null,rider:null,
    deliveryOutcome:null,reason:null,updatedAt:null});
});

const cashOrder:any={...legacyOrder,paymentStatus:"PENDING",paymentSimulation:undefined};
test("E1.2: cash pending and legacy prepaid are the only eligible payment situations",()=>{
  assert.equal(riderPaymentEligibility(cashOrder),"CASH_PENDING");
  assert.equal(riderPaymentEligibility(legacyOrder),"LEGACY_PREPAID");
  for(const patch of [{paymentStatus:"PAID",paymentSimulation:undefined},{paymentMethod:"CARD"},{paymentStatus:"CANCELLED"},
    {pickupCode:null},{pickupCode:"12a4"},{paymentSimulation:true}])
    assert.equal(riderPaymentEligibility({...cashOrder,...patch}),null,JSON.stringify(patch));
  assert.deepEqual(projectRiderOrder(cashOrder,"LIST").payment,{method:"CASH_ON_DELIVERY",status:"PENDING",amountToCollectMinor:2400,currency:"PEN"});
  assert.throws(()=>projectRiderOrder({...cashOrder,paymentMethod:"CARD"},"DETAIL"),/ORDER_NOT_READY/);
});
test("E2: cash orders accept the verified pickup flow but not completion (E4); legacy prepaid keeps every event",()=>{
  for(const type of ["ASSIGNMENT_ACCEPTED","ARRIVED_AT_MERCHANT","ASSIGNMENT_RELEASED","PICKED_UP","ARRIVED_AT_CUSTOMER"] as const)
    assert.equal(riderEventAllowedForPayment("CASH_PENDING",type),true,type);
  for(const type of ["COMPLETED_SUCCESSFULLY","COMPLETED_UNSUCCESSFULLY"] as const) {
    assert.equal(riderEventAllowedForPayment("CASH_PENDING",type),false,type);
    assert.equal(riderEventAllowedForPayment("LEGACY_PREPAID",type),true,type);
  }
});

test("E1.3: the cursor keeps exact seconds, nanoseconds and id and rejects anything else",()=>{
  const cursor={seconds:1_800_000_000,nanoseconds:123_456_789,orderId:"b".repeat(64)};
  const encoded=encodeRiderOrdersCursor(cursor);
  assert.deepEqual(parseRiderOrdersQuery({cursor:encoded,limit:"50"}),{limit:50,cursor});
  assert.deepEqual(parseRiderOrdersQuery({}),{limit:20,cursor:null});
  const raw=(value:unknown)=>Buffer.from(JSON.stringify(value)).toString("base64url");
  const bad:unknown[]=["%%%","",raw({v:2,s:1,n:0,id:"b".repeat(64)}),raw({v:1,s:1,n:1_000_000_000,id:"b".repeat(64)}),
    raw({v:1,s:-1,n:0,id:"b".repeat(64)}),raw({v:1,s:1.5,n:0,id:"b".repeat(64)}),raw({v:1,s:1,n:0,id:"B".repeat(64)}),
    raw({v:1,s:1,n:0,id:"b".repeat(64),x:1}),raw({id:"b".repeat(64),n:0,s:1,v:1}),raw([1,0]),["a","b"],"x".repeat(300)];
  for(const cursorValue of bad)assert.throws(()=>parseRiderOrdersQuery({cursor:cursorValue}),/INVALID_REQUEST/,JSON.stringify(cursorValue));
  for(const limit of ["0","51","05","1.5","-1",["1","2"]])assert.throws(()=>parseRiderOrdersQuery({limit}),/INVALID_REQUEST/,String(limit));
  assert.throws(()=>parseRiderOrdersQuery({status:"READY"}),/INVALID_REQUEST/);
});
