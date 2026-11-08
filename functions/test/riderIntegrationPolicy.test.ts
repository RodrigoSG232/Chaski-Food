import assert from "node:assert/strict";
import test from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { parseRiderEvent,parseRiderTracking,projectRiderOrder } from "../src/integration/riderIntegrationPolicy";

const event={schemaVersion:1,eventId:"EV_1",eventType:"ASSIGNMENT_ACCEPTED",eventSequence:1,
  occurredAt:"2026-11-08T15:00:00.000Z",orderId:"a".repeat(64),assignment:{id:"ASG_1",version:1},status:"ACCEPTED",reason:null,
  rider:{id:"RIDER_1",displayName:"Ana",phone:"+51999999999",vehicleType:"MOTORCYCLE",vehiclePlate:"ABC-123"},deliveryOutcome:null};
test("accepts a strict assignment event and rejects forged combinations",()=>{
  assert.equal(parseRiderEvent(event).rider?.id,"RIDER_1");
  assert.throws(()=>parseRiderEvent({...event,status:"COMPLETED"}));
  assert.throws(()=>parseRiderEvent({...event,unexpected:true}));
});
test("validates tracking coordinates and accuracy",()=>{
  const tracking={assignmentId:"ASG_1",assignmentVersion:1,sequence:2,riderId:"RIDER_1",latitude:-12.1,longitude:-77.1,
    accuracyMeters:12,recordedAt:"2026-11-08T15:01:00.000Z"};
  assert.equal(parseRiderTracking(tracking).sequence,2);
  assert.throws(()=>parseRiderTracking({...tracking,latitude:120}));
});
test("projects only paid ready orders and includes the stable code without unit prices",()=>{
  const order:any={schemaVersion:1,orderId:"a".repeat(64),status:"READY_FOR_PICKUP",paymentStatus:"PAID",paymentSimulation:true,pickupCode:"0042",
    updatedAt:Timestamp.fromMillis(1_800_000_000_000),buyerUid:"buyer",storeId:"store",buyerSnapshot:{displayName:"Cliente",phone:null},
    storeSnapshot:{name:"Local",phone:null},pickupSnapshot:{addressText:"Local 1",location:{latitude:-12.1,longitude:-77.1}},
    dropoffSnapshot:{addressText:"Casa",location:{latitude:-12.2,longitude:-77.2}},items:[{productId:"p",name:"Plato",quantity:2,unitPriceMinor:1000}],
    subtotalMinor:2000,deliveryFeeMinor:300,tipMinor:100,totalMinor:2400,currency:"PEN"};
  const projected:any=projectRiderOrder(order);
  assert.equal(projected.pickupCode,"0042");assert.deepEqual(projected.items,[{id:"p",name:"Plato",quantity:2}]);
  assert.equal("unitPriceMinor" in projected.items[0],false);
  assert.equal(projectRiderOrder({...order,status:"IN_DELIVERY"}).status,"IN_DELIVERY");
  assert.equal(projectRiderOrder({...order,status:"COMPLETED"}).status,"COMPLETED");
  assert.throws(()=>projectRiderOrder({...order,paymentStatus:"PENDING"}));
});
