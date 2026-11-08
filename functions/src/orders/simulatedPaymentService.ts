import { randomInt } from "node:crypto";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";
import { simulatedPaymentDecision, simulatedPickupCode } from "./simulatedPaymentPolicy";

export class SimulatedPaymentService {
  constructor(private readonly db: Firestore, private readonly now: () => number = Date.now,
    private readonly nextCode: () => number = () => randomInt(0, 10_000)) {}

  async pay(uid: string, rawId: string) {
    const id = buyerOrderId(rawId);
    if (!id) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${id}`);
      const order = (await tx.get(ref)).data();
      if (!order || order.buyerUid !== uid || order.orderId !== id || order.schemaVersion !== 1)
        throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      const decision = simulatedPaymentDecision(order);
      if (decision === "ALREADY_PAID") return { contractVersion: 1, outcome: "PAID", paymentStatus: "PAID" };
      if (decision === "NOT_ALLOWED") throw new OrderHttpError("SIMULATED_PAYMENT_NOT_ALLOWED", 409);
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const code = simulatedPickupCode(this.nextCode());
      tx.update(ref, { paymentStatus: "PAID", paymentSimulation: true, paidAt: at,
        pickupCode: code, updatedAt: at });
      tx.create(ref.collection("payment_history").doc("simulated_payment"), { schemaVersion: 1,
        type: "SIMULATED_PAYMENT", status: "PAID", actorUid: uid, occurredAt: at,
        amountMinor: order.totalMinor, currency: order.currency });
      return { contractVersion: 1, outcome: "PAID", paymentStatus: "PAID" };
    });
  }
}
