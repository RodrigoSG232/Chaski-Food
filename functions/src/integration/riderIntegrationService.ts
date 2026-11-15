import { FieldPath,Firestore,Timestamp } from "firebase-admin/firestore";
import { OrderHttpError } from "../orders/orderRequest";
import { encodeRiderOrdersCursor,payloadHash,projectRiderOrder,RIDER_PAGE_MAX,RIDER_SCAN_BATCH,RIDER_SCAN_MAX,RiderOrdersCursor,RiderEvent,riderEventAllowedForPayment,riderOrderId,riderPaymentEligibility,RiderTracking } from "./riderIntegrationPolicy";
import { BuyerNoticeType,createBuyerNoticeEvent } from "../notifications/buyerNoticeEvent";
import { PickupSecurity,readPickupSecurity,storedPickupSecurity,verificationIsCurrent,withoutVerification } from "../orders/pickupSecurityPolicy";

export class RiderIntegrationService{
  constructor(private readonly db:Firestore,private readonly now:()=>number=Date.now){}

  /** E1.3: READY_FOR_PICKUP ordered by readyAt then document id; eligibility is applied before filling the page.
   * Examines at most RIDER_SCAN_MAX documents; nextCursor always points to the LAST EXAMINED one (may follow an empty page). */
  async available(limit=20,cursor:RiderOrdersCursor|null=null){
    if(!Number.isInteger(limit)||limit<1||limit>RIDER_PAGE_MAX)throw new OrderHttpError("INVALID_REQUEST",400);
    // The range bound keeps only real Timestamps: documents without readyAt (or with another type) are not listed.
    const base=this.db.collection("food_orders").where("status","==","READY_FOR_PICKUP")
      .where("readyAt",">=",Timestamp.fromMillis(0)).orderBy("readyAt").orderBy(FieldPath.documentId());
    const orders=[];let last=cursor,examined=0,exhausted=false;
    while(orders.length<limit&&examined<RIDER_SCAN_MAX){
      const requested=Math.min(RIDER_SCAN_BATCH,RIDER_SCAN_MAX-examined);
      const query=last?base.startAfter(new Timestamp(last.seconds,last.nanoseconds),last.orderId):base;
      const batch=await query.limit(requested).get();
      let consumed=0;
      for(const document of batch.docs){
        consumed++;examined++;
        const readyAt=document.get("readyAt") as Timestamp;
        last={seconds:readyAt.seconds,nanoseconds:readyAt.nanoseconds,orderId:document.id};
        try{
          const data=document.data(),order=projectRiderOrder(data,"LIST");
          // Offers only: assigned or pickup-locked orders are reconciled through the detail endpoint.
          if(!order.dispatch.assignment&&!readPickupSecurity(data.pickupSecurity).lockedAt)orders.push(order);
        }
        catch(error){if(!(error instanceof OrderHttpError)||error.code!=="ORDER_NOT_READY")throw error;}
        if(orders.length===limit)break;
      }
      // A short batch fully consumed means nothing remains after the last examined document.
      if(consumed===batch.size&&batch.size<requested){exhausted=true;break;}
    }
    return {contractVersion:1,orders,nextCursor:exhausted||!last?null:encodeRiderOrdersCursor(last)};
  }

  async detail(rawOrderId:string){
    const orderId=riderOrderId(rawOrderId);if(!orderId)throw new OrderHttpError("ORDER_NOT_FOUND",404);
    const data=(await this.db.doc(`food_orders/${orderId}`).get()).data();
    if(!data)throw new OrderHttpError("ORDER_NOT_FOUND",404);
    return {contractVersion:1,order:projectRiderOrder(data,"DETAIL")};
  }

