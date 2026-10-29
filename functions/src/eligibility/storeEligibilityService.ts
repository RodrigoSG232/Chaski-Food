import { Firestore } from "firebase-admin/firestore";
import { businessIdOf, evaluateEligibility, eligibleBusinessName } from "./storeEligibilityPolicy";

/** Lee una vista consistente. No escribe ni conserva autorizaciones entre consultas. */
export async function checkStoreEligibility(db: Firestore, storeId: string) {
  let reads = 0;
  const result = await db.runTransaction(async transaction => {
    reads++;
    const store = await transaction.get(db.doc(`stores/${storeId}`));
    if (!store.exists) return { storeId, businessId: null, decision: evaluateEligibility(undefined, undefined), storeData: undefined };
    const data = store.data()!;
    const businessId = businessIdOf(data);
    reads++;
    const business = await transaction.get(db.doc(`business_requests/${businessId}`));
    const decision = evaluateEligibility(data, business.data());
    return { storeId, businessId, decision, ...eligibleBusinessName(decision, business.data()), storeData: data };
  }, { readOnly: true });
  const { storeData, ...publicResult } = result;
  return { result: publicResult, storeData, reads };
}
