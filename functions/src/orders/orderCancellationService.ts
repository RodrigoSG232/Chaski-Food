import { Firestore, Timestamp } from "firebase-admin/firestore";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";

function nextTimestamp(order: Record<string, any>): Timestamp {
  const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
  return Timestamp.fromMillis(Math.max(Date.now(), previous + 1));
}

export type BuyerCancellationDecision = "CANCEL" | "ALREADY_CANCELLED" | "NOT_ALLOWED" | "LOGISTICS_REQUIRED";

/** HU18: la cancelación directa termina al salir de PENDING_CONFIRMATION. */
export function buyerCancellationDecision(order: Record<string, any>, uid: string): BuyerCancellationDecision {
  if (order.status === "CANCELLED" && order.cancelledByUid === uid) return "ALREADY_CANCELLED";
  if (order.status !== "PENDING_CONFIRMATION") return "NOT_ALLOWED";
  if (order.deliveryId != null) return "LOGISTICS_REQUIRED";
  return "CANCEL";
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
      const decision = buyerCancellationDecision(order, uid);
      if (decision === "ALREADY_CANCELLED")
        return { contractVersion: 1, outcome: "CANCELLED", status: "CANCELLED" };
      if (decision === "NOT_ALLOWED") throw new OrderHttpError("CANCELLATION_NOT_ALLOWED", 409);
      // Delivery se implementa en HU22; nunca se cancela silenciosamente una logística ya vinculada.
      if (decision === "LOGISTICS_REQUIRED") throw new OrderHttpError("LOGISTICS_CANCELLATION_REQUIRED", 409);
      const at = nextTimestamp(order);
      tx.update(ref, { status: "CANCELLED", updatedAt: at, cancelledAt: at, cancelledByUid: uid });
      tx.create(ref.collection("status_history").doc("buyer_cancelled"),
        { schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "CANCELLED", actorUid: uid, occurredAt: at });
      return { contractVersion: 1, outcome: "CANCELLED", status: "CANCELLED" };
    });
  }
}
