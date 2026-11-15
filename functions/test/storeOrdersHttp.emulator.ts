import { connectedHttp } from "./connectedHandler.fixture";
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
import { buyerNoticeEventId } from "../src/notifications/buyerNoticeEvent";
process.env.FIRESTORE_EMULATOR_HOST="127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:9099";
const project="demo-chaski-hu15",app=initializeApp({projectId:project},"hu16-inbox-tests"),db=getFirestore(app),auth=getAuth(app);
const access=new CommercialAccessService(db,auth),base=`http://127.0.0.1:5001/${project}/southamerica-west1/foodApi/api/v1/stores/`;
type Actor=CommercialIdentity&{token:string};let n=0;const prefix=`inbox-${Date.now()}`;let admin:Actor;
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
async function decide(a:Actor,store:string,orderId:string,body:Record<string,unknown>) {
  return connectedHttp(app)(a.token,`/stores/${store}/orders/${orderId}/decision`,body,"POST");
}
async function extend(a:Actor,store:string,orderId:string,body:Record<string,unknown>) {
  return connectedHttp(app)(a.token,`/stores/${store}/orders/${orderId}/preparation-extensions`,body,"POST");
}
const extension=(additionalMinutes:unknown,expectedEstimateRevision:unknown,operationId:string=randomUUID())=>
  ({operationId,additionalMinutes,expectedEstimateRevision});
async function acceptedOrder(f:Awaited<ReturnType<typeof fixture>>,id:string,minutes=40) {
  await seedOrder(f.store,f.owner.uid,id);
  const accepted=await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:minutes});
  assert.equal(accepted.status,200);return accepted.body.estimatedReadyAtEpochMillis as number;
}
before(async()=>{admin=await actor(true,true);await prepare(admin);});
after(async()=>{await db.terminate();await deleteApp(app);});

