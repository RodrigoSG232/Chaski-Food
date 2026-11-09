import { Timestamp, Transaction, Firestore } from "firebase-admin/firestore";

export const BUYER_NOTICE_TYPES = [
  "ORDER_ACCEPTED", "ORDER_REJECTED", "ORDER_READY_FOR_PICKUP", "RIDER_ASSIGNED",
  "ORDER_PICKED_UP", "ORDER_DELIVERED", "ORDER_DELIVERY_FAILED",
] as const;
export type BuyerNoticeType = typeof BUYER_NOTICE_TYPES[number];

/** The outbox ID is stable across transaction retries; dispatch accepts only these known transitions. */
export function buyerNoticeEventId(orderId: string, type: BuyerNoticeType, sequence: number): string {
  if (!/^[0-9a-f]{64}$/.test(orderId) || !Number.isSafeInteger(sequence) || sequence < 1)
    throw new Error("INVALID_BUYER_NOTICE_ID");
  return `buyer_notice_${orderId}_${type.toLowerCase()}_${sequence}`;
}

export function createBuyerNoticeEvent(tx: Transaction, db: Firestore, data: {
  orderId: string; buyerUid: string; type: BuyerNoticeType; sequence: number; at: Timestamp;
  assignmentId?: string; assignmentVersion?: number;
}): void {
  const eventId = buyerNoticeEventId(data.orderId, data.type, data.sequence);
  tx.create(db.doc(`order_events/${eventId}`), {
    schemaVersion: 1, type: data.type, orderId: data.orderId, buyerUid: data.buyerUid,
    eventSequence: data.sequence, assignmentId: data.assignmentId ?? null,
    assignmentVersion: data.assignmentVersion ?? null, status: "PENDING", attemptCount: 0,
    nextAttemptAt: data.at, createdAt: data.at,
  });
}
