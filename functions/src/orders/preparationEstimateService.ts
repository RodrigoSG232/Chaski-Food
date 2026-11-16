import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { validId } from "../access/commercialAccessPolicy";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { buyerOrderId } from "./buyerOrdersPolicy";
import { canExtendPreparation, extendedReadyAtMillis, extensionOperationKey, extensionRequestHash,
  PreparationExtension } from "./preparationEstimatePolicy";
import { OrderHttpError } from "./orderRequest";

type ExtensionResult = { contractVersion: 1; outcome: "EXTENDED"; status: "PREPARING";
  estimatedReadyAtEpochMillis: number; estimateRevision: number; preparationExtendedMinutes: number };

const inconsistent = (): never => { throw new OrderHttpError("DATA_INCONSISTENT", 503); };

/** Optional counters written by HU20: absent means zero; anything else must be a non-negative safe integer. */
function counter(value: unknown): number {
  if (value === undefined || value === null) return 0;
  return Number.isSafeInteger(value) && (value as number) >= 0 && (value as number) < Number.MAX_SAFE_INTEGER ? value as number : inconsistent();
}

/** HU20: extends the current preparation estimate exactly once per operation. */
export class PreparationEstimateService {
  private readonly access: CommercialAccessService;
  constructor(private readonly db: Firestore, auth: Auth, private readonly now: () => number = Date.now) {
    this.access = new CommercialAccessService(db, auth);
  }

  async extend(actor: CommercialIdentity, rawStoreId: string, rawOrderId: string, input: PreparationExtension): Promise<ExtensionResult> {
    const storeId = validId(rawStoreId) ? rawStoreId : null, orderId = buyerOrderId(rawOrderId);
    if (!storeId || !orderId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    const operationKey = extensionOperationKey(input.operationId);
    const requestHash = extensionRequestHash(actor.uid, storeId, orderId, input);
    return this.db.runTransaction(async tx => {
      // Reads first; authorization precedes any answer about the order or a previous operation.
      const scope = await this.access.requireStoreOrderReader(tx, actor, storeId);
      const ref = this.db.doc(`food_orders/${orderId}`);
      const operationRef = ref.collection("estimate_operations").doc(operationKey);
      const [orderDoc, operationDoc] = await Promise.all([tx.get(ref), tx.get(operationRef)]);
      const order = orderDoc.data();
      if (!order || order.schemaVersion !== 1 || order.orderId !== orderId || order.storeId !== storeId ||
          order.businessId !== scope.businessId) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      const previousOperation = operationDoc.data();
      if (previousOperation) {
        if (previousOperation.requestHash !== requestHash) throw new OrderHttpError("OPERATION_CONFLICT", 409);
        return previousOperation.result as ExtensionResult;
      }
      if (!canExtendPreparation(order.status)) throw new OrderHttpError("ORDER_STATE_CHANGED", 409);
      const revision = counter(order.estimateRevision);
      if (revision !== input.expectedEstimateRevision) throw new OrderHttpError("ESTIMATE_REVISION_CHANGED", 409);
      if (!(order.estimatedReadyAt instanceof Timestamp)) return inconsistent();
      const extended = counter(order.preparationExtendedMinutes);
      const previousUpdate = order.updatedAt instanceof Timestamp ? order.updatedAt.toMillis() : 0;
      const at = Timestamp.fromMillis(Math.max(this.now(), previousUpdate + 1));
      const previousReadyAt = order.estimatedReadyAt as Timestamp;
      const readyAt = Timestamp.fromMillis(extendedReadyAtMillis(previousReadyAt.toMillis(), at.toMillis(), input.additionalMinutes));
      const nextRevision = revision + 1, totalExtended = extended + input.additionalMinutes;
      if (!Number.isSafeInteger(totalExtended)) return inconsistent();
      const result: ExtensionResult = { contractVersion: 1, outcome: "EXTENDED", status: "PREPARING",
        estimatedReadyAtEpochMillis: readyAt.toMillis(), estimateRevision: nextRevision, preparationExtendedMinutes: totalExtended };
      // estimatedPreparationMinutes keeps the HU19 value: readers and the HU19 retry contract depend on it.
      tx.update(ref, { estimatedReadyAt: readyAt, estimateRevision: nextRevision, preparationExtendedMinutes: totalExtended,
        estimateUpdatedAt: at, estimateUpdatedByUid: actor.uid, updatedAt: at });
      tx.create(ref.collection("estimate_history").doc(String(nextRevision).padStart(10, "0")), { schemaVersion: 2,
        kind: "EXTENSION", revision: nextRevision, additionalMinutes: input.additionalMinutes, previousReadyAt,
        baseAt: Timestamp.fromMillis(Math.max(previousReadyAt.toMillis(), at.toMillis())), estimatedReadyAt: readyAt,
        actorUid: actor.uid, operationKey, occurredAt: at });
      tx.create(operationRef, { schemaVersion: 1, requestHash, revision: nextRevision, result, createdAt: at });
      // Legacy synchronization kept as-is for HU22; nothing currently writes deliveryId or consumes these events.
      if (typeof order.deliveryId === "string" && order.deliveryId.length) {
        tx.create(this.db.doc(`rider_events/estimate_${orderId}_${nextRevision}`), { schemaVersion: 1,
          type: "PREPARATION_ESTIMATE_UPDATED", orderId, deliveryId: order.deliveryId, businessId: scope.businessId,
          storeId, estimatedReadyAt: readyAt, status: "PENDING", createdAt: at, updatedAt: at });
      }
      return result;
    });
  }
}