test("verified assigned manager reads minimal own-store projection without mutating orders",async()=>{
  const f=await fixture(),id="a".repeat(64);const before=await seedOrder(f.store,f.owner.uid,id);
  const r=await page(f.manager,f.store);assert.equal(r.status,200);assert.equal(r.body.orders.length,1);
  assert.deepEqual(Object.keys(r.body).sort(),["contractVersion","storeId","storeName","orders","nextCursor"].sort());
  assert.equal(JSON.stringify(r.body).includes("PRIVATE"),false);assert.equal(r.body.orders[0].totalMinor,2300);
  assert.equal(r.body.orders[0].items[0].quantity,2);assert.equal(r.body.nextCursor,null);
  assert.deepEqual((await db.doc(`food_orders/${id}`).get()).data(),before);
});
test("manager accepts a pending order with an estimate and the transition is idempotent",async()=>{
  const f=await fixture(),id="ac".repeat(32);await seedOrder(f.store,f.owner.uid,id);
  const body={action:"ACCEPT",estimatedPreparationMinutes:25};
  const first=await decide(f.manager,f.store,id,body),repeated=await decide(f.manager,f.store,id,body);
  assert.equal(first.status,200);assert.equal(first.body.status,"PREPARING");assert.equal(repeated.status,200);
  assert.equal(first.body.estimatedReadyAtEpochMillis,repeated.body.estimatedReadyAtEpochMillis);
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;assert.equal(order.status,"PREPARING");
  assert.equal(order.estimatedPreparationMinutes,25);assert.equal(order.acceptedByUid,f.manager.uid);
  const history=await db.doc(`food_orders/${id}/status_history/manager_decision`).get();
  assert.equal(history.get("fromStatus"),"PENDING_CONFIRMATION");assert.equal(history.get("toStatus"),"PREPARING");
  const notice=await db.doc(`order_events/${buyerNoticeEventId(id,"ORDER_ACCEPTED",1)}`).get();
  assert.equal(notice.get("buyerUid"),"PRIVATE_BUYER");assert.equal(notice.get("type"),"ORDER_ACCEPTED");
  assert.equal((await db.collection("order_events").where("orderId","==",id).get()).size,1);
});
test("HU20: extensions add to the current estimate, keep the HU19 value and record v2 history",async()=>{
  const f=await fixture(),id="ae".repeat(32),initial=await acceptedOrder(f,id,40);
  const first=await extend(f.manager,f.store,id,extension(5,0));
  assert.equal(first.status,200,JSON.stringify(first.body));
  assert.deepEqual(Object.keys(first.body).sort(),["contractVersion","estimateRevision","estimatedReadyAtEpochMillis","outcome",
    "preparationExtendedMinutes","status"]);
  assert.equal(first.body.outcome,"EXTENDED");assert.equal(first.body.estimatedReadyAtEpochMillis,initial+5*60_000);
  const second=await extend(f.manager,f.store,id,extension(60,1));
  assert.equal(second.body.estimatedReadyAtEpochMillis,initial+65*60_000);
  assert.equal(second.body.estimateRevision,2);assert.equal(second.body.preparationExtendedMinutes,65);
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.status,"PREPARING");assert.equal(order.estimatedPreparationMinutes,40);
  assert.equal(order.estimatedReadyAt.toMillis(),initial+65*60_000);assert.equal(order.estimateUpdatedByUid,f.manager.uid);
  const history=(await db.collection(`food_orders/${id}/estimate_history`).orderBy("revision").get()).docs.map(d=>d.data());
  assert.deepEqual(history.map(h=>[h.schemaVersion,h.kind,h.revision,h.additionalMinutes]),[[2,"EXTENSION",1,5],[2,"EXTENSION",2,60]]);
  assert.equal(history[1].previousReadyAt.toMillis(),initial+5*60_000);assert.equal(history[1].actorUid,f.manager.uid);
  const preparing=await page(f.manager,f.store,"?status=PREPARING");
  const row=preparing.body.orders.find((o:any)=>o.orderId===id);
  assert.equal(row.estimatedPreparationMinutes,40);assert.equal(row.estimatedReadyAtEpochMillis,initial+65*60_000);
  assert.equal(row.estimateRevision,2);assert.equal(row.preparationExtendedMinutes,65);
  // HU19 retry contract still holds after extending: same accept returns the original answer, not a new state.
  const retried=await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:40});
  assert.equal(retried.status,200);assert.equal(retried.body.status,"PREPARING");
});
test("HU20: two intentional equal extensions both apply, with no cumulative cap",async()=>{
  const f=await fixture(),id="af".repeat(32),initial=await acceptedOrder(f,id,180);
  for(let revision=0;revision<5;revision++)
    assert.equal((await extend(f.manager,f.store,id,extension(60,revision))).status,200);
  const twice=[await extend(f.manager,f.store,id,extension(10,5)),await extend(f.manager,f.store,id,extension(10,6))];
  assert.deepEqual(twice.map(r=>r.body.estimateRevision),[6,7]);
  assert.equal(twice[1].body.preparationExtendedMinutes,320);
  assert.equal(twice[1].body.estimatedReadyAtEpochMillis,initial+320*60_000);
  assert.equal((await db.collection(`food_orders/${id}/estimate_history`).get()).size,7);
});
test("HU20: a retried operation never adds twice; reusing its ID with other data conflicts",async()=>{
  const f=await fixture(),id="a0".repeat(32),initial=await acceptedOrder(f,id,30);
  await db.doc(`food_orders/${id}`).update({deliveryId:"delivery-1"});
  const op=randomUUID(),first=await extend(f.manager,f.store,id,extension(15,0,op));
  const retries=await Promise.all([1,2,3].map(()=>extend(f.manager,f.store,id,extension(15,0,op))));
  for(const r of retries){assert.equal(r.status,200);assert.deepEqual(r.body,first.body);}
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.estimateRevision,1);assert.equal(order.estimatedReadyAt.toMillis(),initial+15*60_000);
  assert.equal((await db.collection(`food_orders/${id}/estimate_history`).get()).size,1);
  assert.equal((await db.collection(`food_orders/${id}/estimate_operations`).get()).size,1);
  assert.equal((await db.doc(`rider_events/estimate_${id}_1`).get()).get("status"),"PENDING");
  for(const body of [extension(20,0,op),extension(15,1,op)]) {
    const conflict=await extend(f.manager,f.store,id,body);
    assert.equal(conflict.status,409);assert.equal(conflict.body.error,"OPERATION_CONFLICT");
  }
  // After the order moves on, the original operation still replays its recorded result.
  await db.doc(`food_orders/${id}`).update({status:"READY_FOR_PICKUP"});
  assert.deepEqual((await extend(f.manager,f.store,id,extension(15,0,op))).body,first.body);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("estimateRevision"),1);
});
test("HU20: a stale revision is rejected and concurrent managers cannot both extend from the same view",async()=>{
  const f=await fixture(),id="a1".repeat(32),initial=await acceptedOrder(f,id,30);
  const results=await Promise.all([extend(f.manager,f.store,id,extension(10,0)),extend(f.manager,f.store,id,extension(20,0))]);
  assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
  const loser=results.find(r=>r.status===409)!,winner=results.find(r=>r.status===200)!;
  assert.equal(loser.body.error,"ESTIMATE_REVISION_CHANGED");
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.estimateRevision,1);assert.equal(order.estimatedReadyAt.toMillis(),winner.body.estimatedReadyAtEpochMillis);
  assert.ok([initial+10*60_000,initial+20*60_000].includes(order.estimatedReadyAt.toMillis()));
  const stale=await extend(f.manager,f.store,id,extension(5,0));
  assert.equal(stale.status,409);assert.equal(stale.body.error,"ESTIMATE_REVISION_CHANGED");
  assert.equal((await db.collection(`food_orders/${id}/estimate_history`).get()).size,1);
});
test("HU20: an expired estimate restarts from server time; legacy v1 history is preserved",async()=>{
  const f=await fixture(),id="b1".repeat(32);await acceptedOrder(f,id,30);
  const past=Timestamp.fromMillis(Date.now()-3_600_000);
  // Legacy HU20 replacement: overwritten minutes, revision 1 and a schemaVersion 1 history entry, no extension fields.
  await db.doc(`food_orders/${id}`).update({estimatedPreparationMinutes:25,estimatedReadyAt:past,estimateRevision:1,updatedAt:past});
  await db.doc(`food_orders/${id}/estimate_history/0000000001`).set({schemaVersion:1,revision:1,minutes:25,estimatedReadyAt:past,
    actorUid:f.manager.uid,occurredAt:past});
  const before=Date.now(),r=await extend(f.manager,f.store,id,extension(10,1)),after=Date.now();
  assert.equal(r.status,200,JSON.stringify(r.body));
  assert.ok(r.body.estimatedReadyAtEpochMillis>=before+10*60_000&&r.body.estimatedReadyAtEpochMillis<=after+10*60_000);
  assert.equal(r.body.estimateRevision,2);assert.equal(r.body.preparationExtendedMinutes,10);
  const legacy=(await db.doc(`food_orders/${id}/estimate_history/0000000001`).get()).data()!;
  assert.equal(legacy.schemaVersion,1);assert.equal(legacy.minutes,25);
  assert.equal((await db.doc(`food_orders/${id}/estimate_history/0000000002`).get()).get("baseAt").toMillis()>=before,true);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("estimatedPreparationMinutes"),25);
});
test("HU20: validation, state, authorization and the retired replacement endpoint",async()=>{
  const f=await fixture(),id="b2".repeat(32);await seedOrder(f.store,f.owner.uid,id);
  for(const body of [extension(4,0),extension(61,0),extension(10.5,0),extension("10",0),extension(10,-1),
    {additionalMinutes:10,expectedEstimateRevision:0},{...extension(10,0),estimatedReadyAt:1},{estimatedPreparationMinutes:30}]) {
    const r=await extend(f.manager,f.store,id,body as Record<string,unknown>);assert.equal(r.status,400,JSON.stringify(body));
  }
  assert.equal((await extend(f.manager,f.store,id,extension(10,0))).status,409);
  await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:20});
  for(const [a,store] of [[f.manager,f.other],[f.buyer,f.store],[f.owner,f.store]] as const)
    assert.equal((await extend(a,store,id,extension(10,0))).status,403);
  const http=connectedHttp(app);
  const legacy=await http(f.manager.token,`/stores/${f.store}/orders/${id}/preparation-estimate`,{estimatedPreparationMinutes:45},"PUT");
  assert.equal(legacy.status,404);assert.equal(legacy.body.error,"NOT_FOUND");
  assert.equal((await http(f.manager.token,`/stores/${f.store}/orders/${id}/preparation-extensions`,undefined,"GET")).status,405);
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.estimatedPreparationMinutes,20);assert.equal(order.estimateRevision,undefined);
  assert.equal((await db.collection(`food_orders/${id}/estimate_history`).get()).size,0);
  for(const status of ["READY_FOR_PICKUP","IN_DELIVERY","COMPLETED","CANCELLED","REJECTED"]) {
    await db.doc(`food_orders/${id}`).update({status});
    const r=await extend(f.manager,f.store,id,extension(10,0));
    assert.equal(r.status,409,status);assert.equal(r.body.error,"ORDER_STATE_CHANGED");
  }
});
test("HU20: suspension blocks new acceptance but accepted orders can still be extended",async()=>{
  const f=await fixture(),id="b3".repeat(32),other="b4".repeat(32);await acceptedOrder(f,id,30);await acceptedOrder(f,other,30);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  assert.equal((await extend(f.manager,f.store,id,extension(10,0))).status,200);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"APPROVED"});
  await db.doc(`stores/${f.store}`).update({status:"SUSPENDED"});
  assert.equal((await extend(f.manager,f.store,other,extension(10,0))).status,200);
});
test("rejection records a required reason and removes the order from the pending inbox",async()=>{
  const f=await fixture(),id="bd".repeat(32);await seedOrder(f.store,f.owner.uid,id);
  const result=await decide(f.manager,f.store,id,{action:"REJECT",reason:"OTHER",explanation:"Sin insumos frescos"});
  assert.equal(result.status,200);assert.equal(result.body.status,"REJECTED");
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.rejectionReason,"OTHER");assert.equal(order.rejectionExplanation,"Sin insumos frescos");
  assert.equal((await db.doc(`order_events/${buyerNoticeEventId(id,"ORDER_REJECTED",1)}`).get()).get("buyerUid"),"PRIVATE_BUYER");
  assert.equal((await page(f.manager,f.store)).body.orders.some((row:any)=>row.orderId===id),false);
});
test("marking a paid order ready creates one buyer notice atomically",async()=>{
  const f=await fixture(),id="f1".repeat(32);await seedOrder(f.store,f.owner.uid,id);
  assert.equal((await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:20})).status,200);
  await db.doc(`food_orders/${id}`).update({paymentStatus:"PAID",paymentSimulation:true,pickupCode:"1234"});
  const send=connectedHttp(app),path=`/stores/${f.store}/orders/${id}/ready-for-pickup`;
  assert.equal((await send(f.manager.token,path,{},"POST")).status,200);
  assert.equal((await send(f.manager.token,path,{},"POST")).status,200);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("status"),"READY_FOR_PICKUP");
  const notice=await db.doc(`order_events/${buyerNoticeEventId(id,"ORDER_READY_FOR_PICKUP",2)}`).get();
  assert.equal(notice.get("buyerUid"),"PRIVATE_BUYER");
  assert.equal((await db.collection("order_events").where("orderId","==",id).get()).size,2);
});
const readyPath=(store:string,id:string)=>`/stores/${store}/orders/${id}/ready-for-pickup`;
async function cashPreparing(f:Awaited<ReturnType<typeof fixture>>,id:string) {
  await seedOrder(f.store,f.owner.uid,id);
  await db.doc(`food_orders/${id}`).update({paymentStatus:"PENDING"});
  assert.equal((await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:20})).status,200);
}
test("HU21-A: solo el responsable VERIFIED asignado marca listo; los demás no cambian nada",async()=>{
  const f=await fixture(),id="71".repeat(32),send=connectedHttp(app);await cashPreparing(f,id);
  const before=(await db.doc(`food_orders/${id}`).get()).data();
  for(const [a,store] of [[f.owner,f.store],[f.buyer,f.store],[f.manager,f.other]] as const)
    assert.equal((await send(a.token,readyPath(store,id),{},"POST")).status,403);
  const unverified=await fixture(false),uid="72".repeat(32);await seedOrder(unverified.store,unverified.owner.uid,uid);
  await db.doc(`food_orders/${uid}`).update({status:"PREPARING",paymentStatus:"PENDING"});
  assert.equal((await send(unverified.manager.token,readyPath(unverified.store,uid),{},"POST")).status,403);
  assert.equal((await db.doc(`food_orders/${uid}`).get()).get("status"),"PREPARING");
  assert.deepEqual((await db.doc(`food_orders/${id}`).get()).data(),before);
  assert.equal((await db.doc(`food_orders/${id}/status_history/ready_for_pickup`).get()).exists,false);
});
test("HU21-A: PREPARING en efectivo pasa a listo con historial, código y aviso; el reintento no cambia nada",async()=>{
  const f=await fixture(),id="73".repeat(32),send=connectedHttp(app);await cashPreparing(f,id);
  const first=await send(f.manager.token,readyPath(f.store,id),{},"POST");
  assert.equal(first.status,200);assert.equal(first.body.outcome,"UPDATED");assert.match(first.body.pickupCode,/^[0-9]{4}$/);
  const order=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(order.status,"READY_FOR_PICKUP");assert.equal(order.pickupCode,first.body.pickupCode);
  assert.equal(order.readyByUid,f.manager.uid);assert.equal(order.readyAt.toMillis(),first.body.readyAtEpochMillis);
  assert.equal(order.paymentStatus,"PENDING");assert.equal(order.estimatedPreparationMinutes,20);
  const history=(await db.doc(`food_orders/${id}/status_history/ready_for_pickup`).get()).data()!;
  assert.deepEqual({...history,occurredAt:history.occurredAt.toMillis()},{schemaVersion:1,fromStatus:"PREPARING",
    toStatus:"READY_FOR_PICKUP",actorUid:f.manager.uid,occurredAt:first.body.readyAtEpochMillis});
  const notice=await db.doc(`order_events/${buyerNoticeEventId(id,"ORDER_READY_FOR_PICKUP",2)}`).get();
  assert.equal(notice.exists,true);assert.equal(notice.get("buyerUid"),"PRIVATE_BUYER");
  const again=await send(f.manager.token,readyPath(f.store,id),{},"POST");
  assert.equal(again.status,200);assert.equal(again.body.outcome,"UNCHANGED");
  assert.equal(again.body.pickupCode,first.body.pickupCode);assert.equal(again.body.readyAtEpochMillis,first.body.readyAtEpochMillis);
  assert.deepEqual((await db.doc(`food_orders/${id}`).get()).data(),order);
  assert.equal((await db.collection("order_events").where("orderId","==",id).get()).size,2);
});
test("HU21-A: solicitudes simultáneas producen una sola transición, un historial y un aviso",async()=>{
  const f=await fixture(),id="74".repeat(32),send=connectedHttp(app);await cashPreparing(f,id);
  const results=await Promise.all([0,1,2].map(()=>send(f.manager.token,readyPath(f.store,id),{},"POST")));
  for(const r of results)assert.equal(r.status,200,JSON.stringify(r.body));
  assert.deepEqual(results.map(r=>r.body.outcome).sort(),["UNCHANGED","UNCHANGED","UPDATED"]);
  assert.equal(new Set(results.map(r=>r.body.pickupCode)).size,1);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("pickupCode"),results[0].body.pickupCode);
  assert.equal((await db.collection(`food_orders/${id}/status_history`).where("toStatus","==","READY_FOR_PICKUP").get()).size,1);
  assert.equal((await db.collection("order_events").where("orderId","==",id).get()).size,2);
});
test("HU21-A: estados de origen inválidos y pedidos inexistentes o ajenos se rechazan sin escribir",async()=>{
  const f=await fixture(),id="75".repeat(32),send=connectedHttp(app);await seedOrder(f.store,f.owner.uid,id);
  await db.doc(`food_orders/${id}`).update({paymentStatus:"PENDING"});
  for(const status of ["PENDING_CONFIRMATION","IN_DELIVERY","COMPLETED","CANCELLED","REJECTED"]) {
    await db.doc(`food_orders/${id}`).update({status});
    const r=await send(f.manager.token,readyPath(f.store,id),{},"POST");
    assert.equal(r.status,409,status);assert.equal(r.body.error,"ORDER_STATE_CHANGED");
  }
  await db.doc(`food_orders/${id}`).update({status:"READY_FOR_PICKUP",pickupCode:null});
  assert.equal((await send(f.manager.token,readyPath(f.store,id),{},"POST")).status,409);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("pickupCode"),null);
  assert.equal((await db.collection(`food_orders/${id}/status_history`).get()).size,0);
  assert.equal((await send(f.manager.token,readyPath(f.store,"76".repeat(32)),{},"POST")).status,404);
  assert.equal((await send(f.manager.token,readyPath(f.store,"no-valido"),{},"POST")).status,404);
  const foreign="77".repeat(32);await seedOrder(f.other,f.owner.uid,foreign);
  await db.doc(`food_orders/${foreign}`).update({status:"PREPARING",paymentStatus:"PENDING"});
  assert.equal((await send(f.manager.token,readyPath(f.store,foreign),{},"POST")).status,404);
  assert.equal((await db.doc(`food_orders/${foreign}`).get()).get("status"),"PREPARING");
});
test("HU21-A: datos históricos o de pago incoherentes no se marcan listos",async()=>{
  const f=await fixture(),send=connectedHttp(app);
  const cases:[string,Record<string,unknown>,number][]=[
    ["78".repeat(32),{paymentStatus:"PENDING",schemaVersion:2},404],
    ["79".repeat(32),{paymentStatus:"PENDING",paymentMethod:"CARD"},409],
    ["7a".repeat(32),{paymentStatus:"PAID"},409],
    ["7b".repeat(32),{paymentStatus:"PENDING",pickupCode:"1234"},409],
    ["7c".repeat(32),{paymentStatus:"PAID",paymentSimulation:true,pickupCode:"12a4"},409],
    ["7d".repeat(32),{},409]];
  for(const [id,patch,status] of cases) {
    await seedOrder(f.store,f.owner.uid,id);await db.doc(`food_orders/${id}`).update({status:"PREPARING",...patch});
    const r=await send(f.manager.token,readyPath(f.store,id),{},"POST");assert.equal(r.status,status,JSON.stringify(patch));
    assert.equal((await db.doc(`food_orders/${id}`).get()).get("status"),"PREPARING");
  }
  const legacy="7e".repeat(32);await seedOrder(f.store,f.owner.uid,legacy);
  await db.doc(`food_orders/${legacy}`).update({status:"PREPARING",paymentStatus:"PAID",paymentSimulation:true,pickupCode:"0042"});
  const r=await send(f.manager.token,readyPath(f.store,legacy),{},"POST");
  assert.equal(r.status,200);assert.equal(r.body.pickupCode,"0042");
});
test("HU21-A: la suspensión del negocio o del local no impide terminar pedidos aceptados",async()=>{
  const f=await fixture(),id="7f".repeat(32),other="70".repeat(32),send=connectedHttp(app);
  await cashPreparing(f,id);await cashPreparing(f,other);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  assert.equal((await send(f.manager.token,readyPath(f.store,id),{},"POST")).status,200);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"APPROVED"});
  await db.doc(`stores/${f.store}`).update({status:"SUSPENDED"});
  assert.equal((await send(f.manager.token,readyPath(f.store,other),{},"POST")).status,200);
});
test("Food simula recojo, llegada y efectivo solo al confirmar el cliente",async()=>{
  const f=await fixture(),id="fd".repeat(32),send=connectedHttp(app);
  await seedOrder(f.store,f.owner.uid,id);
  await db.doc(`food_orders/${id}`).update({buyerUid:f.buyer.uid,paymentStatus:"PENDING"});
  assert.equal((await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:20})).body.status,"PREPARING");
  const ready=await send(f.manager.token,`/stores/${f.store}/orders/${id}/ready-for-pickup`,{},"POST");
  assert.equal(ready.status,200);
  assert.equal((await db.doc(`food_orders/${id}`).get()).get("paymentStatus"),"PENDING");
  assert.equal((await send(f.buyer.token,`/me/orders/${id}/confirm-received`,{},"POST")).status,409);
  assert.equal((await send(f.manager.token,`/stores/${f.store}/orders/${id}/simulate-pickup`,{},"POST")).status,200);
  assert.equal((await page(f.manager,f.store,"?status=IN_DELIVERY")).body.orders[0].simulationStage,"ON_THE_WAY");
  assert.equal((await send(f.buyer.token,`/me/orders/${id}/confirm-received`,{},"POST")).status,409);
  assert.equal((await send(f.manager.token,`/stores/${f.store}/orders/${id}/simulate-arrival`,{},"POST")).status,200);
  assert.equal((await send(f.buyer.token,`/me/orders/${id}/confirm-received`,{},"POST")).status,200);
  assert.equal((await send(f.buyer.token,`/me/orders/${id}/confirm-received`,{},"POST")).body.outcome,"UNCHANGED");
  const completed=(await db.doc(`food_orders/${id}`).get()).data()!;
  assert.equal(completed.status,"COMPLETED");assert.equal(completed.paymentStatus,"PAID");
  assert.equal(completed.fulfillmentSimulation.outcome,"SUCCESS");
  assert.equal((await db.doc(`order_events/${buyerNoticeEventId(id,"ORDER_PICKED_UP",1)}`).get()).get("buyerUid"),f.buyer.uid);
  assert.equal((await db.collection(`food_orders/${id}/payment_history`).get()).size,1);
});
test("suspension keeps read and rejection but blocks acceptance",async()=>{
  const f=await fixture(),acceptId="ce".repeat(32),rejectId="df".repeat(32);
  await seedOrder(f.store,f.owner.uid,acceptId);await seedOrder(f.store,f.owner.uid,rejectId);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  assert.equal((await page(f.manager,f.store)).status,200);
  const accept=await decide(f.manager,f.store,acceptId,{action:"ACCEPT",estimatedPreparationMinutes:20});
  assert.equal(accept.status,423);assert.equal(accept.body.error,"ACCEPTANCE_SUSPENDED");
  const reject=await decide(f.manager,f.store,rejectId,{action:"REJECT",reason:"STORE_BUSY"});
  assert.equal(reject.status,200);assert.equal(reject.body.status,"REJECTED");
  assert.equal((await db.doc(`food_orders/${acceptId}`).get()).get("status"),"PENDING_CONFIRMATION");
});
test("cancelled or already answered orders cannot be decided again",async()=>{
  const f=await fixture(),id="e0".repeat(32);await seedOrder(f.store,f.owner.uid,id);
  await db.doc(`food_orders/${id}`).update({status:"CANCELLED"});
  assert.equal((await decide(f.manager,f.store,id,{action:"ACCEPT",estimatedPreparationMinutes:20})).status,409);
  assert.equal((await decide(f.manager,f.store,id,{action:"REJECT",reason:"STORE_BUSY"})).status,409);
});
test("owner and a manager of another local cannot decide; nothing is written",async()=>{
  const f=await fixture(),id="c1".repeat(32);await seedOrder(f.other,f.owner.uid,id);await seedOrder(f.store,f.owner.uid,"c2".repeat(32));
  const before=(await db.doc(`food_orders/${id}`).get()).data();
  for(const [a,store,orderId] of [[f.owner,f.other,id],[f.owner,f.store,"c2".repeat(32)],[f.manager,f.other,id]] as const)
    for(const body of [{action:"ACCEPT",estimatedPreparationMinutes:20},{action:"REJECT",reason:"STORE_BUSY"}]) {
      const r=await decide(a,store,orderId,body);assert.equal(r.status,403,`${a.uid} ${store}`);assert.equal(r.body.status,undefined);
    }
  assert.deepEqual((await db.doc(`food_orders/${id}`).get()).data(),before);
  assert.equal((await db.doc(`food_orders/${"c2".repeat(32)}`).get()).get("status"),"PENDING_CONFIRMATION");
  for(const orderId of [id,"c2".repeat(32)]) {
    assert.equal((await db.doc(`food_orders/${orderId}/status_history/manager_decision`).get()).exists,false);
    assert.equal((await db.collection("order_events").where("orderId","==",orderId).get()).size,0);
  }
});
test("a repeated rejection is idempotent and a rejection after acceptance conflicts",async()=>{
  const f=await fixture(),rejected="c3".repeat(32),accepted="c4".repeat(32);
  await seedOrder(f.store,f.owner.uid,rejected);await seedOrder(f.store,f.owner.uid,accepted);
  const body={action:"REJECT",reason:"OTHER",explanation:"Sin gas en cocina"};
  const first=await decide(f.manager,f.store,rejected,body),repeated=await decide(f.manager,f.store,rejected,body);
  assert.equal(first.status,200);assert.equal(repeated.status,200);assert.equal(repeated.body.status,"REJECTED");
  const updatedAt=(await db.doc(`food_orders/${rejected}`).get()).get("updatedAt");
  assert.equal((await db.collection(`food_orders/${rejected}/status_history`).get()).size,1);
  assert.equal((await db.collection("order_events").where("orderId","==",rejected).get()).size,1);
  const changed=await decide(f.manager,f.store,rejected,{action:"REJECT",reason:"STORE_BUSY"});
  assert.equal(changed.status,409);assert.equal(changed.body.error,"ORDER_STATE_CHANGED");
  assert.ok((await db.doc(`food_orders/${rejected}`).get()).get("updatedAt").isEqual(updatedAt));
  assert.equal((await decide(f.manager,f.store,accepted,{action:"ACCEPT",estimatedPreparationMinutes:15})).status,200);
  const late=await decide(f.manager,f.store,accepted,{action:"REJECT",reason:"STORE_BUSY"});
  assert.equal(late.status,409);assert.equal(late.body.error,"ORDER_STATE_CHANGED");
  const order=(await db.doc(`food_orders/${accepted}`).get()).data()!;
  assert.equal(order.status,"PREPARING");assert.equal(order.rejectionReason,undefined);
  assert.equal((await db.collection("order_events").where("orderId","==",accepted).get()).size,1);
});
test("a local that is not ACTIVE blocks acceptance but still allows rejection",async()=>{
  const f=await fixture(),acceptId="c5".repeat(32),rejectId="c6".repeat(32);
  await seedOrder(f.store,f.owner.uid,acceptId);await seedOrder(f.store,f.owner.uid,rejectId);
  await db.doc(`stores/${f.store}`).update({status:"SUSPENDED"});
  const accept=await decide(f.manager,f.store,acceptId,{action:"ACCEPT",estimatedPreparationMinutes:180});
  assert.equal(accept.status,423);assert.equal(accept.body.error,"ACCEPTANCE_SUSPENDED");
  assert.equal((await db.doc(`food_orders/${acceptId}`).get()).get("status"),"PENDING_CONFIRMATION");
  assert.equal((await db.collection("order_events").where("orderId","==",acceptId).get()).size,0);
  const reject=await decide(f.manager,f.store,rejectId,{action:"REJECT",reason:"CLOSING_SOON"});
  assert.equal(reject.status,200);assert.equal(reject.body.status,"REJECTED");
});
test("foreign local, owner, buyer and administrator gain no automatic inbox permission",async()=>{
  const f=await fixture();await seedOrder(f.other,f.owner.uid,"b".repeat(64));
  for(const [a,store] of [[f.manager,f.other],[f.owner,f.store],[f.buyer,f.store],[admin,f.store],[f.buyer,"missing"]] as const) {
    const r=await page(a,store);assert.equal(r.status,403);assert.equal(r.body.orders,undefined);
  }
});
test("suspended business/local and paused or closed operation do not revoke a manager's read scope",async()=>{
  const f=await fixture();await seedOrder(f.store,f.owner.uid,"c".repeat(64));
  for(const [business,status,operational] of [["SUSPENDED","SUSPENDED","PAUSED"],["SUSPENDED","INACTIVE","CLOSED"],["APPROVED","ACTIVE","PAUSED"]]) {
    await db.doc(`business_requests/${f.owner.uid}`).update({status:business});
    await db.doc(`stores/${f.store}`).update({status,operationalStatus:operational});
    assert.equal((await page(f.manager,f.store)).body.orders.length,1);
  }
});
test("revocation denies the next HTTP request including a previously valid cursor",async()=>{
  const f=await fixture();for(const d of ["d","e"])await seedOrder(f.store,f.owner.uid,d.repeat(64));
  const first=await page(f.manager,f.store,"?pageSize=1");assert.equal(first.status,200);
  await access.execute(f.owner,{action:"REVOKE",operationId:`revoke-${++n}`,businessId:f.owner.uid,email:f.manager.email,expectedRevision:f.revision});
  for(const suffix of ["",`?cursor=${first.body.nextCursor}`]){const r=await page(f.manager,f.store,suffix);assert.equal(r.status,403);assert.equal(r.body.orders,undefined);}
});
test("pending email assignment is not authentication or an automatically verified binding",async()=>{
  const f=await fixture(false);await seedOrder(f.store,f.owner.uid,"f".repeat(64));
  assert.equal((await page(f.manager,f.store)).status,403);
  await auth.updateUser(f.manager.uid,{emailVerified:true});const verified=await login(f.manager.uid);
  assert.equal((await page(verified,f.store)).status,403);
  await access.execute(verified,{action:"BIND",operationId:`bind-${++n}`,expectedRevision:f.revision});
  assert.equal((await page(verified,f.store)).status,200);
});
test("an invitation with no Auth account may receive orders but cannot read the inbox",async()=>{
  const f=await fixture(),email=`pending-${++n}@example.test`;
  await prepare({email});await access.execute(f.owner,{action:"ASSIGN",operationId:`pending-${++n}`,businessId:f.owner.uid,
    email,expectedRevision:1,storeIds:[f.other],fullName:"Invitado",phone:"000"});
  assert.equal(await db.runTransaction(tx=>access.hasReceivingManager(tx,f.owner.uid,f.other),{readOnly:true}),true);
  assert.equal((await page(undefined,f.other)).status,401);
});
test("empty inbox and deleted authorized store are distinct without leaking existence to outsiders",async()=>{
  const f=await fixture();const empty=await page(f.manager,f.store);assert.equal(empty.status,200);assert.deepEqual(empty.body.orders,[]);
  await db.doc(`stores/${f.store}`).delete();assert.equal((await page(f.manager,f.store)).status,404);
  assert.equal((await page(f.buyer,f.store)).status,403);
});
test("keyset pagination is deterministic for equal timestamps and ignores new earlier rows until refresh",async()=>{
  const f=await fixture();const ids=["1","2","3","4","5"].map(d=>d.repeat(64));
  for(const id of ids)await seedOrder(f.store,f.owner.uid,id);
  let r=await page(f.manager,f.store,"?pageSize=2");assert.deepEqual(r.body.orders.map((o:any)=>o.orderId),[ids[4],ids[3]]);
  await seedOrder(f.store,f.owner.uid,"9".repeat(64),Timestamp.fromMillis(1800000001000));
  await seedOrder(f.store,f.owner.uid,"6".repeat(64)); // New row tied in time but before the cursor, visible on refresh.
  const all=[...r.body.orders];while(r.body.nextCursor){r=await page(f.manager,f.store,`?pageSize=2&cursor=${r.body.nextCursor}`);assert.equal(r.status,200);all.push(...r.body.orders);}
  assert.deepEqual(all.map(o=>o.orderId),[...ids].reverse());assert.equal(new Set(all.map(o=>o.orderId)).size,5);
  assert.equal((await page(f.manager,f.store)).body.orders[0].orderId,"9".repeat(64));
});
test("sub-millisecond timestamp ordering survives cursor paging and API projects integer milliseconds",async()=>{
  const f=await fixture();await seedOrder(f.store,f.owner.uid,"0".repeat(64),new Timestamp(1800000000,2000));
  await seedOrder(f.store,f.owner.uid,"8".repeat(64),new Timestamp(1800000000,1000));
  const first=await page(f.manager,f.store,"?pageSize=1");assert.equal(first.body.orders[0].orderId,"0".repeat(64));
  assert.equal(Number.isInteger(first.body.orders[0].createdAtEpochMillis),true);
  assert.equal((await page(f.manager,f.store,`?pageSize=1&cursor=${first.body.nextCursor}`)).body.orders[0].orderId,"8".repeat(64));
});
test("tampered foreign, absent or cross-user anchors and query injection return no order data",async()=>{
  const f=await fixture();await seedOrder(f.other,f.owner.uid,"6".repeat(64));
  const cursors=[encodeInboxCursor(f.manager.uid,f.store,"6".repeat(64)),encodeInboxCursor(f.manager.uid,f.store,"7".repeat(64)),
    encodeInboxCursor(f.buyer.uid,f.store,"6".repeat(64)),encodeInboxCursor(f.manager.uid,f.other,"6".repeat(64)),"bad!"];
  for(const cursor of cursors){const r=await page(f.manager,f.store,`?cursor=${encodeURIComponent(cursor)}`);assert.equal(r.status,400);assert.equal(r.body.orders,undefined);}
  for(const query of ["?pageSize=0","?pageSize=51","?pageSize=1&pageSize=2","?status=OTHER","?storeId=foreign"])
    assert.equal((await page(f.manager,f.store,query)).status,400);
});
test("store moved to another business is denied and inconsistent snapshots fail closed",async()=>{
  const f=await fixture();await seedOrder(f.store,f.owner.uid,"7".repeat(64));
  await db.doc(`food_orders/${"7".repeat(64)}`).update({totalMinor:2301});
  const bad=await page(f.manager,f.store);assert.equal(bad.status,503);assert.equal(bad.body.orders,undefined);
  await db.doc(`stores/${f.store}`).update({businessId:"foreign"});assert.equal((await page(f.manager,f.store)).status,403);
});
test("concurrent revocation yields a coherent earlier snapshot or denial; subsequent reads always deny",async()=>{
  const f=await fixture();await seedOrder(f.store,f.owner.uid,"a1".repeat(32));
  const reader=new StoreOrdersReader(db,auth);
  const [read]=await Promise.allSettled([reader.page(f.manager,f.store,{}),access.execute(f.owner,
    {action:"REVOKE",operationId:`parallel-${++n}`,businessId:f.owner.uid,email:f.manager.email,expectedRevision:f.revision})]);
  if(read.status==="fulfilled"){assert.equal(read.value.storeId,f.store);assert.equal(JSON.stringify(read.value).includes("PRIVATE"),false);}
  assert.equal((await page(f.manager,f.store)).status,403);
});
test("HTTP requires a valid token and direct client Firestore reads remain denied",async()=>{
  const f=await fixture();await seedOrder(f.store,f.owner.uid,"b1".repeat(32));
  assert.equal((await page(undefined,f.store)).status,401);
  assert.equal((await page({...f.manager,token:"invalid"},f.store)).status,401);
  const response=await fetch(`http://127.0.0.1:8089/v1/projects/${project}/databases/(default)/documents/food_orders/${"b1".repeat(32)}`,
    {headers:{Authorization:`Bearer ${f.manager.token}`}});assert.equal(response.status,403);
});


test("CLOUD inbox keeps scoped current bindings, privacy and read access during suspension",async()=>{
  const f=await fixture(),send=connectedHttp(app);await seedOrder(f.store,f.owner.uid,"d".repeat(64));
  assert.equal((await send(undefined,`/stores/${f.store}/orders`)).status,401);
  for(const actor of [f.owner,f.buyer,admin])assert.equal((await send(actor.token,`/stores/${f.store}/orders`)).status,403);
  assert.equal((await send(f.manager.token,`/stores/${f.other}/orders`)).status,403);
  await db.doc(`business_requests/${f.owner.uid}`).update({status:"SUSPENDED"});
  const result=await send(f.manager.token,`/stores/${f.store}/orders`);
  assert.equal(result.status,200);assert.equal(result.body.orders.length,1);assert.ok(!JSON.stringify(result.body).includes("PRIVATE"));
  await auth.updateUser(f.manager.uid,{disabled:true});
  assert.equal((await send(f.manager.token,`/stores/${f.store}/orders`)).status,401);
});
