import { QuoteError } from "./deliveryQuoteService";

export function parseDeliveryQuoteRequest(body: unknown): { storeId: string; addressId: string; quoteId?: string } {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new QuoteError("INVALID_REQUEST", 400);
  const fields = body as Record<string, unknown>;
  if (!["addressId,storeId", "addressId,quoteId,storeId"].includes(Object.keys(fields).sort().join(",")) ||
      !validId(fields.storeId) || !validId(fields.addressId) || ("quoteId" in fields && !validId(fields.quoteId))) throw new QuoteError("INVALID_REQUEST", 400);
  return { storeId: fields.storeId as string, addressId: fields.addressId as string, ...(fields.quoteId === undefined ? {} : { quoteId: fields.quoteId as string }) };
}

function validId(value: unknown): boolean {
  return typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value);
}
