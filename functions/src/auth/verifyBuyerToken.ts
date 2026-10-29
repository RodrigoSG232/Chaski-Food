import { Auth } from "firebase-admin/auth";

export class AuthenticationRequired extends Error {}
async function verifiedToken(auth: Auth, authorization: string | undefined) {
  const match = /^Bearer (\S+)$/i.exec(authorization ?? "");
  if (!match) throw new AuthenticationRequired();
  try {
    // También comprueba revocación y cuenta deshabilitada; no concede excepciones por rol.
    return await auth.verifyIdToken(match[1], true);
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (["auth/argument-error", "auth/invalid-id-token", "auth/id-token-expired", "auth/id-token-revoked",
      "auth/user-disabled", "auth/user-not-found"].includes(code ?? "")) throw new AuthenticationRequired();
    throw error;
  }
}

/** Identidad vigente para el coordinador: no crea ni modifica cuentas. */
export async function verifyCommercialIdentity(auth: Auth, authorization: string | undefined) {
  const claims = await verifiedToken(auth, authorization);
  const uid = claims.uid;
  const current = await auth.getUser(uid);
  if (current.disabled || !current.email || claims.email !== current.email ||
      claims.email_verified !== current.emailVerified || (claims.admin === true) !== (current.customClaims?.admin === true)) {
    throw new AuthenticationRequired();
  }
  return { uid, email: current.email.trim().toLowerCase(), emailVerified: current.emailVerified,
    admin: current.customClaims?.admin === true };
}
export type CommercialIdentity = Awaited<ReturnType<typeof verifyCommercialIdentity>>;

export async function verifyBuyerToken(auth: Auth, authorization: string | undefined): Promise<string> {
  return (await verifiedToken(auth, authorization)).uid;
}
