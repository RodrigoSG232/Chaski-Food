export type Decision = "ELIGIBLE" | "BUSINESS_SUSPENDED" | "BUSINESS_NOT_APPROVED" | "STORE_UNAVAILABLE";
export class InconsistentData extends Error {}
export type DocumentData = Record<string, unknown>;

export function businessIdOf(store: DocumentData): string {
  const id = store.businessId;
  if (typeof id !== "string" || !id.trim() || id.includes("/")) throw new InconsistentData();
  return id;
}

/** Solo habilitación administrativa; horario, cobertura y oferta se validan por separado. */
export function evaluateEligibility(store: DocumentData | undefined, business: DocumentData | undefined): Decision {
  if (!store) return "STORE_UNAVAILABLE";
  businessIdOf(store);
  if (!["ACTIVE", "INACTIVE", "SUSPENDED"].includes(String(store.status))) throw new InconsistentData();
  if (!business) return "STORE_UNAVAILABLE";
  if (!["PENDING_REVIEW", "APPROVED", "OBSERVED", "REJECTED", "SUSPENDED"].includes(String(business.status))) {
    throw new InconsistentData();
  }
  if (business.status === "SUSPENDED") return "BUSINESS_SUSPENDED";
  if (business.status !== "APPROVED") return "BUSINESS_NOT_APPROVED";
  return store.status === "ACTIVE" ? "ELIGIBLE" : "STORE_UNAVAILABLE";
}
