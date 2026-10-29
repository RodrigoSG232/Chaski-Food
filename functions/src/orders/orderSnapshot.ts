import { Timestamp } from "firebase-admin/firestore";
import { OrderIntent, OrderReceipt, parseAcceptedPurchase } from "./orderModels";
import { requestHash } from "./orderPolicy";
import { inconsistent } from "./orderValidation";

export function orderDocument(intent: OrderIntent, orderId: string, now: Date) {
  const a=intent.accepted, q=a.deliveryQuote;
  return { schemaVersion:1, orderId, intentId:intent.intentId, requestHash:requestHash(intent), buyerUid:a.ownerUid,
    businessId:a.businessId,storeId:a.storeId,status:"PENDING_CONFIRMATION",createdAt:Timestamp.fromDate(now),updatedAt:Timestamp.fromDate(now),
    buyerSnapshot:{uid:a.ownerUid,displayName:a.buyerName,phone:a.buyerPhone},
    businessSnapshot:{id:a.businessId,name:a.businessName},storeSnapshot:{id:a.storeId,name:a.storeName,phone:a.storePhone},
    pickupSnapshot:a.pickup,dropoffSnapshot:{sourceAddressId:a.delivery.addressId,label:a.delivery.label,...a.delivery.destination},
    items:a.items.map(i=>({...i,lineTotalMinor:Number(BigInt(i.quantity)*BigInt(i.unitPriceMinor))})),
    ...a.amounts,currency:a.currency,paymentMethod:a.paymentMethod,paymentStatus:"PENDING",
    quoteSnapshot:{quoteId:q.quoteId,distanceMeters:q.distanceMeters,tariffVersion:q.tariffVersion,
      quotedAt:Timestamp.fromMillis(q.quotedAtEpochMillis),expiresAt:Timestamp.fromMillis(q.expiresAtEpochMillis)} };
}
/** Creation receipt, not live order/payment state. Initial history is checked by recovery. */
export function receiptFromDocument(data: Record<string, any> | undefined, uid: string, id: string, hash: string): OrderReceipt {
  if(!data || data.schemaVersion!==1 || data.buyerUid!==uid || data.intentId!==id || data.requestHash!==hash ||
    !(data.createdAt instanceof Timestamp))return inconsistent();
  try {
    const d=data.dropoffSnapshot,q=data.quoteSnapshot;
    if(!(q.quotedAt instanceof Timestamp) || !(q.expiresAt instanceof Timestamp))return inconsistent();
    const accepted=parseAcceptedPurchase({ownerUid:uid,businessId:data.businessId,storeId:data.storeId,
      businessName:data.businessSnapshot.name,storeName:data.storeSnapshot.name,storePhone:data.storeSnapshot.phone,
      buyerName:data.buyerSnapshot.displayName,buyerPhone:data.buyerSnapshot.phone,pickup:data.pickupSnapshot,
      delivery:{addressId:d.sourceAddressId,label:d.label,destination:{addressText:d.addressText,location:d.location,reference:d.reference,instructions:d.instructions}},
      items:data.items.map((i:Record<string,any>)=>{
        if(BigInt(i.quantity)*BigInt(i.unitPriceMinor)!==BigInt(i.lineTotalMinor))return inconsistent();
        return {productId:i.productId,categoryId:i.categoryId,name:i.name,quantity:i.quantity,unitPriceMinor:i.unitPriceMinor};
      }),amounts:{subtotalMinor:data.subtotalMinor,deliveryFeeMinor:data.deliveryFeeMinor,totalMinor:data.totalMinor},
      currency:data.currency,paymentMethod:data.paymentMethod,deliveryQuote:{quoteId:q.quoteId,distanceMeters:q.distanceMeters,
        tariffVersion:q.tariffVersion,quotedAtEpochMillis:q.quotedAt.toMillis(),expiresAtEpochMillis:q.expiresAt.toMillis()}});
    if(requestHash({intentId:id,accepted})!==hash || data.buyerSnapshot.uid!==uid || data.businessSnapshot.id!==accepted.businessId ||
      data.storeSnapshot.id!==accepted.storeId)return inconsistent();
    return {orderId:data.orderId,intentId:id,accepted,createdAtEpochMillis:data.createdAt.toMillis(),status:"PENDING_CONFIRMATION"};
  }catch{return inconsistent();}
}
