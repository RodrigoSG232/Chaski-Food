import { connectedHttp } from "./connectedHandler.fixture";
import { before, after, test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { Firestore, getFirestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";
import { createDeliveryQuote } from "../src/delivery/deliveryQuoteService";
import { OrderService, orderIdFor, orderEventIdFor } from "../src/orders/orderService";
import { AcceptedPurchase, OrderIntent, orderAmounts, parseAcceptedPurchase } from "../src/orders/orderModels";
import { consentHash, requestHash } from "../src/orders/orderPolicy";
import { parseOrderSubmission } from "../src/orders/orderRequest";
process.env.FIRESTORE_EMULATOR_HOST="127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:9099";
const project="demo-chaski-hu15",app=initializeApp({projectId:project},"hu16-orders");
const db=getFirestore(app),auth=getAuth(app),access=new CommercialAccessService(db,auth);
const base=`http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1`;
const prefix=`orders-${Date.now()}`;let n=0;let admin:Account;
type Account=CommercialIdentity & {token:string};
async function account(verified=true,isAdmin=false):Promise<Account> {
  const uid=`${prefix}-${++n}`,email=`${uid}@example.test`;
  await auth.createUser({uid,email,emailVerified:verified,password:"emulator-password",displayName:"Cliente ficticio"});
  if(isAdmin)await auth.setCustomUserClaims(uid,{admin:true});
  const r=await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key",{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,password:"emulator-password",returnSecureToken:true})});
  assert.equal(r.status,200);return {uid,email,emailVerified:verified,admin:isAdmin,token:(await r.json() as {idToken:string}).idToken};
}
async function prepareActor(a:Account|{email:string}) {
  return (await access.execute(admin,{action:"PREPARE",operationId:`op-${++n}`,email:a.email,expectedRevision:0})).revision;
}
async function fixture(options:{pending?:boolean;unverified?:boolean;noManager?:boolean}={}) {
  const owner=await account(),buyer=await account(!options.unverified),manager=options.pending?{email:`pending-${++n}@example.test`}:await account();
  const store=`${owner.uid}-store`,address="address",product=`${owner.uid}-product`,category=`${owner.uid}-category`;
  let tick=Date.now();const now=()=>new Date(tick),time=Timestamp.fromMillis(tick);
  const minute=(Number(new Intl.DateTimeFormat("en-US",{timeZone:"America/Lima",hour:"2-digit",hourCycle:"h23"}).format(now()))*60+
    Number(new Intl.DateTimeFormat("en-US",{timeZone:"America/Lima",minute:"2-digit"}).format(now()))+720)%1440;
  const hh=(m:number)=>`${String(Math.floor(m/60)).padStart(2,"0")}:${String(m%60).padStart(2,"0")}`;
  const hours=["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"].map(dayOfWeek=>({dayOfWeek,openTime:hh(minute),closeTime:hh((minute+1439)%1440),enabled:true}));
  await db.doc(`business_requests/${owner.uid}`).set({ownerUid:owner.uid,status:"APPROVED",businessName:"Negocio ficticio",privateNote:"PRIVATE_ADMIN"});
  await db.doc(`stores/${store}`).set({businessId:owner.uid,status:"ACTIVE",operationalStatus:"OPEN",name:"Local ficticio",address:"Plaza pública Lima",
    latitude:-12.0464,longitude:-77.0428,deliveryRadiusKm:5,phone:"987654321",operatingHours:hours});
  await db.doc(`users/${buyer.uid}`).set({uid:buyer.uid,displayName:"Cliente ficticio"});
  await db.doc(`users/${buyer.uid}/addresses/${address}`).set({label:"Casa",addressText:"Parque público Lima",location:{lat:-12.05,lng:-77.04},
    reference:"Puerta pública",instructions:"Llamar al llegar",createdAt:time,updatedAt:time});
  await db.doc(`categories/${category}`).set({businessId:owner.uid,name:"Categoría",isActive:true});
  await db.doc(`products/${product}`).set({businessId:owner.uid,categoryId:category,name:"Plato ficticio",price:9999,isActive:true});
  await db.doc(`store_product_configs/${store}_${product}`).set({businessId:owner.uid,storeId:store,productId:product,price:10,availability:"AVAILABLE",updatedAt:time,updatedBy:owner.uid});
  await prepareActor(owner);await prepareActor(buyer);const managerRevision=await prepareActor(manager);
  if(!options.noManager)await access.execute(owner,{action:"ASSIGN",operationId:`op-${++n}`,expectedRevision:managerRevision,businessId:owner.uid,email:manager.email,
    storeIds:[store],fullName:"Responsable ficticio",phone:"000"});
  let routes=0;
  const quoted=await createDeliveryQuote(db,{async distanceMeters(){routes++;return 1250;}},buyer.uid,store,address,now);
  const quote=quoted.quote;
  const accepted=parseAcceptedPurchase({ownerUid:buyer.uid,businessId:owner.uid,storeId:store,businessName:"Negocio ficticio",storeName:"Local ficticio",storePhone:"987654321",
    buyerName:"Cliente ficticio",buyerPhone:null,pickup:{addressText:"Plaza pública Lima",location:quote.storeLocation,reference:null,instructions:null},
    delivery:{addressId:address,label:"Casa",destination:{addressText:"Parque público Lima",location:quote.deliveryLocation,reference:"Puerta pública",instructions:"Llamar al llegar"}},
    items:[{productId:product,categoryId:category,name:"Plato ficticio",quantity:2,unitPriceMinor:1000}],amounts:{subtotalMinor:2000,deliveryFeeMinor:300,totalMinor:2300},
    currency:"PEN",paymentMethod:"CASH_ON_DELIVERY",deliveryQuote:{quoteId:quote.quoteId,distanceMeters:1250,tariffVersion:quote.tariffVersion,
      quotedAtEpochMillis:Date.parse(quote.quotedAt),expiresAtEpochMillis:Date.parse(quote.expiresAt)}});
  const intent:OrderIntent={intentId:randomUUID(),accepted};
  const service=new OrderService(db,auth,now);
  return {owner,buyer,manager,store,address,product,category,quote,intent,service,now,setTick:(value:number)=>{tick=value;},routes:()=>routes};
}
function bodyFor(intent:OrderIntent):Record<string,any> {
  const a=structuredClone(intent.accepted) as any;
  const {ownerUid,storeId,paymentMethod,...expected}=a;
  const addressId=expected.delivery.addressId,quoteId=expected.deliveryQuote.quoteId;
  delete expected.delivery.addressId;delete expected.deliveryQuote.quoteId;
  return {contractVersion:1,intentId:intent.intentId,storeId,addressId,quoteId,paymentMethod,expected};
}
async function http(token:string,path:string,body?:unknown) {
  const response=await fetch(`${base}${path}`,{method:body===undefined?"GET":"POST",headers:{"Content-Type":"application/json",...(token?{Authorization:`Bearer ${token}`}:{})},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  return {status:response.status,body:await response.json() as Record<string,any>};
}
async function absentRecords(f:Awaited<ReturnType<typeof fixture>>) {
  const id=orderIdFor(f.buyer.uid,f.intent.intentId);
  assert.equal((await db.doc(`food_orders/${id}`).get()).exists,false);
  assert.equal((await db.doc(`food_orders/${id}/status_history/initial`).get()).exists,false);
  assert.equal((await db.doc(`order_events/${orderEventIdFor(id)}`).get()).exists,false);
}
before(async()=>{admin=await account(true,true);});
after(async()=>{await db.terminate();await deleteApp(app);});

test("HTTP normal creates exactly four coherent records with effective store price and optional contact",async()=>{
  const f=await fixture(),r=await http(f.buyer.token,"/orders",bodyFor(f.intent));
  assert.equal(r.status,201,JSON.stringify(r.body));assert.equal(r.body.outcome,"SUCCEEDED");
  const id=r.body.receipt.orderId,order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.buyerUid,f.buyer.uid);assert.equal(order.items[0].unitPriceMinor,1000);assert.equal(order.items[0].lineTotalMinor,2000);
  assert.equal(order.totalMinor,2300);assert.equal(order.paymentStatus,"PENDING");assert.equal(order.paymentMethod,"CASH_ON_DELIVERY");
  assert.equal(order.status,"PENDING_CONFIRMATION");assert.equal(order.buyerSnapshot.phone,null);assert.equal(order.storeSnapshot.phone,"987654321");
  assert.equal(order.pickupSnapshot.addressText,"Plaza pública Lima");assert.equal(order.dropoffSnapshot.instructions,"Llamar al llegar");
  assert.equal((await db.doc(`order_attempts/${f.buyer.uid}/intents/${f.intent.intentId}`).get()).get("orderId"),id);
  assert.equal((await db.doc(`food_orders/${id}/status_history/initial`).get()).get("toStatus"),"PENDING_CONFIRMATION");
  const event=(await db.doc(`order_events/${orderEventIdFor(id)}`).get()).data()!;
  assert.equal(event.status,"PENDING");assert.equal(event.type,"NEW_ORDER");assert.equal(event.attemptCount,0);
  assert.ok(!("phone" in event)&&!("dropoffSnapshot" in event));assert.ok(!JSON.stringify(r.body).includes("PRIVATE_ADMIN"));assert.equal(f.routes(),1);
});
test("same quote is reusable for distinct legitimate intentions and is not consumed",async()=>{
  const f=await fixture(),before=(await db.doc(`delivery_quotes/${f.quote.quoteId}`).get()).data();
  const a=await f.service.confirm(f.intent),b=await f.service.confirm({...f.intent,intentId:randomUUID()});
  assert.equal(a.status,201);assert.equal(b.status,201);assert.notEqual((a.body.receipt as any).orderId,(b.body.receipt as any).orderId);
  assert.deepEqual((await db.doc(`delivery_quotes/${f.quote.quoteId}`).get()).data(),before);assert.equal(f.routes(),1);
});
test("HTTP missing intent is UNKNOWN and auth or malformed requests never imply terminal failure",async()=>{
  const f=await fixture(),missing=await http(f.buyer.token,`/order-attempts/${f.intent.intentId}`);
  assert.equal(missing.status,404);assert.equal(missing.body.outcome,"UNKNOWN");assert.equal(missing.body.error.retryable,true);
  assert.equal((await http("","/orders",bodyFor(f.intent))).status,401);
  assert.equal((await http("invalid","/orders",bodyFor(f.intent))).status,401);
  const bad=bodyFor(f.intent);bad.expected.ownerUid="attacker";
  assert.equal((await http(f.buyer.token,"/orders",bad)).status,400);
  assert.equal((await db.doc(`order_attempts/${f.buyer.uid}/intents/${f.intent.intentId}`).get()).exists,false);
});
test("lost response, later expiry and commercial role change still recover original success",async()=>{
  const f=await fixture(),first=await f.service.confirm(f.intent);assert.equal(first.status,201);
  f.setTick(Date.parse(f.quote.expiresAt));await db.doc(`business_requests/${f.buyer.uid}`).set({ownerUid:f.buyer.uid,status:"APPROVED",businessName:"Ahora propietario"});
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  const replay=await f.service.confirm(f.intent),recovered=await http(f.buyer.token,`/order-attempts/${f.intent.intentId}`);
  assert.equal(replay.status,200);assert.equal(recovered.status,200);assert.deepEqual(replay.body,first.body);assert.deepEqual(recovered.body,first.body);
  assert.equal((await f.service.confirm({...f.intent,intentId:randomUUID()})).body.outcome,"REJECTED_FINAL");
});
test("parallel identical HTTP submissions return one order and one event",async()=>{
  const f=await fixture(),results=await Promise.all(Array.from({length:6},()=>http(f.buyer.token,"/orders",bodyFor(f.intent))));
  assert.equal(results.filter(r=>r.status===201).length,1);assert.ok(results.every(r=>[200,201].includes(r.status)),JSON.stringify(results));
  assert.equal(new Set(results.map(r=>r.body.receipt.orderId)).size,1);
  const id=orderIdFor(f.buyer.uid,f.intent.intentId);assert.equal((await db.doc(`order_events/${orderEventIdFor(id)}`).get()).get("orderId"),id);
  assert.equal((await db.doc(`food_orders/${id}/status_history/initial`).get()).exists,true);
});
test("parallel incompatible payloads cannot replace an intention or create another order",async()=>{
  const f=await fixture(),changed=bodyFor(f.intent);changed.expected.items[0].quantity=3;
  changed.expected.amounts={subtotalMinor:3000,deliveryFeeMinor:300,totalMinor:3300};
  const results=await Promise.all([http(f.buyer.token,"/orders",bodyFor(f.intent)),http(f.buyer.token,"/orders",changed)]);
  assert.equal(results.filter(r=>r.status===201).length,1);assert.equal(results.filter(r=>r.body.error?.code==="INTENT_PAYLOAD_MISMATCH").length,1);
});
for(const [name,mutate] of [
  ["business suspended",async(f:any)=>db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"})],
  ["store inactive",async(f:any)=>db.doc(`stores/${f.store}`).update({status:"INACTIVE"})],
  ["store closed",async(f:any)=>db.doc(`stores/${f.store}`).update({operationalStatus:"CLOSED"})],
  ["outside hours",async(f:any)=>db.doc(`stores/${f.store}`).update({operatingHours:["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"].map(dayOfWeek=>({dayOfWeek,openTime:"08:00",closeTime:"22:00",enabled:false}))})],
  ["out of stock",async(f:any)=>db.doc(`store_product_configs/${f.store}_${f.product}`).update({availability:"OUT_OF_STOCK"})],
  ["category inactive",async(f:any)=>db.doc(`categories/${f.category}`).update({isActive:false})],
  ["product removed",async(f:any)=>db.doc(`products/${f.product}`).delete()],
  ["configuration missing",async(f:any)=>db.doc(`store_product_configs/${f.store}_${f.product}`).delete()],
  ["price up",async(f:any)=>db.doc(`store_product_configs/${f.store}_${f.product}`).update({price:11})],
  ["price down",async(f:any)=>db.doc(`store_product_configs/${f.store}_${f.product}`).update({price:9})],
  ["instructions changed",async(f:any)=>db.doc(`users/${f.buyer.uid}/addresses/${f.address}`).update({instructions:"Nueva instrucción"})],
  ["store renamed",async(f:any)=>db.doc(`stores/${f.store}`).update({name:"Nombre nuevo"})],
  ["outside coverage",async(f:any)=>db.doc(`users/${f.buyer.uid}/addresses/${f.address}`).update({location:{lat:-13,lng:-77}})],
] as const) test(`definitive rejection without partial order: ${name}`,async()=>{
  const f=await fixture();await mutate(f);const r=await f.service.confirm(f.intent);
  assert.equal(r.body.outcome,"REJECTED_FINAL",JSON.stringify(r));await absentRecords(f);
  const recovered=await f.service.recover(f.buyer.uid,f.intent.intentId);assert.equal(recovered.status,200);assert.deepEqual(recovered.body,r.body);
});
test("altered client totals rejected structurally; coherent fraudulent price still cannot override authoritative price",async()=>{
  const f=await fixture(),b=bodyFor(f.intent);b.expected.amounts.totalMinor=1;
  assert.equal((await http(f.buyer.token,"/orders",b)).status,400);
  const forged=bodyFor(f.intent);forged.expected.items[0].unitPriceMinor=1;forged.expected.amounts={subtotalMinor:2,deliveryFeeMinor:300,totalMinor:302};
  const r=await http(f.buyer.token,"/orders",forged);assert.equal(r.body.outcome,"REJECTED_FINAL");await absentRecords(f);
});
test("quote exact expiry, wrong owner and changed geometry cannot authorize an order",async()=>{
  for(const change of ["expiry","owner","destination","version"]) {
    const f=await fixture();
    if(change==="expiry")f.setTick(Date.parse(f.quote.expiresAt));
    if(change==="version")await db.doc(`delivery_quotes/${f.quote.quoteId}`).update({tariffVersion:"obsolete"});
    if(change==="owner")await db.doc(`delivery_quotes/${f.quote.quoteId}`).update({customerUid:"other"});
    if(change==="destination")await db.doc(`users/${f.buyer.uid}/addresses/${f.address}`).update({location:{lat:-12.051,lng:-77.04}});
    const r=await f.service.confirm(f.intent);assert.equal(r.body.outcome,"REJECTED_FINAL");await absentRecords(f);
  }
});
test("corrupted quote or fractional source price is transient and never terminalizes the intent",async()=>{
  for(const field of ["quote","price"]) {
    const f=await fixture();
    if(field==="quote")await db.doc(`delivery_quotes/${f.quote.quoteId}`).update({deliveryFeeMinor:0});
    else await db.doc(`store_product_configs/${f.store}_${f.product}`).update({price:10.001});
    await assert.rejects(f.service.confirm(f.intent));await absentRecords(f);
    assert.equal((await f.service.recover(f.buyer.uid,f.intent.intentId)).body.outcome,"UNKNOWN");
  }
});
test("current verified HU34 facts deny owner and manager regardless of administrative claims",async()=>{
  const f=await fixture();
  for(const who of [f.owner,f.manager as Account]) {
    await auth.setCustomUserClaims(who.uid,{admin:true});
    const i={...f.intent,intentId:randomUUID(),accepted:parseAcceptedPurchase({...f.intent.accepted,ownerUid:who.uid})};
    const r=await f.service.confirm(i);assert.equal(r.status,403);assert.equal(r.body.reason,"PURCHASE_FORBIDDEN");
  }
});
test("admin claim alone does not grant purchase; verified CLIENT facts still follow ResolveAccess",async()=>{
  const f=await fixture();await auth.setCustomUserClaims(f.buyer.uid,{admin:true});
  assert.equal((await f.service.confirm(f.intent)).status,201);
  await db.doc(`commercial_access_index/uid_${f.buyer.uid}`).delete();
  await assert.rejects(f.service.confirm({...f.intent,intentId:randomUUID()}));
});
test("unactivated PENDING manager can receive and unverified CLIENT email is not a new purchase restriction",async()=>{
  const f=await fixture({pending:true,unverified:true});assert.equal((await f.service.confirm(f.intent)).status,201);
});
test("no receiving manager rejects; concurrent last revocation serializes with the order",async()=>{
  const empty=await fixture({noManager:true});const rejected=await empty.service.confirm(empty.intent);
  assert.equal(rejected.body.reason,"NO_RESPONSIBLE");await absentRecords(empty);
  const f=await fixture(),manager=f.manager as Account;
  const results=await Promise.all([f.service.confirm(f.intent),access.execute(f.owner,{action:"REVOKE",businessId:f.owner.uid,email:manager.email,
    expectedRevision:2,operationId:`op-${++n}`})]);
  assert.ok([201,409].includes(results[0].status));
  const later=await f.service.confirm({...f.intent,intentId:randomUUID()});assert.equal(later.body.reason,"NO_RESPONSIBLE");
  if(results[0].status===409)await absentRecords(f);
});
test("snapshot survives edits/deletions of source data and phone; cross UID and direct Firestore are denied",async()=>{
  const f=await fixture();const phone=`+519${String(++n).padStart(8,"0")}`;await auth.updateUser(f.buyer.uid,{phoneNumber:phone});
  const intent={...f.intent,accepted:parseAcceptedPurchase({...f.intent.accepted,buyerPhone:phone})};
  const first=await f.service.confirm(intent);assert.equal(first.status,201);
  await auth.updateUser(f.buyer.uid,{phoneNumber:`+519${String(++n).padStart(8,"0")}`});
  await db.doc(`products/${f.product}`).delete();await db.doc(`stores/${f.store}`).update({name:"Otro",address:"Otra dirección"});
  await db.doc(`users/${f.buyer.uid}/addresses/${f.address}`).delete();
  // Simulate an external later state update; no HU19 operation is implemented.
  await db.doc(`food_orders/${(first.body.receipt as any).orderId}`).update({status:"LATER_EXTERNAL_STATE",paymentStatus:"LATER_EXTERNAL_STATE"});
  const recovered=await f.service.recover(f.buyer.uid,intent.intentId);assert.deepEqual(recovered.body,first.body);
  const other=await account();assert.equal((await http(other.token,`/order-attempts/${intent.intentId}`)).body.outcome,"UNKNOWN");
  const orderId=(first.body.receipt as any).orderId;
  for(const path of [`food_orders/${orderId}`,`food_orders/${orderId}/status_history/initial`,`order_attempts/${f.buyer.uid}/intents/${intent.intentId}`,`order_events/${orderEventIdFor(orderId)}`]) {
    const url=`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents/${path}`;
    for(const actor of [f.buyer,admin]) {
      assert.equal((await fetch(url,{headers:{Authorization:`Bearer ${actor.token}`}})).status,403);
      assert.equal((await fetch(url,{method:"PATCH",headers:{Authorization:`Bearer ${actor.token}`,"Content-Type":"application/json"},body:JSON.stringify({fields:{status:{stringValue:"HACKED"}}})})).status,403);
    }
  }
});
test("empty HU04 label is preserved without invented pickup instructions or buyer phone",async()=>{
  const f=await fixture();await db.doc(`users/${f.buyer.uid}/addresses/${f.address}`).update({label:""});
  const accepted=parseAcceptedPurchase({...f.intent.accepted,delivery:{...f.intent.accepted.delivery,label:""}});
  const r=await f.service.confirm({...f.intent,accepted});assert.equal(r.status,201);
  assert.equal((r.body.receipt as any).accepted.delivery.label,"");assert.equal((r.body.receipt as any).accepted.pickup.instructions,null);
});
test("queued-write failure leaves no partial records and same ID can recover after retry",async()=>{
  const f=await fixture(),id=orderIdFor(f.buyer.uid,f.intent.intentId),event=db.doc(`order_events/${orderEventIdFor(id)}`);
  await event.create({collision:"synthetic"});await assert.rejects(f.service.confirm(f.intent));
  assert.equal((await db.doc(`food_orders/${id}`).get()).exists,false);
  assert.equal((await db.doc(`order_attempts/${f.buyer.uid}/intents/${f.intent.intentId}`).get()).exists,false);
  assert.equal((await db.doc(`food_orders/${id}/status_history/initial`).get()).exists,false);
  await event.delete();assert.equal((await f.service.confirm(f.intent)).status,201);
});
test("transaction retry rechecks expiry and discards all writes from aborted attempt",async()=>{
  const f=await fixture();let attempts=0;
  const proxy=new Proxy(db,{get(target,prop){
    if(prop==="runTransaction")return (fn:any,options:any)=>target.runTransaction(async tx=>{
      const result=await fn(tx);
      if(!options?.readOnly && ++attempts===1) {f.setTick(Date.parse(f.quote.expiresAt));throw Object.assign(new Error("controlled abort"),{code:10});}
      return result;
    },options);
    const value=Reflect.get(target,prop,target);return typeof value==="function"?value.bind(target):value;
  }}) as Firestore;
  const r=await new OrderService(proxy,auth,f.now).confirm(f.intent);
  assert.ok(attempts>=2);assert.equal(r.body.reason,"QUOTE_EXPIRED");await absentRecords(f);
});

test("transient HTTP write failure reports UNKNOWN and same intent succeeds after recovery",async()=>{
  const f=await fixture(),id=orderIdFor(f.buyer.uid,f.intent.intentId),event=db.doc(`order_events/${orderEventIdFor(id)}`);
  await event.create({collision:"synthetic-http"});
  const failure=await http(f.buyer.token,"/orders",bodyFor(f.intent));
  assert.equal(failure.status,503);assert.equal(failure.body.outcome,"UNKNOWN");assert.equal(failure.body.error.retryable,true);
  assert.equal((await http(f.buyer.token,`/order-attempts/${f.intent.intentId}`)).body.outcome,"UNKNOWN");
  await event.delete();const recovered=await http(f.buyer.token,"/orders",bodyFor(f.intent));
  assert.equal(recovered.status,201);assert.equal(recovered.body.outcome,"SUCCEEDED");
});
test("concurrent price change produces a coherent old snapshot or a review rejection, never silent repricing",async()=>{
  const f=await fixture();
  const [result]=await Promise.all([f.service.confirm(f.intent),db.doc(`store_product_configs/${f.store}_${f.product}`).update({price:11})]);
  if(result.status===201)assert.equal((result.body.receipt as any).accepted.items[0].unitPriceMinor,1000);
  else assert.equal(result.body.reason,"REVIEW_REQUIRED");
  const later=await f.service.confirm({...f.intent,intentId:randomUUID()});assert.equal(later.body.reason,"REVIEW_REQUIRED");
});
test("concurrent approval changes purchase capability through the existing HU34 transaction",async()=>{
  const f=await fixture();await db.doc(`business_requests/${f.buyer.uid}`).set({ownerUid:f.buyer.uid,status:"PENDING_REVIEW",businessName:"Solicitud ficticia"});
  const [result]=await Promise.all([f.service.confirm(f.intent),access.execute(admin,{action:"APPROVE",businessId:f.buyer.uid,
    operationId:`op-${++n}`,expectedRevision:1})]);
  assert.ok([201,403].includes(result.status));
  assert.equal((await f.service.confirm({...f.intent,intentId:randomUUID()})).body.reason,"PURCHASE_FORBIDDEN");
  if(result.status===201)assert.equal((await f.service.recover(f.buyer.uid,f.intent.intentId)).body.outcome,"SUCCEEDED");
});
test("unavailable Auth never terminalizes a new intent or creates an order",async()=>{
  const f=await fixture();
  const unavailableAuth={getUser:async()=>{throw new Error("controlled Auth unavailability");}} as any;
  await assert.rejects(new OrderService(db,unavailableAuth,f.now).confirm(f.intent));
  assert.equal((await f.service.recover(f.buyer.uid,f.intent.intentId)).body.outcome,"UNKNOWN");await absentRecords(f);
});


test("CLOUD handler keeps authenticated idempotency and buyer-only recovery with demo adapters",async()=>{
  const f=await fixture(),send=connectedHttp(app),body=bodyFor(f.intent);
  assert.equal((await send(undefined,"/orders",body)).status,401);
  assert.equal((await send("invalid-token","/orders",body)).status,401);
  const [first,second]=await Promise.all([send(f.buyer.token,"/orders",body),send(f.buyer.token,"/orders",body)]);
  assert.equal(first.body.outcome,"SUCCEEDED");assert.equal(second.body.receipt.orderId,first.body.receipt.orderId);
  assert.equal((await send(f.buyer.token,`/order-attempts/${f.intent.intentId}`)).body.outcome,"SUCCEEDED");
  assert.equal((await send(f.owner.token,`/order-attempts/${f.intent.intentId}`)).body.outcome,"UNKNOWN");
  assert.ok(!JSON.stringify(first.body).includes("PRIVATE_ADMIN"));
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  assert.equal((await send(f.buyer.token,"/orders",bodyFor({...f.intent,intentId:randomUUID()}))).body.reason,"BUSINESS_UNAVAILABLE");
  assert.equal((await send(f.buyer.token,`/order-attempts/${f.intent.intentId}`)).body.outcome,"SUCCEEDED");
});

test("CLOUD handler does not bypass HU34 purchase roles, altered consent or uncertain recovery",async()=>{
  const f=await fixture(),send=connectedHttp(app);
  const own=parseAcceptedPurchase({...f.intent.accepted,ownerUid:f.owner.uid});
  const denied=await send(f.owner.token,"/orders",bodyFor({intentId:randomUUID(),accepted:own}));
  assert.equal(denied.status,403);assert.equal(denied.body.reason,"PURCHASE_FORBIDDEN");
  const changed=structuredClone(bodyFor(f.intent)) as any;changed.expected.amounts.totalMinor++;
  assert.equal((await send(f.buyer.token,"/orders",changed)).status,400);
  assert.equal((await send(f.buyer.token,`/order-attempts/${randomUUID()}`)).body.outcome,"UNKNOWN");
  await absentRecords(f);
});
