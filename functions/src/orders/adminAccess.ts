import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { OrderHttpError } from "./orderRequest";

/**
 * Narrow Food-admin exception to HU34-28, shared by pickup incidents (E2.4) and collection reconciliation (E5.4a):
 * the `admin` claim with a verified email. It grants NO general order access — each service exposes only its own
 * minimal operations and data.
 */
export function requireFoodAdmin(actor: CommercialIdentity): void {
  if (actor.admin !== true || actor.emailVerified !== true) throw new OrderHttpError("ACCESS_DENIED", 403);
}
