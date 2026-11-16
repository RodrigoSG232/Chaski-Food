import { connectedHttp } from "./connectedHandler.fixture";
import { before,after,test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp,deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore,Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../src/access/commercialAccessService";
import { CommercialIdentity } from "../src/auth/verifyBuyerToken";

process.env.FIRESTORE_EMULATOR_HOST="127.0.0.1:8089";
process.env.FIREBASE_AUTH_EMULATOR_HOST="127.0.0.1:9099";
const app=initializeApp({projectId:"demo-chaski-hu15"},"hu28-dashboard-tests"),db=getFirestore(app),auth=getAuth(app);
const access=new CommercialAccessService(db,auth),http=connectedHttp(app);
type Actor=CommercialIdentity&{token:string};
const prefix=`dashboard-${Date.now()}`;let sequence=0;let admin:Actor;
async function actor(isAdmin=false):Promise<Actor> {
  const uid=`${prefix}-${++sequence}`;
  await auth.createUser({uid,email:`${uid}@example.test`,password:"emulator-password",emailVerified:true});
  if(isAdmin)await auth.setCustomUserClaims(uid,{admin:true});
  const user=await auth.getUser(uid);
  const response=await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key",{
    method:"POST",headers:{"Content-Type":"application/json"},
    body:JSON.stringify({email:user.email,password:"emulator-password",returnSecureToken:true})});
  assert.equal(response.status,200);
  return {uid,email:user.email!,emailVerified:true,admin:isAdmin,token:(await response.json() as {idToken:string}).idToken};
}
async function prepare(who:Actor) {
  await access.execute(admin,{action:"PREPARE",operationId:`prepare-${++sequence}`,
    email:who.email,expectedRevision:0});
}
async function order(businessId:string,storeId:string,id:string,success:boolean) {
  const now=Date.now(),created=Timestamp.fromMillis(now-3_600_000),completed=Timestamp.fromMillis(now-1_000);
  await db.doc(`food_orders/${id}`).set({schemaVersion:1,orderId:id,businessId,storeId,createdAt:created,
    updatedAt:completed,completedAt:completed,acceptedAt:created,status:"COMPLETED",currency:"PEN",
    subtotalMinor:success?2400:9100,riderIntegration:{deliveryOutcome:success?"SUCCESS":"UNSUCCESSFUL"},
    ...(success?{paymentStatus:"PAID"}:{}),
    buyerSnapshot:{phone:"PRIVATE"}});
}
before(async()=>{admin=await actor(true);await prepare(admin);});
after(async()=>{await db.terminate();await deleteApp(app);});

test("owner sees own business; manager only assigned store; admin claim grants nothing",async()=>{
  const owner=await actor(),manager=await actor(),buyer=await actor();
  const ownStore=`${owner.uid}-own`,otherStore=`${owner.uid}-other`;
  await db.doc(`business_requests/${owner.uid}`).set({ownerUid:owner.uid,status:"APPROVED",businessName:"Ficticio"});
  await db.doc(`stores/${ownStore}`).set({businessId:owner.uid,name:"Local A"});
  await db.doc(`stores/${otherStore}`).set({businessId:owner.uid,name:"Local B"});
  await prepare(owner);await prepare(manager);await prepare(buyer);
  const assignment=await access.execute(owner,{action:"ASSIGN",operationId:`assign-${++sequence}`,businessId:owner.uid,
    email:manager.email,expectedRevision:1,storeIds:[ownStore],fullName:"Responsable",phone:"000"});
  await order(owner.uid,ownStore,`${prefix}-order-one`,true);
  await order(owner.uid,otherStore,`${prefix}-order-two`,true);
  await order(owner.uid,otherStore,`${prefix}-failed`,false);
  const route=`/commercial/dashboard?businessId=${owner.uid}&days=7`;
  const owned=await http(owner.token,route),assigned=await http(manager.token,route);
  assert.equal(owned.status,200);assert.equal(owned.body.orders,3);assert.equal(owned.body.salesMinor,4800);
  assert.equal(assigned.status,200);assert.equal(assigned.body.orders,1);assert.equal(assigned.body.salesMinor,2400);
  assert.deepEqual(assigned.body.stores.map((s:{storeId:string})=>s.storeId),[ownStore]);
  assert.equal(JSON.stringify(assigned.body).includes("PRIVATE"),false);
  assert.equal((await http(manager.token,`${route}&storeId=${otherStore}`)).status,403);
  assert.equal((await http(owner.token,`/commercial/dashboard?businessId=${buyer.uid}&days=7`)).status,403);
  assert.equal((await http(buyer.token,route)).status,403);
  assert.equal((await http(admin.token,route)).status,403);
  assert.equal((await http(undefined,route)).status,401);
  assert.equal((await http(owner.token,`${route}&days=31`)).status,400);
  await access.execute(owner,{action:"REVOKE",operationId:`revoke-${++sequence}`,businessId:owner.uid,
    email:manager.email,expectedRevision:assignment.revision});
  assert.equal((await http(manager.token,route)).status,403);
  await db.doc(`business_requests/${owner.uid}`).update({status:"SUSPENDED"});
  assert.equal((await http(owner.token,route)).status,200);
});

