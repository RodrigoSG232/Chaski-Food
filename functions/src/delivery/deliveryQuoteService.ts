import { Firestore, Transaction } from "firebase-admin/firestore";
import { InvalidRouteDistance, QuoteError, TARIFF_VERSION } from "./deliveryQuotePolicy";
import { DeliveryQuote, QuoteContext, quoteDocument, readQuoteContext, readStoredQuote } from "./deliveryQuoteStore";
import { RouteFailure, RouteReader } from "./routeReader";
export { QuoteError, QuoteErrorCode } from "./deliveryQuotePolicy";
export { DeliveryQuote, haversineKm } from "./deliveryQuoteStore";

/** Rediscovery by authenticated logistics context survives relogin/process restart; no session cache. */
export async function createDeliveryQuote(db: Firestore, routes: RouteReader, uid: string,
  storeId: string, addressId: string, now: () => Date = () => new Date(), signal?: AbortSignal,
  quoteId?: string): Promise<{ quote: DeliveryQuote; firestoreReads: number; routeMs: number; reused: boolean }> {
  if (![uid, storeId, addressId].every(v => typeof v === "string" && v.trim() && !v.includes("/")) ||
      (quoteId !== undefined && !/^[A-Za-z0-9_-]{1,128}$/.test(quoteId))) throw new QuoteError("INVALID_REQUEST", 400);
  let reads = 0;
  const read = () => { reads++; };
  async function existing(tx: Transaction, context: QuoteContext): Promise<DeliveryQuote | null> {
    signal?.throwIfAborted();
    if (quoteId !== undefined) {
      read(); const doc = await tx.get(db.doc(`delivery_quotes/${quoteId}`));
      return readStoredQuote(doc.id, doc.data(), context, now());
    }
    // One bounded query; composite index avoids scanning a user's historical quotes.
    read(); const docs = await tx.get(db.collection("delivery_quotes").where("logisticsHash", "==", context.logisticsHash).where("tariffVersion", "==", TARIFF_VERSION)
      .orderBy("quotedAt", "desc").limit(1));
    if (docs.empty) return null;
    try { return readStoredQuote(docs.docs[0].id, docs.docs[0].data(), context, now()); }
    catch (error) {
      if (error instanceof QuoteError && ["QUOTE_EXPIRED", "QUOTE_VERSION_INVALID"].includes(error.code)) return null;
      throw error;
    }
  }
  const initial = await db.runTransaction(async tx => {
    const context = await readQuoteContext(db, tx, uid, storeId, addressId, read);
    const quote = await existing(tx, context);
    signal?.throwIfAborted();
    return { context, quote };
  }, { readOnly: true });
  if (initial.quote) return { quote: initial.quote, firestoreReads: reads, routeMs: 0, reused: true };
  signal?.throwIfAborted();
  const started = performance.now();
  let distance: number;
  try { distance = await routes.distanceMeters(initial.context.origin, initial.context.destination, signal); }
  catch (error) {
    if (error instanceof RouteFailure) throw new QuoteError(error.code,
      ["NO_ROUTE", "INVALID_LOCATION"].includes(error.code) ? 422 : 503);
    throw error;
  }
  const routeMs = performance.now() - started;
  signal?.throwIfAborted();
  const reference = db.collection("delivery_quotes").doc();
  const final = await db.runTransaction(async tx => {
    const context = await readQuoteContext(db, tx, uid, storeId, addressId, read);
    if (context.logisticsHash !== initial.context.logisticsHash || context.businessId !== initial.context.businessId) {
      throw new QuoteError("QUOTE_INCOMPATIBLE", 409);
    }
    const previous = await existing(tx, context);
    signal?.throwIfAborted();
    if (previous) return { quote: previous, reused: true };
    let data: Record<string, unknown>;
    try { data = quoteDocument(context, distance, now()); }
    catch (error) {
      if (error instanceof InvalidRouteDistance) throw new QuoteError("ROUTING_INVALID_RESPONSE", 503);
      throw error;
    }
    const quote = readStoredQuote(reference.id, data, context, now());
    tx.create(reference, data); // Immutable quote; never replace an expired quote under its original ID.
    return { quote, reused: false };
  });
  return { ...final, firestoreReads: reads, routeMs };
}
