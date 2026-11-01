import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { canUpdatePreparationEstimate } from "./preparationEstimatePolicy";
import { OrderHttpError } from "./orderRequest";

export class PreparationEstimateService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now) {
    this.access = new CommercialAccessService(db, auth);
  }
  async update(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string, minutes: number) {
    const storeId = validId(rawStoreId) ? rawStoreId : null, orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const ref = this.db.doc(`food_orders/${orderId}`), order = (await tx.get(ref)).data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId || order.storeId !== storeId ||
          order.businessId !== scope.businessId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (!canUpdatePreparationEstimate(order.status)) throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      if (order.estimatedPreparationMinutes === minutes && order.estimatedReadyAt instanceof Timestamp)
        return { contractVersion: 1, outcome: "UNCHANGED", status: "PREPARING",
          estimatedReadyAtEpochMillis: order.estimatedReadyAt.toMillis() };
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const readyAt = Timestamp.fromMillis(at.toMillis() + minutes * 60_000);
      const currentRevision = Number.isSafeInteger(order.estimateRevision) && order.estimateRevision >= 0 ? order.estimateRevision : 0;
      if (currentRevision >= Number.MAX_SAFE_INTEGER) throw new OrderHttpError("DATA_INCONSISTENT", 503);
      const revision = currentRevision + 1;
      tx.update(ref, { estimatedPreparationMinutes: minutes, estimatedReadyAt: readyAt, estimateRevision: revision,
        estimateUpdatedAt: at, estimateUpdatedByUid: actor.uid, updatedAt: at });
      tx.create(ref.collection("estimate_history").doc(String(revision).padStart(10, "0")),
        { schemaVersion: 1, revision, minutes, estimatedReadyAt: readyAt, actorUid: actor.uid, occurredAt: at });
      if (typeof order.deliveryId === "string" && order.deliveryId.length) {
        tx.create(this.db.doc(`rider_events/estimate_${orderId}_${revision}`), { schemaVersion: 1,
          type: "PREPARATION_ESTIMATE_UPDATED", orderId, deliveryId: order.deliveryId, businessId: scope.businessId,
          storeId, estimatedReadyAt: readyAt, status: "PENDING", createdAt: at, updatedAt: at });
      }
      return { contractVersion: 1, outcome: "UPDATED", status: "PREPARING",
        estimatedReadyAtEpochMillis: readyAt.toMillis() };
    });
  }
}
