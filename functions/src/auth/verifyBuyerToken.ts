import { Auth } from "firebase-admin/auth";

export class AuthenticationRequired extends Error {}
export async function verifyBuyerToken(auth: Auth, authorization: string | undefined): Promise<void> {
  const match = /^Bearer (\S+)$/i.exec(authorization ?? "");
  if (!match) throw new AuthenticationRequired();
  try {
    // También comprueba revocación y cuenta deshabilitada; no concede excepciones por rol.
    await auth.verifyIdToken(match[1], true);
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (["auth/argument-error", "auth/invalid-id-token", "auth/id-token-expired", "auth/id-token-revoked",
      "auth/user-disabled", "auth/user-not-found"].includes(code ?? "")) throw new AuthenticationRequired();
    throw error;
  }
}
