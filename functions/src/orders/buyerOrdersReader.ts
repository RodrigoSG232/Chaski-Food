import { FieldPath, Firestore, Timestamp } from "firebase-admin/firestore";
import { OrderHttpError } from "./orderRequest";
import { BUYER_ORDER_FIELDS, BUYER_SCAN_BATCH, BUYER_SCAN_BUDGET, buyerOrderId, encodeBuyerOrdersCursor, parseBuyerOrdersQuery,
  projectBuyerOrderDetail, projectBuyerOrderSummary } from "./buyerOrdersPolicy";

type Summary = ReturnType<typeof projectBuyerOrderSummary>;

/** HU17: the buyer's own orders (any commercial experience; authorization is the authenticated UID = buyerUid).
 * Read-only transactions; one chronological stream per UID classified by phase here, so no status is excluded by the query. */
export class BuyerOrdersReader {
  constructor(private readonly db: Firestore) {}

  async page(uid: string, query: Record<string, unknown>) {
    const { phase, pageSize, cursor } = parseBuyerOrdersQuery(query, uid);
    return this.db.runTransaction(async tx => {
      let after: { createdAt: Timestamp; id: string } | null = null;
      if (cursor) {
        const anchor = (await tx.get(this.db.doc(`food_orders/${cursor.anchorId}`))).data();
        if (!anchor || anchor.buyerUid !== uid || anchor.orderId !== cursor.anchorId || !(anchor.createdAt instanceof Timestamp))
          throw new OrderHttpError("INVALID_CURSOR", 400);
        after = { createdAt: anchor.createdAt, id: cursor.anchorId };
      }
      const orders: Summary[] = [];
      let examined = 0, lastExamined: string | null = null, exhausted = false;
      scan: while (orders.length < pageSize && examined < BUYER_SCAN_BUDGET) {
        let stream = this.db.collection("food_orders").where("buyerUid", "==", uid)
          .orderBy("createdAt", "desc").orderBy(FieldPath.documentId(), "desc").select(...BUYER_ORDER_FIELDS);
        if (after) stream = stream.startAfter(after.createdAt, after.id);
        const limit = Math.min(BUYER_SCAN_BATCH, BUYER_SCAN_BUDGET - examined);
        const batch = await tx.get(stream.limit(limit));
        for (const doc of batch.docs) {
          examined++;
          const summary = projectBuyerOrderSummary(doc.id, doc.data(), uid);
          if (!summary.statusKnown) console.warn("UNKNOWN_ORDER_STATUS", summary.status);
          // The cursor advances to every examined document, including those of the other phase and the one filling the page.
          lastExamined = doc.id;
          after = { createdAt: doc.get("createdAt"), id: doc.id };
          if (summary.phase === phase) orders.push(summary);
          if (orders.length === pageSize) break scan;
        }
        if (batch.size < limit) { exhausted = true; break; }
      }
      return { contractVersion: 1, phase, orders,
        nextCursor: exhausted || lastExamined === null ? null : encodeBuyerOrdersCursor(uid, phase, lastExamined) };
    }, { readOnly: true });
  }

  async detail(uid: string, rawId: string) {
    const id = buyerOrderId(rawId);
    if (!id) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(`food_orders/${id}`);
      const data = (await tx.get(ref)).data();
      // A foreign order and a missing one produce the same answer: existence is never revealed.
      if (!data || data.buyerUid !== uid) throw new OrderHttpError("ORDER_NOT_FOUND", 404);
      const history = await tx.get(ref.collection("status_history").orderBy("occurredAt", "asc").limit(100));
      return { contractVersion: 1, order: projectBuyerOrderDetail(id, data, uid, history.docs.map(doc => doc.data())) };
    }, { readOnly: true });
  }
}
