import { randomInt } from "node:crypto";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";
import { requireFoodAdmin } from "./adminAccess";
import { pickupCode } from "./orderReadyPolicy";
import { PickupUnlockRequest, readPickupSecurity, storedPickupSecurity, unlockedSecurity } from "./pickupSecurityPolicy";

const MAX_INCIDENTS = 50;

/**
 * E2.4 — narrow exception to HU34-28: the `admin` claim may ONLY list locked pickups (minimal data) and unlock them.
 * It grants no other order access. Unlocking regenerates the code, opens a new cycle and keeps every previous attempt.
 */
export class PickupIncidentService {
  constructor(private readonly db: Firestore, private readonly now: () => number = Date.now,
    private readonly nextCode: () => number = () => randomInt(0, 10_000)) {}

  async list(actor: CommercialIdentity) {
    requireFoodAdmin(actor);
    const snapshot = await this.db.collection("food_orders").where("pickupSecurity.lockedAt", ">=", Timestamp.fromMillis(0))
      .orderBy("pickupSecurity.lockedAt").limit(MAX_INCIDENTS + 1).get();
    const incidents = snapshot.docs.slice(0, MAX_INCIDENTS).map(doc => {
      const data = doc.data(), security = readPickupSecurity(data.pickupSecurity);
      return { orderId: doc.id, shortId: doc.id.slice(0, 8), storeId: data.storeId,
        storeName: typeof data.storeSnapshot?.name === "string" ? data.storeSnapshot.name : null, status: data.status,
        lockedAt: security.lockedAt!.toDate().toISOString(), cycle: security.cycle, failures: security.failures,
        hasAssignment: !!data.riderIntegration?.assignment };
    });
    return { contractVersion: 1, incidents, truncated: snapshot.size > MAX_INCIDENTS };
  }

  async unlock(actor: CommercialIdentity, rawOrderId: string, request: PickupUnlockRequest) {
    requireFoodAdmin(actor);
    const orderId = buyerOrderId(rawOrderId);
    if (!orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${orderId}`), auditRef = ref.collection("pickup_unlocks").doc(request.operationId);
      const [snapshot, known] = await Promise.all([tx.get(ref), tx.get(auditRef)]);
      const order = snapshot.data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (known.exists) {
        const stored = known.data()!;
        if (stored.actorUid !== actor.uid || stored.reason !== request.reason) throw new OrderHttpError("IDEMPOTENCY_CONFLICT", 409);
        return { contractVersion: 1, outcome: "UNLOCKED", cycle: stored.cycleAfter, replayed: true };
      }
      const security = readPickupSecurity(order.pickupSecurity);
      if (!security.lockedAt) throw new OrderHttpError("PICKUP_NOT_LOCKED", 409);
      if (order.status !== "READY_FOR_PICKUP") throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      let code = pickupCode(this.nextCode());
      for (let tries = 0; code === order.pickupCode && tries < 5; tries++) code = pickupCode(this.nextCode());
      if (code === order.pickupCode) throw new OrderHttpError("UNLOCK_UNAVAILABLE", 503, true);
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1)), next = unlockedSecurity(security);
      tx.update(ref, { pickupCode: code, pickupSecurity: storedPickupSecurity(next), updatedAt: at });
      tx.create(auditRef, { schemaVersion: 1, type: "PICKUP_UNLOCKED", actorUid: actor.uid, reason: request.reason,
        cycleBefore: security.cycle, cycleAfter: next.cycle, failuresBefore: security.failures,
        lockedAt: security.lockedAt, occurredAt: at });
      return { contractVersion: 1, outcome: "UNLOCKED", cycle: next.cycle, replayed: false };
    });
  }
}
