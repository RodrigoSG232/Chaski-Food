import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderDecision, orderCanBeResponded } from "./orderDecisionPolicy";
import { OrderHttpError } from "./orderRequest";

function responseForExisting(order: Record<string, any>, actorUid: string, decision: OrderDecision) {
  if (decision.action === "ACCEPT" && order.status === "PREPARING" && order.acceptedByUid === actorUid &&
      order.estimatedPreparationMinutes === decision.estimatedPreparationMinutes && order.estimatedReadyAt instanceof Timestamp)
    return { contractVersion: 1, outcome: "ACCEPTED", status: "PREPARING",
      estimatedReadyAtEpochMillis: order.estimatedReadyAt.toMillis() };
  if (decision.action === "REJECT" && order.status === "REJECTED" && order.rejectedByUid === actorUid &&
      order.rejectionReason === decision.reason && (order.rejectionExplanation ?? null) === decision.explanation)
    return { contractVersion: 1, outcome: "REJECTED", status: "REJECTED" };
  return null;
}

/** HU19: one atomic winner among buyer cancellation, manager acceptance and manager rejection. */
export class OrderDecisionService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now) {
    this.access = new CommercialAccessService(db, auth);
  }

  async respond(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string, decision: OrderDecision) {
    const storeId = validId(rawStoreId) ? rawStoreId : null;
    const orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const orderRef = this.db.doc(`food_orders/${orderId}`);
      const storeRef = this.db.doc(`stores/${storeId}`);
      const businessRef = this.db.doc(`business_requests/${scope.businessId}`);
      const [orderDoc, storeDoc, businessDoc] = await Promise.all([tx.get(orderRef), tx.get(storeRef), tx.get(businessRef)]);
      const order = orderDoc.data(), store = storeDoc.data(), business = businessDoc.data();
      if (!order || order.orderId !== orderId || order.schemaVersion !== 1 || order.storeId !== storeId ||
          order.businessId !== scope.businessId || !store || store.businessId !== scope.businessId ||
          !business || business.ownerUid !== scope.businessId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      const existing = responseForExisting(order, actor.uid, decision);
      if (existing) return existing;
      if (!orderCanBeResponded(order.status)) throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      if (decision.action === "ACCEPT" && (business.status !== "APPROVED" || store.status !== "ACTIVE"))
        throw new OrderHttpError("ACCEPTANCE_SUSPENDED", 423);
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const historyRef = orderRef.collection("status_history").doc("manager_decision");
      if (decision.action === "ACCEPT") {
        const readyAt = Timestamp.fromMillis(at.toMillis() + decision.estimatedPreparationMinutes * 60_000);
        tx.update(orderRef, { status: "PREPARING", acceptedAt: at, acceptedByUid: actor.uid,
          estimatedPreparationMinutes: decision.estimatedPreparationMinutes, estimatedReadyAt: readyAt, updatedAt: at });
        tx.create(historyRef, { schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "PREPARING",
          actorUid: actor.uid, occurredAt: at, estimatedReadyAt: readyAt });
        return { contractVersion: 1, outcome: "ACCEPTED", status: "PREPARING",
          estimatedReadyAtEpochMillis: readyAt.toMillis() };
      }
      tx.update(orderRef, { status: "REJECTED", rejectedAt: at, rejectedByUid: actor.uid,
        rejectionReason: decision.reason, rejectionExplanation: decision.explanation, updatedAt: at });
      tx.create(historyRef, { schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "REJECTED",
        actorUid: actor.uid, occurredAt: at, reason: decision.reason, explanation: decision.explanation });
      return { contractVersion: 1, outcome: "REJECTED", status: "REJECTED" };
    });
  }
}
