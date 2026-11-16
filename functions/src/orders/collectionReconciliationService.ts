import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { requireFoodAdmin } from "./adminAccess";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { commandHash, decideResolution, publicResolution, ReconciliationCommand, reviewIsOpen } from "./collectionReconciliationPolicy";
import { OrderHttpError } from "./orderRequest";

const MAX_ITEMS = 50;

/**
 * E5.4a — admin-only reconciliation of Rider collection discrepancies. Every decision is one Firestore transaction
 * (all reads before writes, no external effects) with an audit document per ORDER and operationId, so ids never
 * collide across orders. Rider's original declaration is never rewritten: only additive fields are written.
 */
export class CollectionReconciliationService {
  constructor(private readonly db: Firestore, private readonly now: () => number = Date.now) {}

  /** Open reviews and resolved reviews with an excess still to refund. Minimal data: no buyer fields. */
  async list(actor: CommercialIdentity) {
    requireFoodAdmin(actor);
    // Equality-only queries on one field each: served by the automatic single-field indexes (no composite index needed).
    const [open, excess] = await Promise.all([
      this.db.collection("food_orders").where("collectionReview.status", "==", "OPEN").limit(MAX_ITEMS + 1).get(),
      this.db.collection("food_orders").where("collectionReview.resolution.excessStatus", "==", "PENDING_REFUND").limit(MAX_ITEMS + 1).get(),
    ]);
    const row = (kind: "OPEN_REVIEW" | "EXCESS_PENDING_REFUND") => (doc: FirebaseFirestore.QueryDocumentSnapshot) => {
      const data = doc.data(), review = data.collectionReview ?? {};
      return { orderId: doc.id, shortId: doc.id.slice(0, 8), kind, storeId: data.storeId,
        storeName: typeof data.storeSnapshot?.name === "string" ? data.storeSnapshot.name : null,
        expectedMinor: review.expectedMinor, declaredMinor: review.declaredMinor, method: review.method, currency: review.currency,
        openedAt: review.openedAt instanceof Timestamp ? review.openedAt.toDate().toISOString() : null,
        resolution: publicResolution(review) };
    };
    const items = [...open.docs.slice(0, MAX_ITEMS).map(row("OPEN_REVIEW")), ...excess.docs.slice(0, MAX_ITEMS).map(row("EXCESS_PENDING_REFUND"))]
      .sort((a, b) => String(a.openedAt).localeCompare(String(b.openedAt)) || a.orderId.localeCompare(b.orderId));
    return { contractVersion: 1, items, truncated: open.size > MAX_ITEMS || excess.size > MAX_ITEMS };
  }

