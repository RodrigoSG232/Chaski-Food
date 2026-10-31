import { randomUUID } from "node:crypto";
import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp, DocumentData } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { CommercialAccessError, digest, validId } from "../access/commercialAccessPolicy";
import { orderEventIdFor } from "../orders/orderService";
import { NoticeSender } from "./fcmSender";

export const EVENT_LEASE_MS=180000, MAX_SEND_ATTEMPTS=5;
export class RetryOrderEvent extends Error {constructor(){super("ORDER_EVENT_RETRY_REQUIRED");}}
const terminal=new Set(["SENT","FAILED_PERMANENT","EXHAUSTED","NO_RECIPIENTS"]);
const backoff=[60000,300000,900000,900000,900000];
/** The SAME unacknowledged Firestore creation event is retried by Functions v2 retry:true.
 * Busy lease / future nextAttemptAt MUST throw, not acknowledge the event.
 * Retry delivery is bounded by the platform window; operational replay is needed after an extended outage.
 * No FCM operation is performed inside a Firestore transaction. */
export class OrderEventProcessor {
  constructor(private readonly db:Firestore,private readonly auth:Auth,private readonly sender:NoticeSender,
    private readonly now:()=>number=Date.now){}
  async process(eventId:string):Promise<void>{
    if(!/^new_order_[0-9a-f]{64}$/.test(eventId))return;
    const ref=this.db.doc(`order_events/${eventId}`),leaseId=randomUUID();
    const claim=await this.db.runTransaction(async tx=>{
      const event=(await tx.get(ref)).data();
      if(!event||terminal.has(event.status))return null;
      const time=this.now();
      if(event.status==="PROCESSING" && event.leaseUntil instanceof Timestamp && event.leaseUntil.toMillis()>time)throw new RetryOrderEvent();
      if(event.nextAttemptAt instanceof Timestamp && event.nextAttemptAt.toMillis()>time)throw new RetryOrderEvent();
      if(!Number.isInteger(event.attemptCount)||event.attemptCount<0||event.attemptCount>=MAX_SEND_ATTEMPTS){
        tx.update(ref,{status:"EXHAUSTED",lastError:"ATTEMPTS_EXHAUSTED",updatedAt:Timestamp.fromMillis(time),leaseId:null});return null;
      }
      if(event.schemaVersion!==1||event.type!=="NEW_ORDER"||!validId(event.storeId)||!validId(event.businessId)||
        typeof event.orderId!=="string"||!/^[0-9a-f]{64}$/.test(event.orderId)||eventId!==orderEventIdFor(event.orderId)){
        tx.update(ref,{status:"FAILED_PERMANENT",lastError:"EVENT_INVALID",updatedAt:Timestamp.fromMillis(time)});return null;
      }
      const order=(await tx.get(this.db.doc(`food_orders/${event.orderId}`))).data();
      if(!order||order.orderId!==event.orderId||order.storeId!==event.storeId||order.businessId!==event.businessId){
        tx.update(ref,{status:"FAILED_PERMANENT",lastError:"ORDER_MISMATCH",updatedAt:Timestamp.fromMillis(time)});return null;
      }
      tx.update(ref,{status:"PROCESSING",leaseId,leaseUntil:Timestamp.fromMillis(time+EVENT_LEASE_MS),
        attemptCount:event.attemptCount+1,lastAttemptAt:Timestamp.fromMillis(time),updatedAt:Timestamp.fromMillis(time)});
      return event;
    });
    if(!claim)return;
    try {
      const rows=await this.db.collection("store_managers").where("businessId","==",claim.businessId).get();
      const uids=[...new Set(rows.docs.filter(d=>d.data().bindingState==="VERIFIED" && d.data().assignedStoreIds?.includes(claim.storeId))
        .map(d=>d.data().boundUid).filter((uid):uid is string=>typeof uid==="string" && validId(uid)))];
      let accepted=0,permanent=0,invalid=0,eligibleDevices=0;
      for(const uid of uids){
        const devices=await this.db.collection("notification_devices").where("uid","==",uid).get();
        for(const device of devices.docs){
          const data=device.data();
          if(!data.enabled||typeof data.token!=="string"||device.id!==digest(data.token))continue;
          // Progress is operational, not an authority or a permanent recipient list.
          const progress=ref.collection("deliveries").doc(digest([uid,device.id]));
          if(["ACCEPTED","PERMANENT_FAILURE","INVALID_TOKEN"].includes((await progress.get()).data()?.status))continue;
          if(!await this.authorized(uid,claim,device.id,data,leaseId,eventId))continue;
          eligibleDevices++;
          const result=await this.sender.send(data.token,{type:"NEW_ORDER",eventId,orderId:claim.orderId,storeId:claim.storeId,
            businessId:claim.businessId,recipientUid:uid});
          await this.db.runTransaction(async tx=>{
            const current=(await tx.get(ref)).data();const currentDevice=(await tx.get(device.ref)).data();
            if(current?.leaseId!==leaseId)throw new RetryOrderEvent();
            tx.set(progress,{status:result,updatedAt:Timestamp.fromMillis(this.now())});
            if(result==="INVALID_TOKEN" && currentDevice?.uid===uid && currentDevice.token===data.token)tx.delete(device.ref);
          });
          if(result==="ACCEPTED")accepted++;else if(result==="INVALID_TOKEN")invalid++;else permanent++;
        }
      }
      const successful=await ref.collection("deliveries").where("status","==","ACCEPTED").limit(1).get();
      await this.finish(eventId,leaseId,{status:!successful.empty?"SENT":permanent?"FAILED_PERMANENT":"NO_RECIPIENTS",
        lastError:permanent?"TRANSPORT_PERMANENT":invalid?"INVALID_TOKENS":eligibleDevices===0?"NO_AUTHORIZED_DEVICES":null,
        acceptedCount:accepted});
    }catch(error){
      // A lease conflict is never acknowledged; the platform must redeliver.
      if(error instanceof RetryOrderEvent)throw error;
      await this.db.runTransaction(async tx=>{
        const event=(await tx.get(ref)).data();if(event?.leaseId!==leaseId)throw new RetryOrderEvent();
        const exhausted=event.attemptCount>=MAX_SEND_ATTEMPTS;
        tx.update(ref,{status:exhausted?"EXHAUSTED":"RETRY",nextAttemptAt:Timestamp.fromMillis(this.now()+backoff[event.attemptCount-1]),
          leaseId:null,leaseUntil:null,lastError:"PROCESSING_TRANSIENT",updatedAt:Timestamp.fromMillis(this.now())});
      });
      if((await ref.get()).data()?.status!=="EXHAUSTED")throw new RetryOrderEvent();
    }
  }
  private async authorized(uid:string,event:DocumentData,deviceId:string,expected:DocumentData,leaseId:string,eventId:string):Promise<boolean>{
    let actor;
    try {const user=await this.auth.getUser(uid);if(user.disabled||!user.email||!user.emailVerified)return false;
      const validAfter=Date.parse(user.tokensValidAfterTime??"");
      if(!(expected.authenticatedAt instanceof Timestamp)||
        (Number.isFinite(validAfter) && expected.authenticatedAt.toMillis()<validAfter))return false;
      actor={uid,email:user.email.toLowerCase().trim(),emailVerified:true,admin:user.customClaims?.admin===true};
    }catch(error){if(["auth/user-not-found","auth/user-disabled"].includes((error as {code?:string}).code??""))return false;throw error;}
    return this.db.runTransaction(async tx=>{
      const eventState=(await tx.get(this.db.doc(`order_events/${eventId}`))).data();
      if(eventState?.leaseId!==leaseId||eventState.leaseUntil.toMillis()<=this.now())throw new RetryOrderEvent();
      const device=(await tx.get(this.db.doc(`notification_devices/${deviceId}`))).data();
      if(!device?.enabled||device.uid!==uid||device.token!==expected.token)return false;
      try {const scope=await new CommercialAccessService(this.db,this.auth).requireStoreOrderReader(tx,actor,event.storeId);
        return scope.businessId===event.businessId;
      }catch(error){if(error instanceof CommercialAccessError && error.status===403)return false;throw error;}
    },{readOnly:true});
  }
  private async finish(id:string,leaseId:string,fields:DocumentData){
    await this.db.runTransaction(async tx=>{const ref=this.db.doc(`order_events/${id}`),event=(await tx.get(ref)).data();
      if(event?.leaseId!==leaseId)throw new RetryOrderEvent();
      tx.update(ref,{...fields,leaseId:null,leaseUntil:null,completedAt:Timestamp.fromMillis(this.now()),updatedAt:Timestamp.fromMillis(this.now())});});
  }
}
