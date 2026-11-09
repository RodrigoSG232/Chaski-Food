import { randomUUID } from "node:crypto";
import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { digest } from "../access/commercialAccessPolicy";
import { buyerNoticeEventId, BUYER_NOTICE_TYPES, BuyerNoticeType } from "./buyerNoticeEvent";
import { NoticeSender } from "./fcmSender";
import { EVENT_LEASE_MS, MAX_SEND_ATTEMPTS, RetryOrderEvent } from "./orderEventProcessor";

const terminal = new Set(["SENT", "FAILED_PERMANENT", "EXHAUSTED", "NO_RECIPIENTS"]);
const backoff = [60_000, 300_000, 900_000, 900_000, 900_000];

/** Dispatches buyer hints. The order and device are rechecked before every FCM call. */
export class BuyerOrderEventProcessor {
  constructor(private readonly db: Firestore, private readonly auth: Auth, private readonly sender: NoticeSender,
    private readonly now: () => number = Date.now) {}

  async process(eventId: string): Promise<void> {
    if (!/^buyer_notice_[0-9a-f]{64}_[a-z_]+_[1-9][0-9]*$/.test(eventId)) return;
    const ref = this.db.doc(`order_events/${eventId}`), leaseId = randomUUID();
    const claim = await this.db.runTransaction(async tx => {
      const event = (await tx.get(ref)).data();
      if (!event || terminal.has(event.status)) return null;
      const time = this.now();
      if (event.status === "PROCESSING" && event.leaseUntil instanceof Timestamp && event.leaseUntil.toMillis() > time)
        throw new RetryOrderEvent();
      if (event.nextAttemptAt instanceof Timestamp && event.nextAttemptAt.toMillis() > time) throw new RetryOrderEvent();
      if (!Number.isInteger(event.attemptCount) || event.attemptCount < 0 || event.attemptCount >= MAX_SEND_ATTEMPTS) {
        tx.update(ref, {status:"EXHAUSTED",lastError:"ATTEMPTS_EXHAUSTED",updatedAt:Timestamp.fromMillis(time),leaseId:null});
        return null;
      }
      const validType = BUYER_NOTICE_TYPES.includes(event.type as BuyerNoticeType);
      if (event.schemaVersion !== 1 || !validType || typeof event.orderId !== "string" ||
          !/^[0-9a-f]{64}$/.test(event.orderId) || typeof event.buyerUid !== "string" || !event.buyerUid ||
          !Number.isSafeInteger(event.eventSequence) || event.eventSequence < 1 ||
          eventId !== buyerNoticeEventId(event.orderId, event.type, event.eventSequence)) {
        tx.update(ref, {status:"FAILED_PERMANENT",lastError:"EVENT_INVALID",updatedAt:Timestamp.fromMillis(time)});
        return null;
      }
      const order = (await tx.get(this.db.doc(`food_orders/${event.orderId}`))).data();
      if (!order || order.orderId !== event.orderId || order.buyerUid !== event.buyerUid) {
        tx.update(ref, {status:"FAILED_PERMANENT",lastError:"ORDER_CHANGED",updatedAt:Timestamp.fromMillis(time)});
        return null;
      }
      tx.update(ref, {status:"PROCESSING",leaseId,leaseUntil:Timestamp.fromMillis(time+EVENT_LEASE_MS),
        attemptCount:event.attemptCount+1,lastAttemptAt:Timestamp.fromMillis(time),updatedAt:Timestamp.fromMillis(time)});
      return {type:event.type as BuyerNoticeType,orderId:event.orderId as string,buyerUid:event.buyerUid as string};
    });
    if (!claim) return;
    try {
      let account;
      try {account = await this.auth.getUser(claim.buyerUid);}
      catch (error) {
        if (["auth/user-not-found","auth/user-disabled"].includes((error as {code?:string}).code ?? "")) {
          await this.finish(eventId,leaseId,{status:"NO_RECIPIENTS",lastError:"ACCOUNT_UNAVAILABLE",acceptedCount:0});
          return;
        }
        throw error;
      }
      if (account.disabled) {
        await this.finish(eventId,leaseId,{status:"NO_RECIPIENTS",lastError:"ACCOUNT_UNAVAILABLE",acceptedCount:0});
        return;
      }
      const validAfter = Date.parse(account.tokensValidAfterTime ?? "");
      const devices = await this.db.collection("notification_devices").where("uid","==",claim.buyerUid).get();
      let accepted = 0, permanent = 0, invalid = 0, eligible = 0;
      for (const device of devices.docs) {
        const data = device.data();
        if (!data.enabled || typeof data.token !== "string" || device.id !== digest(data.token) ||
            !(data.authenticatedAt instanceof Timestamp) ||
            (Number.isFinite(validAfter) && data.authenticatedAt.toMillis() < validAfter)) continue;
        const progress = ref.collection("deliveries").doc(digest([claim.buyerUid,device.id]));
        if (["ACCEPTED","PERMANENT_FAILURE","INVALID_TOKEN"].includes((await progress.get()).data()?.status)) continue;
        // Auth can change during a multi-device attempt; recheck it immediately before each send.
        let freshAccount;
        try { freshAccount = await this.auth.getUser(claim.buyerUid); }
        catch (error) {
          if (["auth/user-not-found","auth/user-disabled"].includes((error as {code?:string}).code ?? "")) break;
          throw error;
        }
        if (freshAccount.disabled) break;
        const freshValidAfter = Date.parse(freshAccount.tokensValidAfterTime ?? "");
        if (Number.isFinite(freshValidAfter) && data.authenticatedAt.toMillis() < freshValidAfter) continue;
        const authorized = await this.db.runTransaction(async tx => {
          const [state,order,registered] = await Promise.all([
            tx.get(ref),tx.get(this.db.doc(`food_orders/${claim.orderId}`)),tx.get(device.ref)]);
          if (state.data()?.leaseId !== leaseId || state.data()?.leaseUntil.toMillis() <= this.now())
            throw new RetryOrderEvent();
          return order.data()?.buyerUid === claim.buyerUid &&
            registered.data()?.enabled === true && registered.data()?.uid === claim.buyerUid &&
            registered.data()?.token === data.token;
        },{readOnly:true});
        if (!authorized) continue;
        eligible++;
        const result = await this.sender.send(data.token,{type:claim.type,eventId,orderId:claim.orderId,recipientUid:claim.buyerUid});
        await this.db.runTransaction(async tx => {
          const [state,registered] = await Promise.all([tx.get(ref),tx.get(device.ref)]);
          if (state.data()?.leaseId !== leaseId) throw new RetryOrderEvent();
          tx.set(progress,{status:result,updatedAt:Timestamp.fromMillis(this.now())});
          if (result === "INVALID_TOKEN" && registered.data()?.uid === claim.buyerUid &&
              registered.data()?.token === data.token) tx.delete(device.ref);
        });
        if (result === "ACCEPTED") accepted++; else if (result === "INVALID_TOKEN") invalid++; else permanent++;
      }
      const successful = await ref.collection("deliveries").where("status","==","ACCEPTED").limit(1).get();
      await this.finish(eventId,leaseId,{status:!successful.empty?"SENT":permanent?"FAILED_PERMANENT":"NO_RECIPIENTS",
        lastError:permanent?"TRANSPORT_PERMANENT":invalid?"INVALID_TOKENS":eligible===0?"NO_AUTHORIZED_DEVICES":null,
        acceptedCount:accepted});
    } catch(error) {
      if (error instanceof RetryOrderEvent) throw error;
      await this.db.runTransaction(async tx => {
        const event=(await tx.get(ref)).data();
        if (event?.leaseId!==leaseId) throw new RetryOrderEvent();
        const exhausted=event.attemptCount>=MAX_SEND_ATTEMPTS;
        tx.update(ref,{status:exhausted?"EXHAUSTED":"RETRY",
          nextAttemptAt:Timestamp.fromMillis(this.now()+backoff[event.attemptCount-1]),
          leaseId:null,leaseUntil:null,lastError:"PROCESSING_TRANSIENT",updatedAt:Timestamp.fromMillis(this.now())});
      });
      if ((await ref.get()).data()?.status !== "EXHAUSTED") throw new RetryOrderEvent();
    }
  }

  private async finish(eventId:string,leaseId:string,fields:Record<string,unknown>) {
    await this.db.runTransaction(async tx => {
      const ref=this.db.doc(`order_events/${eventId}`),event=(await tx.get(ref)).data();
      if(event?.leaseId!==leaseId)throw new RetryOrderEvent();
      tx.update(ref,{...fields,leaseId:null,leaseUntil:null,
        completedAt:Timestamp.fromMillis(this.now()),updatedAt:Timestamp.fromMillis(this.now())});
    });
  }
}
