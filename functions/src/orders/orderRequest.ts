import { OrderIntent, parseOrderIntent } from "./orderModels";

export class OrderHttpError extends Error {
  constructor(readonly code: string, readonly status: number, readonly retryable = false) { super(code); }
}
function fields(v: unknown, names: string[]): Record<string, unknown> {
  if (!v || typeof v !== "object" || Array.isArray(v) || Object.keys(v).sort().join(",") !== names.sort().join(",")) {
    throw new OrderHttpError("INVALID_REQUEST",400);
  }
  return v as Record<string, unknown>;
}
/** IDs at the root; expected contains reviewed evidence only. UID is always injected from Auth. */
export function parseOrderSubmission(body: unknown, uid: string): OrderIntent {
  try {
    if (Buffer.byteLength(JSON.stringify(body),"utf8") > 512 * 1024) throw new OrderHttpError("REQUEST_TOO_LARGE",413);
    const b = fields(body,["contractVersion","intentId","storeId","addressId","quoteId","paymentMethod","expected"]);
    if (b.contractVersion !== 1 || ![b.storeId,b.addressId,b.quoteId].every(v => typeof v === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(v))) {
      throw new OrderHttpError("INVALID_REQUEST",400);
    }
    const e = fields(b.expected,["businessId","businessName","storeName","storePhone","buyerName","buyerPhone","pickup","delivery","items","amounts","currency","deliveryQuote"]);
    const d = fields(e.delivery,["label","destination"]);
    const q = fields(e.deliveryQuote,["distanceMeters","tariffVersion","quotedAtEpochMillis","expiresAtEpochMillis"]);
    return parseOrderIntent({ intentId:b.intentId,accepted:{ ...e,ownerUid:uid,storeId:b.storeId,paymentMethod:b.paymentMethod,
      delivery:{...d,addressId:b.addressId},deliveryQuote:{...q,quoteId:b.quoteId} } });
  } catch (error) {
    if (error instanceof OrderHttpError) throw error;
    throw new OrderHttpError("INVALID_REQUEST",400);
  }
}
