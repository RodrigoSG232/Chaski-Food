import { createHash } from "node:crypto";

export class CommercialAccessError extends Error {
  constructor(readonly code: string, readonly status = 409) { super(code); }
}
export function digest(value: unknown): string {
  const canonical = (input: unknown): unknown => Array.isArray(input) ? input.map(canonical) :
    input && typeof input === "object" ? Object.fromEntries(Object.entries(input).sort(([a], [b]) => a.localeCompare(b)).map(([key, entry]) => [key, canonical(entry)])) : input;
  return createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
}
export function normalizedEmail(value: unknown): string {
  if (typeof value !== "string") throw new CommercialAccessError("INVALID_EMAIL", 400);
  const email = value.trim().toLowerCase();
  if (!/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(email) || email.length > 254) throw new CommercialAccessError("INVALID_EMAIL", 400);
  return email;
}
export function validId(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value);
}
export function ownsCommercialBusiness(status: unknown): boolean {
  return status === "APPROVED" || status === "SUSPENDED";
}
export type Assignment = {
  id: string; businessId: string; email: string; assignedStoreIds: string[];
  bindingState: "LEGACY" | "PENDING" | "VERIFIED" | "REVOKED" | "RECONCILIATION_REQUIRED";
  boundUid: string | null;
};
export function confirmedAssignments(rows: Assignment[], uid: string): Assignment[] {
  return rows.filter(row => row.bindingState === "VERIFIED" && row.boundUid === uid && row.assignedStoreIds.length > 0);
}
export function assertExclusive(status: unknown, rows: Assignment[], uid: string): void {
  if (ownsCommercialBusiness(status) && confirmedAssignments(rows, uid).length) throw new CommercialAccessError("AUTHORIZATION_CONFLICT");
}
export function assignmentFrom(id: string, data: Record<string, unknown>): Assignment {
  const email = normalizedEmail(data.email);
  if (!validId(data.businessId) || id !== `${data.businessId}_${email}` || !Array.isArray(data.assignedStoreIds) ||
      !data.assignedStoreIds.every(validId) || new Set(data.assignedStoreIds).size !== data.assignedStoreIds.length) {
    throw new CommercialAccessError("DATA_INCONSISTENT", 503);
  }
  const state = data.bindingState ?? "LEGACY";
  if (!["LEGACY", "PENDING", "VERIFIED", "REVOKED", "RECONCILIATION_REQUIRED"].includes(String(state)) ||
      (state === "VERIFIED" && !validId(data.boundUid)) ||
      (data.boundUid != null && !validId(data.boundUid))) throw new CommercialAccessError("DATA_INCONSISTENT", 503);
  return { id, businessId: data.businessId, email, assignedStoreIds: data.assignedStoreIds as string[],
    bindingState: state as Assignment["bindingState"], boundUid: (data.boundUid as string | undefined) ?? null };
}
export type CommercialCommand = {
  action: "PREPARE" | "APPROVE" | "ASSIGN" | "REVOKE" | "BIND" | "REGISTER" | "RESUBMIT";
  operationId: string; expectedRevision: number; businessId?: string; email?: string;
  storeIds?: string[]; fullName?: string; phone?: string;
  business?: { businessName: string; ruc: string; legalAddress: string; phone: string; email: string; category: string };
};
export function parseCommercialCommand(body: unknown): CommercialCommand {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new CommercialAccessError("INVALID_REQUEST", 400);
  const c = body as Record<string, unknown>;
  const allowed = ["action", "operationId", "expectedRevision", "businessId", "email", "storeIds", "fullName", "phone", "business"];
  if (Object.keys(c).some(k => !allowed.includes(k)) || !["PREPARE", "APPROVE", "ASSIGN", "REVOKE", "BIND", "REGISTER", "RESUBMIT"].includes(String(c.action)) ||
      !validId(c.operationId) || !Number.isSafeInteger(c.expectedRevision) || Number(c.expectedRevision) < 0) throw new CommercialAccessError("INVALID_REQUEST", 400);
  if (["APPROVE", "ASSIGN", "REVOKE"].includes(String(c.action)) && !validId(c.businessId)) throw new CommercialAccessError("INVALID_REQUEST", 400);
  if (["PREPARE", "ASSIGN", "REVOKE"].includes(String(c.action))) normalizedEmail(c.email);
  if (c.action === "ASSIGN" && (!Array.isArray(c.storeIds) || !c.storeIds.length || !c.storeIds.every(validId) ||
      new Set(c.storeIds).size !== c.storeIds.length || typeof c.fullName !== "string" || typeof c.phone !== "string")) throw new CommercialAccessError("INVALID_REQUEST", 400);
  if (["REGISTER", "RESUBMIT"].includes(String(c.action))) {
    const b = c.business as Record<string, unknown> | undefined;
    if (!b || typeof b !== "object" || Array.isArray(b) || Object.keys(b).sort().join(",") !== "businessName,category,email,legalAddress,phone,ruc" ||
        !Object.values(b).every(v => typeof v === "string" && v.trim().length > 0) || !/^\d{11}$/.test(String(b.ruc))) throw new CommercialAccessError("INVALID_REQUEST", 400);
    normalizedEmail(b.email);
  }
  for (const field of ["fullName", "phone"]) if (c[field] !== undefined && (typeof c[field] !== "string" || (c[field] as string).length > 1000)) throw new CommercialAccessError("INVALID_REQUEST", 400);
  return c as unknown as CommercialCommand;
}
