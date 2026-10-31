import { Timestamp } from "firebase-admin/firestore";
import { digest, validId } from "../access/commercialAccessPolicy";
import { MAX_ORDER_EPOCH_MILLIS, orderText } from "./orderModels";
import { OrderHttpError } from "./orderRequest";
import { validatedOrderCore } from "./storeOrdersPolicy";

/** HU17 read vocabulary. HU17 never writes statuses; HU18–HU23 add transitions (HU19: PENDING_CONFIRMATION → PREPARING). */
export const ACTIVE_ORDER_STATUSES = ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP", "IN_DELIVERY"] as const;
export const HISTORICAL_ORDER_STATUSES = ["COMPLETED", "CANCELLED", "REJECTED"] as const;
export type OrderPhase = "ACTIVE" | "HISTORICAL";
export const DEFAULT_BUYER_PAGE_SIZE = 20;
export const MAX_BUYER_PAGE_SIZE = 50;
/** Documents read per Firestore query and per request. Bounds billable reads; select() only trims transfer. */
export const BUYER_SCAN_BATCH = 50;
export const BUYER_SCAN_BUDGET = 200;
/** Fields the projections need; everything else (buyer identity, hashes, quote) is never read into the response. */
export const BUYER_ORDER_FIELDS = ["schemaVersion", "orderId", "buyerUid", "status", "createdAt", "items", "subtotalMinor",
  "deliveryFeeMinor", "totalMinor", "currency", "paymentMethod", "storeSnapshot", "businessSnapshot"] as const;

const inconsistent = (): never => { throw new OrderHttpError("DATA_INCONSISTENT", 503, true); };

/** A present, well-formed but unknown status is shown as ACTIVE (N1); missing/null/malformed is an inconsistency. */
export function classifyStatus(status: unknown): { status: string; phase: OrderPhase; known: boolean } {
  if (typeof status !== "string" || !/^[A-Z][A-Z0-9_]{1,63}$/.test(status)) return inconsistent();
  if ((ACTIVE_ORDER_STATUSES as readonly string[]).includes(status)) return { status, phase: "ACTIVE", known: true };
  if ((HISTORICAL_ORDER_STATUSES as readonly string[]).includes(status)) return { status, phase: "HISTORICAL", known: true };
  return { status, phase: "ACTIVE", known: false };
}

function snapshotName(snapshot: unknown): string {
  if (!snapshot || typeof snapshot !== "object") return inconsistent();
  return orderText((snapshot as Record<string, unknown>).name);
}

/** List row: no buyer identity, addresses, contacts, hashes or quote evidence. */
export function projectBuyerOrderSummary(id: string, data: Record<string, any>, uid: string) {
  try {
    if (data.buyerUid !== uid) throw new Error();
    const core = validatedOrderCore(id, data), state = classifyStatus(data.status);
    return { orderId: id, createdAtEpochMillis: core.createdAtEpochMillis, status: state.status, phase: state.phase,
      statusKnown: state.known, storeName: snapshotName(data.storeSnapshot), businessName: snapshotName(data.businessSnapshot),
      itemCount: core.items.reduce((sum: number, item: { quantity: number }) => sum + item.quantity, 0),
      totalMinor: core.totalMinor, currency: core.currency };
  } catch (error) {
    if (error instanceof OrderHttpError) throw error;
    return inconsistent();
  }
}

function optionalText(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  return orderText(value);
}