  async event(event:RiderEvent,rawPayload:unknown){
    const hash=payloadHash(rawPayload),receivedAt=Timestamp.fromMillis(this.now());
    return this.db.runTransaction(async tx=>{
      const orderRef=this.db.doc(`food_orders/${event.orderId}`),eventRef=orderRef.collection("rider_events").doc(event.eventId);
      const [orderSnapshot,knownSnapshot]=await Promise.all([tx.get(orderRef),tx.get(eventRef)]);
      const order=orderSnapshot.data(),known=knownSnapshot.data();
      if(known){if(known.payloadHash!==hash)throw new OrderHttpError("IDEMPOTENCY_CONFLICT",409);
        return {contractVersion:1,eventId:event.eventId,orderId:event.orderId,applied:true,lastEventSequence:known.eventSequence};}
      if(!order||order.schemaVersion!==1)throw new OrderHttpError("ORDER_NOT_FOUND",404);
      const eligibility=riderPaymentEligibility(order);
      if(eligibility===null||!riderEventAllowedForPayment(eligibility,event.eventType))
        throw new OrderHttpError("ORDER_NOT_READY",409);
      const integration=order.riderIntegration??{lastEventSequence:0,lastAssignmentVersion:0,status:"SEARCHING_RIDER",assignment:null,rider:null};
      if(event.eventSequence!==integration.lastEventSequence+1)throw new OrderHttpError("EVENT_SEQUENCE_CONFLICT",409);
      const assignment=integration.assignment as {id:string;version:number}|null;
      const sameAssignment=assignment?.id===event.assignmentId&&assignment.version===event.assignmentVersion;
      let nextStatus=order.status,nextAssignment=assignment,nextRider=integration.rider,nextDeliveryStatus=event.status;
      const security=readPickupSecurity(order.pickupSecurity);
      let nextSecurity:PickupSecurity|null=null;
      if(event.eventType==="ASSIGNMENT_ACCEPTED"){
        if(order.status!=="READY_FOR_PICKUP"||assignment!==null||event.assignmentVersion!==(integration.lastAssignmentVersion??0)+1)
          throw new OrderHttpError("ASSIGNMENT_CONFLICT",409);
        if(security.lockedAt)throw new OrderHttpError("PICKUP_LOCKED",409);
        nextAssignment={id:event.assignmentId,version:event.assignmentVersion};nextRider=event.rider;
      }else{
        if(!sameAssignment)throw new OrderHttpError("ASSIGNMENT_STALE",409);
        if(event.eventType==="ASSIGNMENT_RELEASED"){
          if(order.status!=="READY_FOR_PICKUP")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);
          nextAssignment=null;nextRider=null;
          if(security.verification)nextSecurity=withoutVerification(security);
        }else if(event.eventType==="PICKED_UP"){
          if(order.status!=="READY_FOR_PICKUP")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);
          // E2: the store must have verified the code for this exact assignment before any physical pickup is accepted.
          if(!verificationIsCurrent(security,{id:event.assignmentId,version:event.assignmentVersion}))
            throw new OrderHttpError("PICKUP_NOT_VERIFIED",409);
          nextStatus="IN_DELIVERY";
        }else if(event.eventType==="ARRIVED_AT_MERCHANT"){
          if(order.status!=="READY_FOR_PICKUP")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);
        }else if(event.eventType==="ARRIVED_AT_CUSTOMER"){
          if(order.status!=="IN_DELIVERY")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);
        }else if(event.eventType.startsWith("COMPLETED_")){
          if(order.status!=="IN_DELIVERY")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);nextStatus="COMPLETED";
        }
      }
      const at=Timestamp.fromMillis(Math.max(receivedAt.toMillis(),order.updatedAt instanceof Timestamp?order.updatedAt.toMillis()+1:0));
      const nextIntegration={lastEventSequence:event.eventSequence,lastAssignmentVersion:event.eventType==="ASSIGNMENT_ACCEPTED"?
        event.assignmentVersion:(integration.lastAssignmentVersion??event.assignmentVersion),status:nextDeliveryStatus,assignment:nextAssignment,rider:nextRider,
        deliveryOutcome:event.deliveryOutcome,reason:event.reason,updatedAt:at};
      const update:Record<string,unknown>={riderIntegration:nextIntegration,updatedAt:at};
      if(nextSecurity)update.pickupSecurity=storedPickupSecurity(nextSecurity);
      if(nextStatus!==order.status)update.status=nextStatus;
      if(nextStatus==="COMPLETED")update.completedAt=at;
      tx.update(orderRef,update as Record<string,any>);
      tx.create(eventRef,{schemaVersion:1,...event,payloadHash:hash,receivedAt});
      if(nextStatus!==order.status)tx.create(orderRef.collection("status_history").doc(`rider_${event.eventSequence}`),{
        schemaVersion:1,fromStatus:order.status,toStatus:nextStatus,actorUid:"CHASKI_RIDER",occurredAt:at});
      const noticeType:Partial<Record<RiderEvent["eventType"],BuyerNoticeType>>={
        ASSIGNMENT_ACCEPTED:"RIDER_ASSIGNED",PICKED_UP:"ORDER_PICKED_UP",
        COMPLETED_SUCCESSFULLY:"ORDER_DELIVERED",COMPLETED_UNSUCCESSFULLY:"ORDER_DELIVERY_FAILED"};
      const type=noticeType[event.eventType];
      if(type)createBuyerNoticeEvent(tx,this.db,{orderId:event.orderId,buyerUid:order.buyerUid,type,
        sequence:event.eventSequence,at,assignmentId:event.assignmentId,assignmentVersion:event.assignmentVersion});
      return {contractVersion:1,eventId:event.eventId,orderId:event.orderId,applied:true,lastEventSequence:event.eventSequence};
    });
  }

  async tracking(rawOrderId:string,tracking:RiderTracking,rawPayload:unknown){
    const orderId=riderOrderId(rawOrderId);if(!orderId)throw new OrderHttpError("ORDER_NOT_FOUND",404);
    const hash=payloadHash(rawPayload),receivedAt=Timestamp.fromMillis(this.now());
    return this.db.runTransaction(async tx=>{
      const ref=this.db.doc(`food_orders/${orderId}`),snapshot=await tx.get(ref),order=snapshot.data();
      if(!order)throw new OrderHttpError("ORDER_NOT_FOUND",404);
      const integration=order.riderIntegration,assignment=integration?.assignment;
      if(order.status!=="IN_DELIVERY"||assignment?.id!==tracking.assignmentId||assignment.version!==tracking.assignmentVersion||
        integration.rider?.id!==tracking.riderId)throw new OrderHttpError("ASSIGNMENT_STALE",409);
      const current=order.riderTracking;
      if(tracking.recordedAt.toMillis()>receivedAt.toMillis()+60_000)throw new OrderHttpError("INVALID_REQUEST",422);
      if(current&&tracking.sequence<current.sequence)return {contractVersion:1,orderId,assignmentVersion:tracking.assignmentVersion,
        acceptedSequence:current.sequence,received:false};
      if(current&&tracking.sequence===current.sequence){
        if(current.payloadHash!==hash)throw new OrderHttpError("IDEMPOTENCY_CONFLICT",409);
        return {contractVersion:1,orderId,assignmentVersion:tracking.assignmentVersion,acceptedSequence:tracking.sequence,received:true};
      }
      if(current && tracking.recordedAt.toMillis()<current.recordedAt.toMillis())
        return {contractVersion:1,orderId,assignmentVersion:tracking.assignmentVersion,acceptedSequence:current.sequence,received:false};
      tx.update(ref,{riderTracking:{...tracking,payloadHash:hash,receivedAt}});
      return {contractVersion:1,orderId,assignmentVersion:tracking.assignmentVersion,acceptedSequence:tracking.sequence,received:true};
    });
  }
}
