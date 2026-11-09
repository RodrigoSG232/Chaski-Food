import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { createBuyerNoticeEvent } from "../notifications/buyerNoticeEvent";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";
import { validatedOrderCore } from "./storeOrdersPolicy";

/** Temporary Food-only delivery actions. No Rider assignment or event is fabricated. */
export class FoodDeliverySimulationService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now) {
    this.access = new CommercialAccessService(db, auth);
  }

  async storeAction(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string,
    action: "pickup" | "arrival") {
    const storeId = validId(rawStoreId) ? rawStoreId : null;
    const orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const ref = this.db.doc(`food_orders/${orderId}`);
      const order = (await tx.get(ref)).data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId ||
          order.storeId !== storeId || order.businessId !== scope.businessId)
        throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      const simulation = order.fulfillmentSimulation;
      if (action === "pickup") {
        if (order.status === "IN_DELIVERY" && ["ON_THE_WAY","AT_CUSTOMER"].includes(simulation?.stage))
          return {contractVersion: 1, outcome: "UNCHANGED", status: "IN_DELIVERY"};
        if (order.status !== "READY_FOR_PICKUP" || order.paymentMethod !== "CASH_ON_DELIVERY" ||
            !(order.readyAt instanceof Timestamp) || !/^[0-9]{4}$/.test(order.pickupCode ?? "") ||
            order.riderIntegration?.assignment || simulation)
          throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
        const at = this.nextAt(order);
        tx.update(ref, {status: "IN_DELIVERY", fulfillmentSimulation: {stage: "ON_THE_WAY", pickedUpAt: at}, updatedAt: at});
        tx.create(ref.collection("status_history").doc("food_simulated_pickup"), {
          schemaVersion: 1, fromStatus: "READY_FOR_PICKUP", toStatus: "IN_DELIVERY", actorUid: actor.uid, occurredAt: at});
        createBuyerNoticeEvent(tx, this.db, {orderId, buyerUid: order.buyerUid, type: "ORDER_PICKED_UP", sequence: 1, at});
        return {contractVersion: 1, outcome: "UPDATED", status: "IN_DELIVERY"};
      }
      if (order.status === "IN_DELIVERY" && simulation?.stage === "AT_CUSTOMER")
        return {contractVersion: 1, outcome: "UNCHANGED", status: "IN_DELIVERY"};
      if (order.status !== "IN_DELIVERY" || simulation?.stage !== "ON_THE_WAY" ||
          !(simulation.pickedUpAt instanceof Timestamp) || order.riderIntegration?.assignment)
        throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      const at = this.nextAt(order);
      tx.update(ref, {"fulfillmentSimulation.stage": "AT_CUSTOMER", "fulfillmentSimulation.arrivedAt": at, updatedAt: at});
      tx.create(ref.collection("simulation_history").doc("arrival"), {
        schemaVersion: 1, type: "ARRIVED_AT_CUSTOMER", actorUid: actor.uid, occurredAt: at});
      return {contractVersion: 1, outcome: "UPDATED", status: "IN_DELIVERY"};
    });
  }

  async buyerConfirm(uid: string, rawOrderId: string) {
    const orderId = buyerOrderId(rawOrderId);
    if (!orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${orderId}`);
      const order = (await tx.get(ref)).data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId || order.buyerUid !== uid)
        throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (order.status === "COMPLETED" && order.fulfillmentSimulation?.outcome === "SUCCESS" &&
          order.paymentStatus === "PAID")
        return {contractVersion: 1, outcome: "UNCHANGED", status: "COMPLETED", paymentStatus: "PAID"};
      if (order.status !== "IN_DELIVERY" || order.fulfillmentSimulation?.stage !== "AT_CUSTOMER" ||
          !(order.fulfillmentSimulation.arrivedAt instanceof Timestamp) ||
          order.riderIntegration?.assignment || order.paymentMethod !== "CASH_ON_DELIVERY" ||
          !["PENDING", "PAID"].includes(order.paymentStatus) ||
          (order.paymentStatus === "PAID" && order.paymentSimulation !== true))
        throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      try { validatedOrderCore(orderId,order); }
      catch { throw new OrderHttpError("DATA_INCONSISTENT",503,true); }
      const at = this.nextAt(order);
      const payNow = order.paymentStatus === "PENDING";
      tx.update(ref, {status: "COMPLETED", completedAt: at, updatedAt: at,
        "fulfillmentSimulation.outcome": "SUCCESS", "fulfillmentSimulation.completedAt": at,
        ...(payNow ? {paymentStatus: "PAID", paymentSimulation: true, paidAt: at} : {})});
      tx.create(ref.collection("status_history").doc("food_simulated_delivery"), {
        schemaVersion: 1, fromStatus: "IN_DELIVERY", toStatus: "COMPLETED", actorUid: uid, occurredAt: at});
      if (payNow) tx.create(ref.collection("payment_history").doc("simulated_cash_on_delivery"), {
        schemaVersion: 1, type: "SIMULATED_CASH_ON_DELIVERY", status: "PAID", actorUid: uid,
        occurredAt: at, amountMinor: order.totalMinor, currency: order.currency});
      createBuyerNoticeEvent(tx, this.db, {orderId, buyerUid: uid, type: "ORDER_DELIVERED", sequence: 2, at});
      return {contractVersion: 1, outcome: "COMPLETED", status: "COMPLETED", paymentStatus: "PAID"};
    });
  }

  private nextAt(order: Record<string, any>): Timestamp {
    return Timestamp.fromMillis(Math.max(this.now(), order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() + 1 : 0));
  }
}
