import { Firestore, Timestamp } from "firebase-admin/firestore";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";

function nextTimestamp(order: Record<string, any>): Timestamp {
  const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
  return Timestamp.fromMillis(Math.max(Date.now(), previous + 1));
}

/** Only the buyer may cancel, and only while the live order is awaiting confirmation. */
export class OrderCancellationService {
  constructor(private readonly db: Firestore) {}

  async buyerCancel(uid: string, rawId: string) {
    const id = buyerOrderId(rawId);
    if (!id) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${id}`);
      const order = (await tx.get(ref)).data();
      if (!order || order.buyerUid !== uid || order.orderId !== id || order.schemaVersion !== 1)
        throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (order.status === "CANCELLED" && order.cancelledByUid === uid)
        return { contractVersion: 1, outcome: "CANCELLED", status: "CANCELLED" };
      if (order.status !== "PENDING_CONFIRMATION") throw new OrderHttpError("CANCELLATION_NOT_ALLOWED", 409);
      // Delivery is not implemented yet. Never silently cancel an order linked to logistics.
      if (order.deliveryId != null) throw new OrderHttpError("LOGISTICS_CANCELLATION_REQUIRED", 409);
      const at = nextTimestamp(order);
      tx.update(ref, { status: "CANCELLED", updatedAt: at, cancelledAt: at, cancelledByUid: uid });
      tx.create(ref.collection("status_history").doc("buyer_cancelled"),
        { schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "CANCELLED", actorUid: uid, occurredAt: at });
      return { contractVersion: 1, outcome: "CANCELLED", status: "CANCELLED" };
    });
  }
}