  async apply(actor: CommercialIdentity, rawOrderId: string, command: ReconciliationCommand) {
    requireFoodAdmin(actor);
    const orderId = buyerOrderId(rawOrderId);
    if (!orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    const hash = commandHash(actor.uid, orderId, command);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${orderId}`), auditRef = ref.collection("collection_reconciliations").doc(command.operationId);
      const [snapshot, known] = await Promise.all([tx.get(ref), tx.get(auditRef)]);
      const order = snapshot.data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      // Idempotency (also covers a response lost after commit): same operation + same content → original result.
      if (known.exists) {
        const stored = known.data()!;
        if (stored.requestHash !== hash) throw new OrderHttpError("IDEMPOTENCY_CONFLICT", 409);
        return { ...stored.result, replayed: true };
      }
      const previous = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previous + 1));
      const review = order.collectionReview as Record<string, any> | undefined;
      const historyRef = ref.collection("payment_history").doc(`reconciliation_${command.operationId}`);
      const base = { schemaVersion: 1, operationId: command.operationId, actorUid: actor.uid, evidence: command.evidence,
        requestHash: hash, occurredAt: at };

      if (command.action === "RESOLVE") {
        if (!reviewIsOpen(order)) {
          if (review?.status === "RESOLVED") throw new OrderHttpError("REVIEW_NOT_OPEN", 409);
          throw new OrderHttpError("REVIEW_NOT_ELIGIBLE", 409);
        }
        if (review!.expectedMinor !== order.totalMinor || review!.currency !== "PEN") throw new OrderHttpError("DATA_INCONSISTENT", 503, true);
        const decision = decideResolution(review!.expectedMinor, command.outcome, command.confirmedAmountMinor);
        const paid = decision.outcome === "FULLY_COLLECTED";
        const update: Record<string, unknown> = {
          "collectionReview.status": "RESOLVED",
          "collectionReview.resolution": { ...decision, actorUid: actor.uid, resolvedAt: at, operationId: command.operationId },
          updatedAt: at,
        };
        if (paid) {
          update.paymentStatus = "PAID"; update.paidAt = at;
          // amountMinor = the amount APPLIED to the order (Food's total); the excess lives only in the resolution.
          update.paymentCollection = { source: "RECONCILED", method: review!.method, amountMinor: review!.expectedMinor,
            confirmedMinor: decision.confirmedMinor, currency: review!.currency, operationId: command.operationId, recordedAt: at };
        }
        const result = { contractVersion: 1, orderId, action: "RESOLVE", outcome: decision.outcome,
          paymentStatus: paid ? "PAID" : "PENDING", reviewStatus: "RESOLVED", expectedMinor: review!.expectedMinor,
          declaredMinor: review!.declaredMinor, confirmedMinor: decision.confirmedMinor, shortfallMinor: decision.shortfallMinor,
          excessMinor: decision.excessMinor, excessStatus: decision.excessStatus };
        tx.update(ref, update as Record<string, any>);
        // Adjustment record, NOT a new cash movement: it must never be summed with the original Rider collection.
        tx.create(historyRef, { schemaVersion: 1, type: "COLLECTION_RECONCILIATION", nature: "ADJUSTMENT", outcome: decision.outcome,
          expectedMinor: review!.expectedMinor, declaredMinor: review!.declaredMinor, confirmedMinor: decision.confirmedMinor,
          shortfallMinor: decision.shortfallMinor, excessMinor: decision.excessMinor, currency: review!.currency,
          operationId: command.operationId, occurredAt: at });
        tx.create(auditRef, { ...base, type: "COLLECTION_REVIEW_RESOLVED", outcome: decision.outcome,
          confirmedAmountMinor: command.confirmedAmountMinor, paymentStatusBefore: "PENDING", paymentStatusAfter: result.paymentStatus,
          reviewStatusBefore: "OPEN", reviewStatusAfter: "RESOLVED", result });
        return { ...result, replayed: false };
      }

      // RECORD_EXCESS_REFUND: documents a refund made outside the app; full excess only.
      const resolution = review?.resolution as Record<string, any> | undefined;
      if (review?.status !== "RESOLVED" || resolution?.outcome !== "FULLY_COLLECTED" || resolution?.excessStatus !== "PENDING_REFUND")
        throw new OrderHttpError("EXCESS_NOT_PENDING", 409);
      if (!Number.isSafeInteger(resolution.excessMinor) || resolution.excessMinor < 1) throw new OrderHttpError("DATA_INCONSISTENT", 503, true);
      if (command.refundedAmountMinor !== resolution.excessMinor) throw new OrderHttpError("REFUND_AMOUNT_MISMATCH", 422);
      tx.update(ref, {
        "collectionReview.resolution.excessStatus": "REFUNDED",
        "collectionReview.resolution.refund": { amountMinor: command.refundedAmountMinor, actorUid: actor.uid, recordedAt: at,
          operationId: command.operationId },
        updatedAt: at,
      });
      const result = { contractVersion: 1, orderId, action: "RECORD_EXCESS_REFUND", excessMinor: resolution.excessMinor,
        excessStatus: "REFUNDED", paymentStatus: order.paymentStatus };
      tx.create(historyRef, { schemaVersion: 1, type: "EXCESS_REFUND_RECORDED", nature: "REFUND_DECLARATION",
        amountMinor: command.refundedAmountMinor, currency: review.currency, operationId: command.operationId, occurredAt: at });
      tx.create(auditRef, { ...base, type: "EXCESS_REFUND_RECORDED", refundedAmountMinor: command.refundedAmountMinor,
        excessStatusBefore: "PENDING_REFUND", excessStatusAfter: "REFUNDED", result });
      return { ...result, replayed: false };
    });
  }
}
