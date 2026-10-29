import {test} from "node:test";
import assert from "node:assert/strict";
import {deviceToken,installationId} from "../src/notifications/deviceRegistry";
import {FirebaseNoticeSender,SimulatedNoticeSender} from "../src/notifications/fcmSender";
import {EVENT_LEASE_MS,MAX_SEND_ATTEMPTS} from "../src/notifications/orderEventProcessor";

test("device input validation rejects request paths and malformed tokens",()=>{
  assert.equal(installationId("12345678-1234-1234-1234-123456789abc"),"12345678-1234-1234-1234-123456789abc");
  for(const value of [null,"../foreign","","a".repeat(500)])assert.throws(()=>installationId(value));
  for(const value of [null,"short","token with whitespace".repeat(5),"x".repeat(4097)])assert.throws(()=>deviceToken(value));
  assert.equal(deviceToken("fixture_token_123456789"),"fixture_token_123456789");
});
test("LOCAL simulated transport never requires Messaging credentials",async()=>{
  assert.equal(await new SimulatedNoticeSender().send("fixture",{type:"NEW_ORDER",eventId:"event",orderId:"order",storeId:"store",businessId:"business",recipientUid:"uid"}),"ACCEPTED");
});
test("lease exceeds function timeout and send attempts remain bounded",()=>{
  assert.ok(EVENT_LEASE_MS>120000);assert.equal(MAX_SEND_ATTEMPTS,5);
});

test("actual Functions v2 creation trigger requests retry redelivery rather than relying on lease writes",async()=>{
  const {newOrderNotifier}=await import("../src/notifications/newOrderNotifier");
  const endpoint=(newOrderNotifier as unknown as {__endpoint:{eventTrigger:{retry:boolean};timeoutSeconds:number}}).__endpoint;
  assert.equal(endpoint.eventTrigger.retry,true);assert.equal(endpoint.timeoutSeconds,120);
});

test("FCM adapter classifies invalid/permanent/transient failures without leaking raw errors",async()=>{
  const notice={type:"NEW_ORDER" as const,eventId:"event",orderId:"order",storeId:"store",businessId:"business",recipientUid:"manager"};
  for(const [code,expected] of [["messaging/registration-token-not-registered","INVALID_TOKEN"],["messaging/invalid-registration-token","INVALID_TOKEN"],
    ["messaging/invalid-argument","PERMANENT_FAILURE"],["messaging/mismatched-credential","PERMANENT_FAILURE"]]){
    const sender=new FirebaseNoticeSender({} as any,async()=>{throw Object.assign(new Error("PRIVATE_TOKEN"),{code});});
    assert.equal(await sender.send("fictional",notice),expected);
  }
  const transient=new FirebaseNoticeSender({} as any,async()=>{throw Object.assign(new Error("PRIVATE_TOKEN"),{code:"messaging/server-unavailable"});});
  await assert.rejects(transient.send("fictional",notice),error=>error instanceof Error && error.message==="NOTIFICATION_TRANSPORT_UNAVAILABLE");
  const sender=new FirebaseNoticeSender({} as any,async message=>{
    assert.equal("notification" in message,false);assert.deepEqual(message.data,notice);return "accepted-message-id";
  });
  assert.equal(await sender.send("fictional",notice),"ACCEPTED");
});