/** Detail keeps the HU16 historical snapshots by value; coordinates, buyer data and actors are excluded. */
export function projectBuyerOrderDetail(id: string, data: Record<string, any>, uid: string, history: Array<Record<string, any>>) {
  const summary = projectBuyerOrderSummary(id, data, uid);
  try {
    const core = validatedOrderCore(id, data), store = data.storeSnapshot, pickup = data.pickupSnapshot, dropoff = data.dropoffSnapshot;
    if (!pickup || !dropoff || typeof pickup !== "object" || typeof dropoff !== "object") throw new Error();
    const statusHistory = history.map(entry => {
      if (entry.schemaVersion !== 1 || !(entry.occurredAt instanceof Timestamp)) throw new Error();
      const occurredAtEpochMillis = Math.floor(entry.occurredAt.toMillis());
      if (occurredAtEpochMillis < 1 || occurredAtEpochMillis > MAX_ORDER_EPOCH_MILLIS) throw new Error();
      const state = classifyStatus(entry.toStatus);
      return { status: state.status, statusKnown: state.known, occurredAtEpochMillis };
    }).sort((a, b) => a.occurredAtEpochMillis - b.occurredAtEpochMillis);
    if (statusHistory.length === 0) throw new Error();
    return { ...summary, storePhone: optionalText(store.phone), pickupAddressText: orderText(pickup.addressText),
      delivery: { label: optionalText(dropoff.label), addressText: orderText(dropoff.addressText),
        reference: optionalText(dropoff.reference), instructions: optionalText(dropoff.instructions) },
      items: core.items, subtotalMinor: core.subtotalMinor, deliveryFeeMinor: core.deliveryFeeMinor,
      paymentMethod: core.paymentMethod, statusHistory };
  } catch (error) {
    if (error instanceof OrderHttpError) throw error;
    return inconsistent();
  }
}

export type BuyerOrdersCursor = Readonly<{ v: 1; phase: OrderPhase; subject: string; anchorId: string }>;
const cursorSubject = (uid: string) => digest(["buyer-orders-cursor-v1", uid]);
/** Opaque pointer to the LAST EXAMINED document. digest() is an unkeyed SHA-256: it binds, it does not authenticate;
 * the reader re-validates the anchor against the authenticated UID, so a forged cursor can only reach the caller's orders. */
export function encodeBuyerOrdersCursor(uid: string, phase: OrderPhase, anchorId: string): string {
  return Buffer.from(JSON.stringify({ v: 1, phase, subject: cursorSubject(uid), anchorId })).toString("base64url");
}
export function parseBuyerOrdersQuery(query: Record<string, unknown>, uid: string):
  { phase: OrderPhase; pageSize: number; cursor: BuyerOrdersCursor | null } {
  if (Object.keys(query).some(key => !["phase", "pageSize", "cursor"].includes(key))) throw new OrderHttpError("INVALID_REQUEST", 400);
  const phase = query.phase;
  if (phase !== "ACTIVE" && phase !== "HISTORICAL") throw new OrderHttpError("INVALID_REQUEST", 400);
  const size = query.pageSize;
  if (size !== undefined && (typeof size !== "string" || !/^[1-9][0-9]?$/.test(size) || Number(size) > MAX_BUYER_PAGE_SIZE))
    throw new OrderHttpError("INVALID_REQUEST", 400);
  let cursor: BuyerOrdersCursor | null = null;
  if (query.cursor !== undefined) {
    try {
      if (typeof query.cursor !== "string" || query.cursor.length > 1024 || !/^[A-Za-z0-9_-]+$/.test(query.cursor)) throw new Error();
      const raw = JSON.parse(Buffer.from(query.cursor, "base64url").toString("utf8"));
      if (!raw || Array.isArray(raw) || Object.keys(raw).sort().join(",") !== "anchorId,phase,subject,v" || raw.v !== 1 ||
        raw.phase !== phase || raw.subject !== cursorSubject(uid) || typeof raw.anchorId !== "string" || !/^[0-9a-f]{64}$/.test(raw.anchorId) ||
        encodeBuyerOrdersCursor(uid, phase, raw.anchorId) !== query.cursor) throw new Error();
      cursor = Object.freeze(raw);
    } catch { throw new OrderHttpError("INVALID_CURSOR", 400); }
  }
  return { phase, pageSize: size === undefined ? DEFAULT_BUYER_PAGE_SIZE : Number(size), cursor };
}
/** Order IDs are SHA-256 hex (HU16); anything else is reported as not found, never as a distinct validation error. */
export function buyerOrderId(value: string): string | null {
  return validId(value) && /^[0-9a-f]{64}$/.test(value) ? value : null;
}
