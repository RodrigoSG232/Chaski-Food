import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { DeviceRegistry } from "../src/notifications/deviceRegistry";
import { BuyerOrderEventProcessor } from "../src/notifications/buyerOrderEventProcessor";
import { BUYER_NOTICE_TYPES, buyerNoticeEventId, createBuyerNoticeEvent } from "../src/notifications/buyerNoticeEvent";
import { NoticeSender, OrderNotice, SendResult } from "../src/notifications/fcmSender";
import { RetryOrderEvent } from "../src/notifications/orderEventProcessor";

process.env.FIRESTORE_EMULATOR_HOST="127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:9099";
const app=initializeApp({projectId:"demo-chaski-hu15"},"hu24-buyer-notifications-tests");
const db=getFirestore(app),auth=getAuth(app),registry=new DeviceRegistry(db);
const prefix=`hu24-${Date.now()}`;
let sequence=0;
const uid=()=>`${prefix}-${++sequence}`;
const orderId=()=>randomUUID().replace(/-/g,"").repeat(2);
const at=()=>Timestamp.now();

class Sender implements NoticeSender {
  calls:{token:string;data:OrderNotice}[]=[];
  behavior:()=>Promise<SendResult>=async()=>"ACCEPTED";
  async send(token:string,data:OrderNotice){this.calls.push({token,data});return this.behavior();}
}
async function account(){const id=uid();await auth.createUser({uid:id,email:`${id}@example.test`,password:"fictional-password"});return id;}
async function device(owner:string){const token=`fictional-${randomUUID()}`;await registry.put(owner,randomUUID(),{token,bindingVersion:1,platform:"ANDROID"});return token;}
async function order(buyerUid:string){const id=orderId();await db.doc(`food_orders/${id}`).set({schemaVersion:1,orderId:id,buyerUid,
  status:"COMPLETED",privateAddress:"must never be in push"});return id;}
async function notice(id:string,buyerUid:string,type:typeof BUYER_NOTICE_TYPES[number],eventSequence=1){
  const eventId=buyerNoticeEventId(id,type,eventSequence);
  await db.runTransaction(async tx=>createBuyerNoticeEvent(tx,db,{orderId:id,buyerUid,type,sequence:eventSequence,at:at()}));
  return {id:eventId,ref:db.doc(`order_events/${eventId}`)};
}

after(async()=>{await db.terminate();await deleteApp(app);});

test("all buyer transitions send only minimal hints to the order owner, including old transitions after state advances",async()=>{
  const buyer=await account(),foreign=await account(),id=await order(buyer),mine=await device(buyer),outsider=await device(foreign);
  const sender=new Sender(),processor=new BuyerOrderEventProcessor(db,auth,sender);
  for(const [index,type] of BUYER_NOTICE_TYPES.entries()){
    const event=await notice(id,buyer,type,index+1);
    await processor.process(event.id);await processor.process(event.id);
    assert.equal((await event.ref.get()).data()?.status,"SENT");
  }
  assert.equal(sender.calls.length,BUYER_NOTICE_TYPES.length);
  for(const [index,call] of sender.calls.entries()){
    assert.equal(call.token,mine);assert.notEqual(call.token,outsider);
    assert.deepEqual(call.data,{type:BUYER_NOTICE_TYPES[index],eventId:buyerNoticeEventId(id,BUYER_NOTICE_TYPES[index],index+1),
      orderId:id,recipientUid:buyer});
    assert.equal(JSON.stringify(call.data).includes("privateAddress"),false);
  }
});

test("ownership change and disabled accounts stop dispatch without modifying the order",async()=>{
  const buyer=await account(),foreign=await account(),id=await order(buyer);await device(buyer);
  const sender=new Sender(),processor=new BuyerOrderEventProcessor(db,auth,sender);
  const changed=await notice(id,buyer,"ORDER_ACCEPTED");
  await db.doc(`food_orders/${id}`).update({buyerUid:foreign});
  await processor.process(changed.id);
  assert.equal((await changed.ref.get()).data()?.status,"FAILED_PERMANENT");
  await db.doc(`food_orders/${id}`).update({buyerUid:buyer});
  const disabled=await notice(id,buyer,"ORDER_READY_FOR_PICKUP");
  await auth.updateUser(buyer,{disabled:true});await processor.process(disabled.id);
  assert.equal((await disabled.ref.get()).data()?.status,"NO_RECIPIENTS");
  assert.equal(sender.calls.length,0);
});

test("transient send is retried and accepted delivery is not resent",async()=>{
  const buyer=await account(),id=await order(buyer);await device(buyer);
  const event=await notice(id,buyer,"ORDER_PICKED_UP"),sender=new Sender();
  let now=Date.now();sender.behavior=async()=>{throw Error("fictional network failure");};
  const processor=new BuyerOrderEventProcessor(db,auth,sender,()=>now);
  await assert.rejects(processor.process(event.id),RetryOrderEvent);
  assert.equal((await event.ref.get()).data()?.status,"RETRY");
  now+=61_000;sender.behavior=async()=>"ACCEPTED";
  await processor.process(event.id);await processor.process(event.id);
  assert.equal((await event.ref.get()).data()?.status,"SENT");
  assert.equal(sender.calls.length,2);
});

test("account disabled between devices stops later sends",async()=>{
  const buyer=await account(),id=await order(buyer);await device(buyer);await device(buyer);
  const event=await notice(id,buyer,"RIDER_ASSIGNED"),sender=new Sender();
  sender.behavior=async()=>{await auth.updateUser(buyer,{disabled:true});return "ACCEPTED";};
  await new BuyerOrderEventProcessor(db,auth,sender).process(event.id);
  assert.equal(sender.calls.length,1);
  assert.equal((await event.ref.get()).data()?.status,"SENT");
});
