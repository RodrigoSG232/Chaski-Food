import { validId } from "../access/commercialAccessPolicy";

/**
 * E5.3 — operational scope of the Chaski Rider integration, from ONE deployment parameter (CHASKI_RIDER_ACTIVATION):
 * - unset or empty → DISABLED (fail-safe default: Rider sees nothing new);
 * - "pilot:storeA,storeB" → PILOT, only those Firestore store ids (never names, never hard-coded);
 * - "general" → GENERAL, every store.
 * Any other value is a configuration error: the API refuses to run rather than guessing a scope.
 */
export type RiderActivation =
  | { readonly mode: "DISABLED" }
  | { readonly mode: "PILOT"; readonly storeIds: ReadonlySet<string> }
  | { readonly mode: "GENERAL" };

export class RiderActivationConfigError extends Error {
  constructor() { super("RIDER_ACTIVATION_INVALID"); }
}

export function parseRiderActivation(raw: string | undefined): RiderActivation {
  const value = (raw ?? "").trim();
  if (value === "") return { mode: "DISABLED" };
  if (value === "general") return { mode: "GENERAL" };
  if (value.startsWith("pilot:")) {
    const ids = value.slice("pilot:".length).split(",").map(id => id.trim());
    if (ids.length === 0 || ids.length > 50 || ids.some(id => !validId(id))) throw new RiderActivationConfigError();
    return { mode: "PILOT", storeIds: new Set(ids) };
  }
  throw new RiderActivationConfigError();
}

/** New Rider participation (offers, new assignments, pickup simulations blocked) only inside the scope. */
export function storeInRiderScope(activation: RiderActivation, storeId: unknown): boolean {
  if (typeof storeId !== "string") return false;
  return activation.mode === "GENERAL" || (activation.mode === "PILOT" && activation.storeIds.has(storeId));
}

/**
 * An order that already started the Rider flow stays reachable (detail, its events, tracking) even if the scope later
 * shrinks, so an in-flight delivery can always be finished and reconciled. It never gains NEW assignments out of scope.
 */
export function orderStartedWithRider(data: Record<string, any>): boolean {
  return data.riderIntegration != null;
}

/**
 * E5.1: an order Rider already touched (any event, store verification or lock, collection or review) is Rider-managed
 * regardless of configuration. Food simulations must never move it.
 */
export function orderRiderManaged(data: Record<string, any>): boolean {
  const security = data.pickupSecurity;
  return data.riderIntegration != null || security?.verification != null || security?.lockedAt != null ||
    data.paymentCollection != null || data.collectionReview != null;
}

export function orderInRiderScope(activation: RiderActivation, data: Record<string, any>): boolean {
  return storeInRiderScope(activation, data.storeId) || orderStartedWithRider(data);
}
