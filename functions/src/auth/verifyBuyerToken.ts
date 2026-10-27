import { Auth } from "firebase-admin/auth";

export class AuthenticationRequired extends Error {}
export async function verifyBuyerToken(auth: Auth, authorization: string | undefined): Promise<string> {
  const match = /^Bearer (\S+)$/i.exec(authorization ?? "");
  if (!match) throw new AuthenticationRequired();
  try {
    // También comprueba revocación y cuenta deshabilitada; no concede excepciones por rol.
    return (await auth.verifyIdToken(match[1], true)).uid;
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (["auth/argument-error", "auth/invalid-id-token", "auth/id-token-expired", "auth/id-token-revoked",
      "auth/user-disabled", "auth/user-not-found"].includes(code ?? "")) throw new AuthenticationRequired();
    throw error;
  }
}
