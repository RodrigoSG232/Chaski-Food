import { DeviceRegistry } from "../src/notifications/deviceRegistry";
import { OrderEventProcessor,RetryOrderEvent,EVENT_LEASE_MS,MAX_SEND_ATTEMPTS } from "../src/notifications/orderEventProcessor";
import { NoticeSender,NewOrderNotice,SendResult } from "../src/notifications/fcmSender";
import { digest } from "../src/access/commercialAccessPolicy";
import { orderEventIdFor } from "../src/orders/orderService";
import { before,after,test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp,deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore,Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";
import { encodeInboxCursor } from "../src/orders/storeOrdersPolicy";
import { StoreOrdersReader } from "../src/orders/storeOrdersReader";
process.env.FIRESTORE_EMULATOR_HOST="127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:9099";
const project="demo-chaski-hu15",app=initializeApp({projectId:project},"hu16-notifications-tests"),db=getFirestore(app),auth=getAuth(app);
const access=new CommercialAccessService(db,auth),base=`http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1/stores/`;
type Actor=CommercialIdentity&{token:string};let n=0;const prefix=`notice-${Date.now()}`;let admin:Actor;
async function login(uid:string):Promise<Actor> {
  const user=await auth.getUser(uid);
  const r=await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key",{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:user.email,password:"emulator-password",returnSecureToken:true})});
  assert.equal(r.status,200);
  return {uid,email:user.email!,emailVerified:user.emailVerified,admin:user.customClaims?.admin===true,token:(await r.json() as {idToken:string}).idToken};
}
async function actor(verified=true,isAdmin=false):Promise<Actor> {
  const uid=`${prefix}-${++n}`;
  await auth.createUser({uid,email:`${uid}@example.test`,password:"emulator-password",emailVerified:verified});
  if(isAdmin)await auth.setCustomUserClaims(uid,{admin:true});return login(uid);
}
async function prepare(a:{email:string},revision=0) {
  return (await access.execute(admin,{action:"PREPARE",operationId:`prepare-${++n}`,email:a.email,expectedRevision:revision})).revision;
}
async function fixture(verified=true) {
  const owner=await actor(),manager=await actor(verified),buyer=await actor();
  const store=`${owner.uid}-store`,other=`${owner.uid}-other`;
  await db.doc(`business_requests/${owner.uid}`).set({ownerUid:owner.uid,status:"APPROVED",businessName:"Negocio ficticio"});
  for(const id of [store,other])await db.doc(`stores/${id}`).set({businessId:owner.uid,name:"Local ficticio",status:"ACTIVE",operationalStatus:"OPEN"});
  await prepare(owner);await prepare(buyer);await prepare(manager);
  const assignment=await access.execute(owner,{action:"ASSIGN",operationId:`assign-${++n}`,businessId:owner.uid,
    email:manager.email,expectedRevision:1,storeIds:[store],fullName:"Responsable ficticio",phone:"000"});
  return {owner,manager,buyer,store,other,revision:assignment.revision};
}
async function seedOrder(store:string,businessId:string,id:string,createdAt=Timestamp.fromMillis(1800000000000)) {
  const data={schemaVersion:1,orderId:id,businessId,storeId:store,status:"PENDING_CONFIRMATION",createdAt,updatedAt:createdAt,
    buyerUid:"PRIVATE_BUYER",buyerSnapshot:{displayName:"PRIVATE_NAME",phone:"PRIVATE_PHONE"},dropoffSnapshot:{addressText:"PRIVATE_ADDRESS"},
    pickupSnapshot:{addressText:"PRIVATE_PICKUP"},intentId:randomUUID(),requestHash:"PRIVATE_HASH",quoteSnapshot:{quoteId:"PRIVATE_QUOTE"},
    items:[{productId:"product",categoryId:"category",name:"Plato ficticio",quantity:2,unitPriceMinor:1000,lineTotalMinor:2000}],
    subtotalMinor:2000,deliveryFeeMinor:300,totalMinor:2300,currency:"PEN",paymentMethod:"CASH_ON_DELIVERY"};
  await db.doc(`food_orders/${id}`).set(data);return data;
}
async function page(a:Actor|undefined,store:string,query="") {
  const response=await fetch(`${base}${store}/orders${query}`,{headers:a?{Authorization:`Bearer ${a.token}`}:{},signal:AbortSignal.timeout(20000)});
  return {status:response.status,body:await response.json() as any};
}
before(async()=>{admin=await actor(true,true);await prepare(admin);});
after(async()=>{await db.terminate();await deleteApp(app);});


const devices=new DeviceRegistry(db);
class Sender implements NoticeSender {
  calls:{token:string;data:NewOrderNotice}[]=[];
  behavior:(token:string)=>Promise<SendResult>=async()=>"ACCEPTED";
  async send(token:string,data:NewOrderNotice){this.calls.push({token,data});return this.behavior(token);}
}
async function device(uid:string,id=randomUUID(),token=`fictional-${randomUUID()}`){await devices.put(uid,id,{token,bindingVersion:1,platform:"ANDROID"});return {id,token,hash:digest(token)};}
async function event(f:Awaited<ReturnType<typeof fixture>>){
  const id=digest(randomUUID());await seedOrder(f.store,f.owner.uid,id);
  const eventId=orderEventIdFor(id),now=1800000000000;
  await db.doc(`order_events/${eventId}`).set({schemaVersion:1,type:"NEW_ORDER",orderId:id,businessId:f.owner.uid,storeId:f.store,
    status:"PENDING",attemptCount:0,nextAttemptAt:Timestamp.fromMillis(now),createdAt:Timestamp.fromMillis(now)});
  return {id:eventId,ref:db.doc(`order_events/${eventId}`),order:db.doc(`food_orders/${id}`),now};
}
async function revoke(f:Awaited<ReturnType<typeof fixture>>){await access.execute(f.owner,{action:"REVOKE",operationId:`revoke-${++n}`,
  businessId:f.owner.uid,email:f.manager.email,expectedRevision:f.revision});}

test("NEW_ORDER sends minimal hints only to verified scoped manager devices; replay never resends accepted progress",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();
  await device(f.manager.uid);await device(f.manager.uid);await device(f.owner.uid);await device(f.buyer.uid);await device(admin.uid);
  const other=await fixture();await device(other.manager.uid);
  const before=(await e.order.get()).data();const processor=new OrderEventProcessor(db,auth,sender,()=>e.now);
  await processor.process(e.id);await processor.process(e.id);
  assert.equal(sender.calls.length,2);assert.equal((await e.ref.get()).data()?.status,"SENT");
  for(const call of sender.calls){assert.deepEqual(Object.keys(call.data).sort(),["type","eventId","orderId","storeId","businessId","recipientUid"].sort());
    assert.equal(call.data.recipientUid,f.manager.uid);assert.equal(JSON.stringify(call.data).includes("PRIVATE"),false);}
  assert.deepEqual((await e.order.get()).data(),before);
});
test("busy concurrent claim does not acknowledge original event and only one invocation sends",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);
  let release!:()=>void;let entered!:()=>void;const ready=new Promise<void>(r=>entered=r),gate=new Promise<void>(r=>release=r);
  sender.behavior=async()=>{entered();await gate;return "ACCEPTED";};
  const processor=new OrderEventProcessor(db,auth,sender,()=>e.now),first=processor.process(e.id);await ready;
  await assert.rejects(processor.process(e.id),RetryOrderEvent);release();await first;assert.equal(sender.calls.length,1);
});
test("interrupted PROCESSING invocation is redelivered, waits for lease, and actually recovers",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);let now=e.now;
  sender.behavior=async()=>{throw new RetryOrderEvent();};
  const processor=new OrderEventProcessor(db,auth,sender,()=>now);
  await assert.rejects(processor.process(e.id),RetryOrderEvent);assert.equal((await e.ref.get()).data()?.status,"PROCESSING");
  await assert.rejects(processor.process(e.id),RetryOrderEvent);
  now+=EVENT_LEASE_MS;sender.behavior=async()=>"ACCEPTED";await processor.process(e.id);
  assert.equal((await e.ref.get()).data()?.status,"SENT");assert.equal((await e.ref.get()).data()?.attemptCount,2);
});
test("transient transport retries only when due and preserves successful per-device progress",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);await device(f.manager.uid);let now=e.now;
  let calls=0;sender.behavior=async()=>{if(++calls===2)throw new Error("simulated network");return "ACCEPTED";};
  const processor=new OrderEventProcessor(db,auth,sender,()=>now);await assert.rejects(processor.process(e.id),RetryOrderEvent);
  await assert.rejects(processor.process(e.id),RetryOrderEvent);now+=60000;await processor.process(e.id);
  assert.equal(sender.calls.length,3);assert.equal((await e.ref.get()).data()?.status,"SENT");
});
test("invalid token is removed without removing valid devices or order",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender(),bad=await device(f.manager.uid);await device(f.manager.uid);
  sender.behavior=async t=>t===bad.token?"INVALID_TOKEN":"ACCEPTED";
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);
  assert.equal((await db.doc(`notification_devices/${bad.hash}`).get()).exists,false);
  assert.equal((await e.ref.get()).data()?.status,"SENT");assert.equal((await e.order.get()).exists,true);
});
test("no devices is an explicit durable terminal outcome and never loses the order",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);
  assert.equal((await e.ref.get()).data()?.status,"NO_RECIPIENTS");assert.equal(sender.calls.length,0);assert.equal((await e.order.get()).exists,true);
});
test("revocation before send or before retry suppresses any later device send",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);let now=e.now;
  sender.behavior=async()=>{throw new Error("network");};const processor=new OrderEventProcessor(db,auth,sender,()=>now);
  await assert.rejects(processor.process(e.id),RetryOrderEvent);await revoke(f);now+=60000;sender.behavior=async()=>"ACCEPTED";
  await processor.process(e.id);assert.equal(sender.calls.length,1);assert.equal((await e.ref.get()).data()?.status,"NO_RECIPIENTS");
  const second=await event(f);await processor.process(second.id);assert.equal(sender.calls.length,1);
});
test("assigned manager still receives while business and store are suspended",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});await db.doc(`stores/${f.store}`).update({status:"SUSPENDED",operationalStatus:"PAUSED"});
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);assert.equal(sender.calls.length,1);
});
test("pending email assignment, unverified identity and disabled accounts cannot receive",async()=>{
  const f=await fixture(false),e=await event(f),sender=new Sender();await device(f.manager.uid);
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);assert.equal(sender.calls.length,0);
  const other=await fixture(),second=await event(other);await device(other.manager.uid);await auth.updateUser(other.manager.uid,{disabled:true});
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(second.id);assert.equal(sender.calls.length,0);
});
test("permanent failures and exhausted attempts remain durable without order mutation",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);sender.behavior=async()=>"PERMANENT_FAILURE";
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);assert.equal((await e.ref.get()).data()?.status,"FAILED_PERMANENT");
  const second=await event(f);let now=second.now;sender.behavior=async()=>{throw new Error("network");};
  const processor=new OrderEventProcessor(db,auth,sender,()=>now);
  for(let i=0;i<MAX_SEND_ATTEMPTS;i++){await processor.process(second.id).catch(error=>assert.ok(error instanceof RetryOrderEvent));now+=900001;}
  await processor.process(second.id);assert.equal((await second.ref.get()).data()?.status,"EXHAUSTED");
  assert.equal((await second.ref.get()).data()?.attemptCount,MAX_SEND_ATTEMPTS);assert.equal((await second.order.get()).exists,true);
});
test("registry renewal, multiple installations, cross-UID token transfer and stale logout preserve current owner",async()=>{
  const a=await actor(),b=await actor(),first=await device(a.uid),second=await device(a.uid);
  const renewed=`fictional-${randomUUID()}`;await devices.put(a.uid,first.id,{token:renewed,bindingVersion:2,platform:"ANDROID"});
  assert.equal((await db.doc(`notification_devices/${first.hash}`).get()).exists,false);
  await devices.put(b.uid,first.id,{token:renewed,bindingVersion:3,platform:"ANDROID"});await devices.remove(a.uid,first.id,4);
  assert.equal((await db.doc(`notification_devices/${digest(renewed)}`).get()).data()?.uid,b.uid);
  await assert.rejects(devices.put(a.uid,first.id,{token:renewed,bindingVersion:2,platform:"ANDROID"}));
  await devices.remove(b.uid,first.id,4);assert.equal((await db.doc(`notification_devices/${digest(renewed)}`).get()).data()?.enabled,false);
  assert.equal((await db.doc(`notification_devices/${second.hash}`).get()).exists,true);
});
test("device HTTP uses authenticated UID only, refuses body UID and denies direct private registry reads",async()=>{
  const a=await actor(),b=await actor(),id=randomUUID(),token=`fictional-${randomUUID()}`;
  const url=`http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1/devices/${id}`;
  const put=(body:unknown,bearer?:string)=>fetch(url,{method:"PUT",headers:{"Content-Type":"application/json",...(bearer?{Authorization:`Bearer ${bearer}`}:{})},body:JSON.stringify(body)});
  assert.equal((await put({token,bindingVersion:1,platform:"ANDROID"})).status,401);
  assert.equal((await put({token,bindingVersion:1,platform:"ANDROID",uid:b.uid},a.token)).status,400);
  const response=await put({token,bindingVersion:1,platform:"ANDROID"},a.token);assert.equal(response.status,204);assert.equal(await response.text(),"");
  const denied=await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents/notification_devices/${digest(token)}`,{headers:{Authorization:`Bearer ${b.token}`}});
  assert.equal(denied.status,403);
  assert.equal((await fetch(url+"?bindingVersion=2",{method:"DELETE",headers:{Authorization:`Bearer ${b.token}`}})).status,204);
  assert.equal((await db.doc(`notification_devices/${digest(token)}`).get()).data()?.uid,a.uid);
  assert.equal((await fetch(url+"?bindingVersion=2",{method:"DELETE",headers:{Authorization:`Bearer ${a.token}`}})).status,204);
  assert.equal((await db.doc(`notification_devices/${digest(token)}`).get()).data()?.enabled,false);
});

test("authorization is checked again between devices when assignment changes mid-attempt",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender();await device(f.manager.uid);await device(f.manager.uid);
  sender.behavior=async()=>{await revoke(f);return "ACCEPTED";};
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);assert.equal(sender.calls.length,1);
});
test("registered tokens never appear in a tombstone after logout",async()=>{
  const a=await actor(),d=await device(a.uid);await devices.remove(a.uid,d.id,2);
  const stored=(await db.doc(`notification_devices/${d.hash}`).get()).data();
  assert.equal(stored?.enabled,false);assert.equal(stored?.token,undefined);
  await assert.rejects(devices.put(a.uid,d.id,{token:d.token,bindingVersion:1,platform:"ANDROID"}));
});

test("actual exported trigger handles a redelivery after an interrupted claim, without a new event write",async()=>{
  const f=await fixture(),e=await event(f);await device(f.manager.uid);
  await e.ref.update({status:"PROCESSING",attemptCount:1,leaseId:"interrupted",leaseUntil:Timestamp.fromMillis(Date.now()+60000),nextAttemptAt:Timestamp.fromMillis(1)});
  const {newOrderNotifier}=await import("../src/notifications/newOrderNotifier");
  const old=process.env.CHASKI_NOTIFICATIONS_FIXTURE,oldEmulator=process.env.FUNCTIONS_EMULATOR;process.env.CHASKI_NOTIFICATIONS_FIXTURE="enabled";process.env.FUNCTIONS_EMULATOR="true";
  try {
    await assert.rejects(newOrderNotifier.run({params:{eventId:e.id}} as any),RetryOrderEvent);
    await e.ref.update({leaseUntil:Timestamp.fromMillis(Date.now()-1)});
    await newOrderNotifier.run({params:{eventId:e.id}} as any);
    assert.equal((await e.ref.get()).data()?.status,"SENT");assert.equal((await e.ref.get()).data()?.attemptCount,2);
  }finally {if(old===undefined)delete process.env.CHASKI_NOTIFICATIONS_FIXTURE;else process.env.CHASKI_NOTIFICATIONS_FIXTURE=old;if(oldEmulator===undefined)delete process.env.FUNCTIONS_EMULATOR;else process.env.FUNCTIONS_EMULATOR=oldEmulator;}
});

test("devices authenticated before token revocation cannot receive until registered with valid credentials",async()=>{
  const f=await fixture(),e=await event(f),sender=new Sender(),d=await device(f.manager.uid);
  await db.doc(`notification_devices/${d.hash}`).update({authenticatedAt:Timestamp.fromMillis(1)});
  await auth.revokeRefreshTokens(f.manager.uid);
  await new OrderEventProcessor(db,auth,sender,()=>e.now).process(e.id);
  assert.equal(sender.calls.length,0);assert.equal((await e.ref.get()).data()?.status,"NO_RECIPIENTS");
});
