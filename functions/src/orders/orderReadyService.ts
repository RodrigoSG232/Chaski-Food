import { randomInt } from "node:crypto";
import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";
import { orderCanBeMarkedReady } from "./orderReadyPolicy";
import { createBuyerNoticeEvent } from "../notifications/buyerNoticeEvent";

export class OrderReadyService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now,
    private readonly nextCode: () => number = () => randomInt(0, 10_000)) {
    this.access = new CommercialAccessService(db, auth);
  }

  async markReady(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string) {
    const storeId = validId(rawStoreId) ? rawStoreId : null;
    const orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const orderRef = this.db.doc(`food_orders/${orderId}`);
      const order = (await tx.get(orderRef)).data();
      if (!order || order.orderId !== orderId || order.schemaVersion !== 1 || order.storeId !== storeId ||
          order.businessId !== scope.businessId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (order.status === "READY_FOR_PICKUP" && typeof order.pickupCode === "string" &&
          /^[0-9]{4}$/.test(order.pickupCode) && order.readyAt instanceof Timestamp) {
        return { contractVersion: 1, outcome: "UNCHANGED", status: "READY_FOR_PICKUP",
          pickupCode: order.pickupCode, readyAtEpochMillis: order.readyAt.toMillis() };
      }
      if (!orderCanBeMarkedReady(order.status)) throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      const legacyPaid=order.paymentStatus==="PAID"&&order.paymentSimulation===true&&
        typeof order.pickupCode==="string"&&/^[0-9]{4}$/.test(order.pickupCode);
      if(order.paymentMethod!=="CASH_ON_DELIVERY" ||
          !(legacyPaid || (order.paymentStatus==="PENDING"&&order.pickupCode==null)))
        throw new OrderHttpError("ORDER_STATE_CHANGED",409);
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const code = legacyPaid ? order.pickupCode : this.nextCode().toString().padStart(4,"0");
      if(!/^[0-9]{4}$/.test(code))throw new OrderHttpError("READY_UPDATE_UNAVAILABLE",503,true);
      tx.update(orderRef, { status: "READY_FOR_PICKUP", readyAt: at,
        readyByUid: actor.uid, pickupCode:code, updatedAt: at });
      tx.create(orderRef.collection("status_history").doc("ready_for_pickup"), { schemaVersion: 1,
        fromStatus: "PREPARING", toStatus: "READY_FOR_PICKUP", actorUid: actor.uid, occurredAt: at });
      createBuyerNoticeEvent(tx, this.db, {orderId, buyerUid: order.buyerUid, type:"ORDER_READY_FOR_PICKUP", sequence:2, at});
      return { contractVersion: 1, outcome: "UPDATED", status: "READY_FOR_PICKUP",
        pickupCode: code, readyAtEpochMillis: at.toMillis() };
    });
  }
}
