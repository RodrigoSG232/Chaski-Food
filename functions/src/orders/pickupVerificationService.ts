import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { OrderHttpError } from "./orderRequest";
import { decidePickupVerification, PickupVerificationRequest, pickupOutcomeStatus, readPickupSecurity,
  storedPickupSecurity } from "./pickupSecurityPolicy";

/**
 * E2.2 — the assigned VERIFIED manager types the code shown by the rider. Every attempt (also a wrong one) commits:
 * the transaction never throws for business outcomes, so failures and the lock are persisted before answering 409/423.
 * Attempts are idempotent per operationId and stored WITHOUT the submitted code.
 */
export class PickupVerificationService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now) {
    this.access = new CommercialAccessService(db, auth);
  }

  async verify(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string, request: PickupVerificationRequest) {
    const storeId = validId(rawStoreId) ? rawStoreId : null, orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const ref = this.db.doc(`food_orders/${orderId}`), attemptRef = ref.collection("pickup_attempts").doc(request.operationId);
      const [snapshot, known] = await Promise.all([tx.get(ref), tx.get(attemptRef)]);
      const order = snapshot.data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId || order.storeId !== storeId ||
          order.businessId !== scope.businessId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      if (known.exists) {
        const stored = known.data()!;
        // Same operation, same actor and same assignment: replay the stored answer. Anything else is a collision.
        if (stored.actorUid !== actor.uid || stored.assignmentId !== request.assignmentId ||
            stored.assignmentVersion !== request.assignmentVersion) throw new OrderHttpError("IDEMPOTENCY_CONFLICT", 409);
        return { httpStatus: pickupOutcomeStatus(stored.outcome), body: { contractVersion: 1, outcome: stored.outcome,
          attemptsRemaining: stored.attemptsRemaining, replayed: true } };
      }
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const security = readPickupSecurity(order.pickupSecurity);
      const decision = decidePickupVerification({ status: order.status, storedCode: order.pickupCode, security,
        assignment: order.riderIntegration?.assignment ?? null, request, actorUid: actor.uid, at });
      if (decision.next) tx.update(ref, { pickupSecurity: storedPickupSecurity(decision.next), updatedAt: at });
      tx.create(attemptRef, { schemaVersion: 1, actorUid: actor.uid, assignmentId: request.assignmentId,
        assignmentVersion: request.assignmentVersion, cycle: security.cycle, outcome: decision.outcome,
        attemptsRemaining: decision.attemptsRemaining, occurredAt: at });
      return { httpStatus: pickupOutcomeStatus(decision.outcome), body: { contractVersion: 1, outcome: decision.outcome,
        attemptsRemaining: decision.attemptsRemaining, replayed: false } };
    });
  }
}