test("E4: a delivered order under collection review is not a sale and does not break the dashboard; corrupt data still does",async()=>{
  const owner=await actor();const store=`${owner.uid}-review`;
  await db.doc(`business_requests/${owner.uid}`).set({ownerUid:owner.uid,status:"APPROVED",businessName:"Ficticio"});
  await db.doc(`stores/${store}`).set({businessId:owner.uid,name:"Local revisión"});
  await prepare(owner);
  await order(owner.uid,store,`${prefix}-paid`,true);
  const review=`${prefix}-review`;await order(owner.uid,store,review,true);
  await db.doc(`food_orders/${review}`).update({paymentStatus:"PENDING",collectionReview:{status:"OPEN",expectedMinor:2400,declaredMinor:2000}});
  const route=`/commercial/dashboard?businessId=${owner.uid}&days=7`;
  const ok=await http(owner.token,route);
  assert.equal(ok.status,200);assert.equal(ok.body.orders,2);assert.equal(ok.body.salesMinor,2400);
  await db.doc(`food_orders/${review}`).update({collectionReview:{status:"CLOSED"}});
  assert.equal((await http(owner.token,route)).status,503);
});

test("E5.4a: reconciled orders count once by subtotal; NOT_FULLY_COLLECTED and excess never add sales",async()=>{
  const owner=await actor();const store=`${owner.uid}-reconciled`;
  await db.doc(`business_requests/${owner.uid}`).set({ownerUid:owner.uid,status:"APPROVED",businessName:"Ficticio"});
  await db.doc(`stores/${store}`).set({businessId:owner.uid,name:"Local conciliación"});
  await prepare(owner);
  const resolved=(resolution:Record<string,unknown>)=>({status:"RESOLVED",expectedMinor:2700,declaredMinor:1000,resolution});
  const full=`${prefix}-full`,short=`${prefix}-short`,excess=`${prefix}-excess`;
  for(const id of [full,short,excess])await order(owner.uid,store,id,true);
  await db.doc(`food_orders/${full}`).update({paymentCollection:{source:"RECONCILED"},collectionReview:resolved({outcome:"FULLY_COLLECTED",excessMinor:0})});
  await db.doc(`food_orders/${short}`).update({paymentStatus:"PENDING",collectionReview:resolved({outcome:"NOT_FULLY_COLLECTED",shortfallMinor:1700})});
  await db.doc(`food_orders/${excess}`).update({paymentCollection:{source:"RECONCILED"},
    collectionReview:resolved({outcome:"FULLY_COLLECTED",excessMinor:300,excessStatus:"PENDING_REFUND"})});
  const route=`/commercial/dashboard?businessId=${owner.uid}&days=7`,ok=await http(owner.token,route);
  assert.equal(ok.status,200);assert.equal(ok.body.salesMinor,2*2400);
  await db.doc(`food_orders/${short}`).update({"collectionReview.resolution.outcome":"FULLY_COLLECTED"}); // PENDING but "fully": corrupt
  assert.equal((await http(owner.token,route)).status,503);
});
