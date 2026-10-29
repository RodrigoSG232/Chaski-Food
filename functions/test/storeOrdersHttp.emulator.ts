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
