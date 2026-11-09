import { Firestore,Timestamp } from "firebase-admin/firestore";
import { OrderHttpError } from "../orders/orderRequest";
import { payloadHash,projectRiderOrder,RiderEvent,riderOrderId,RiderTracking } from "./riderIntegrationPolicy";
import { BuyerNoticeType,createBuyerNoticeEvent } from "../notifications/buyerNoticeEvent";

export class RiderIntegrationService{
  constructor(private readonly db:Firestore,private readonly now:()=>number=Date.now){}

  async available(limit=20){
    if(!Number.isInteger(limit)||limit<1||limit>50)throw new OrderHttpError("INVALID_REQUEST",400);
    const snapshot=await this.db.collection("food_orders").where("status","==","READY_FOR_PICKUP").limit(limit).get();
    const orders=[];
    for(const document of snapshot.docs){
      try{const order=projectRiderOrder(document.data());if(!order.dispatch.assignment)orders.push(order);}
      catch(error){if(!(error instanceof OrderHttpError)||error.code!=="ORDER_NOT_READY")throw error;}
    }
    return {contractVersion:1,orders};
  }

  async detail(rawOrderId:string){
    const orderId=riderOrderId(rawOrderId);if(!orderId)throw new OrderHttpError("ORDER_NOT_FOUND",404);
    const data=(await this.db.doc(`food_orders/${orderId}`).get()).data();
    if(!data)throw new OrderHttpError("ORDER_NOT_FOUND",404);
    return {contractVersion:1,order:projectRiderOrder(data)};
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
      if(order.paymentStatus!=="PAID"||order.paymentSimulation!==true||typeof order.pickupCode!=="string")
        throw new OrderHttpError("ORDER_NOT_READY",409);
      const integration=order.riderIntegration??{lastEventSequence:0,lastAssignmentVersion:0,status:"SEARCHING_RIDER",assignment:null,rider:null};
      if(event.eventSequence!==integration.lastEventSequence+1)throw new OrderHttpError("EVENT_SEQUENCE_CONFLICT",409);
      const assignment=integration.assignment as {id:string;version:number}|null;
      const sameAssignment=assignment?.id===event.assignmentId&&assignment.version===event.assignmentVersion;
      let nextStatus=order.status,nextAssignment=assignment,nextRider=integration.rider,nextDeliveryStatus=event.status;
      if(event.eventType==="ASSIGNMENT_ACCEPTED"){
        if(order.status!=="READY_FOR_PICKUP"||assignment!==null||event.assignmentVersion!==(integration.lastAssignmentVersion??0)+1)
          throw new OrderHttpError("ASSIGNMENT_CONFLICT",409);
        nextAssignment={id:event.assignmentId,version:event.assignmentVersion};nextRider=event.rider;
      }else{
        if(!sameAssignment)throw new OrderHttpError("ASSIGNMENT_STALE",409);
        if(event.eventType==="ASSIGNMENT_RELEASED"){
          if(order.status!=="READY_FOR_PICKUP")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);
          nextAssignment=null;nextRider=null;
        }else if(event.eventType==="PICKED_UP"){
          if(order.status!=="READY_FOR_PICKUP")throw new OrderHttpError("ORDER_STATE_CONFLICT",409);nextStatus="IN_DELIVERY";
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
