import { OAuth2Client } from "google-auth-library";
import { RIDER_INVOKER } from "./riderActivation";

/**
 * E6/G7-B — cryptographic check of WHO calls riderApi, on top of Cloud Run IAM.
 * Needed because project-wide roles (roles/editor on the default compute SA, roles/owner) also carry run.routes.invoke,
 * so IAM alone cannot guarantee that only Chaski Rider calls the integration API.
 *
 * Contract (documented for Rider):
 * - `Authorization: Bearer <Google-signed ID token>` of RIDER_INVOKER, audience EXACTLY RIDER_AUDIENCE, with the
 *   `email` claim (format "full" / includeEmail) and `email_verified: true`.
 * - `X-Serverless-Authorization` is REJECTED: Cloud Run checks only that header for IAM when present and strips its
 *   signature before the container, so it could carry another identity past IAM while Authorization is unverifiable.
 */
export const RIDER_AUDIENCE = "https://riderapi-u65lcqarqa-tl.a.run.app";
export const GOOGLE_ISSUERS = ["accounts.google.com", "https://accounts.google.com"] as const;

export type IdTokenClaims = { iss?: string; aud?: string | string[]; email?: string; email_verified?: boolean; exp?: number };
/** Verifies signature, expiry, issuer and audience; throws on any failure. Injectable for tests. */
export type IdTokenVerifier = (token: string, audience: string) => Promise<IdTokenClaims>;

export class CallerNotAllowed extends Error {
  constructor() { super("CALLER_NOT_ALLOWED"); }
}

const JWT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

/** Production verifier: Google's published certificates + google-auth-library (RS256 signature, exp, aud, iss). */
export function googleIdTokenVerifier(client = new OAuth2Client()): IdTokenVerifier {
  return async (token, audience) => {
    const { certs } = await client.getFederatedSignonCertsAsync();
    const ticket = await client.verifySignedJwtWithCertsAsync(token, certs, audience, [...GOOGLE_ISSUERS]);
    return (ticket.getPayload() ?? {}) as IdTokenClaims;
  };
}

/** Throws CallerNotAllowed unless the request carries a verified ID token of the Rider service account. */
export async function assertRiderCaller(headers: Record<string, string | string[] | undefined>, verify: IdTokenVerifier): Promise<void> {
  if (headers["x-serverless-authorization"] !== undefined) throw new CallerNotAllowed();
  const authorization = headers["authorization"];
  if (typeof authorization !== "string") throw new CallerNotAllowed(); // absent or repeated
  const match = /^Bearer ([^\s,]+)$/.exec(authorization);
  if (!match || !JWT.test(match[1])) throw new CallerNotAllowed();
  let claims: IdTokenClaims;
  try { claims = await verify(match[1], RIDER_AUDIENCE); } catch { throw new CallerNotAllowed(); }
  // Defense in depth: re-check what the library already enforced, plus identity.
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (!audiences.includes(RIDER_AUDIENCE) || !GOOGLE_ISSUERS.includes(claims.iss as typeof GOOGLE_ISSUERS[number]) ||
      claims.email !== RIDER_INVOKER || claims.email_verified !== true) throw new CallerNotAllowed();
}
