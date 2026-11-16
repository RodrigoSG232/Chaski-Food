import { test } from "node:test";
import assert from "node:assert/strict";
import { createSign, generateKeyPairSync } from "node:crypto";
import { OAuth2Client } from "google-auth-library";
import { assertRiderCaller, CallerNotAllowed, GOOGLE_ISSUERS, IdTokenClaims, IdTokenVerifier, RIDER_AUDIENCE } from "../src/integration/riderCaller";
import { RIDER_INVOKER } from "../src/integration/riderActivation";
import { createRiderApiHandler } from "../src/riderApiHandler";

/**
 * E6/G7-B — riderApi caller identity.
 * Part 1 uses a FAKE verifier (pure decision logic). Part 2 performs REAL RS256 cryptography with the same
 * google-auth-library function used in production (verifySignedJwtWithCertsAsync), with a locally generated key.
 */
const shape = "aaaa.bbbb.cccc";
const rider: IdTokenClaims = { iss: "https://accounts.google.com", aud: RIDER_AUDIENCE, email: RIDER_INVOKER, email_verified: true };
const fake = (claims: IdTokenClaims | Error): IdTokenVerifier => async () => { if (claims instanceof Error) throw claims; return claims; };
const bearer = (t = shape) => ({ authorization: `Bearer ${t}` });
const denied = (p: Promise<unknown>) => assert.rejects(p, CallerNotAllowed);

test("G7-B logic: only a verified Rider token passes", async () => {
  await assertRiderCaller(bearer(), fake(rider));
  await assertRiderCaller(bearer(), fake({ ...rider, iss: "accounts.google.com" }));
  await denied(assertRiderCaller(bearer(), fake({ ...rider, email: "other@chaski-rider.iam.gserviceaccount.com" }))); // other account
  await denied(assertRiderCaller(bearer(), fake({ ...rider, email: "x@developer.gserviceaccount.com" }))); // default compute SA
  await denied(assertRiderCaller(bearer(), fake({ ...rider, aud: "https://riderapi-220568639274.southamerica-west1.run.app" }))); // aud
  await denied(assertRiderCaller(bearer(), fake({ ...rider, iss: "https://evil.example" }))); // issuer
  await denied(assertRiderCaller(bearer(), fake({ ...rider, email_verified: false }))); // unverified email
  await denied(assertRiderCaller(bearer(), fake({ ...rider, email_verified: undefined, email: undefined }))); // standard-format token without email
  await denied(assertRiderCaller(bearer(), fake(new Error("Token used too late")))); // expired / bad signature (verifier failure)
});

test("G7-B logic: missing, malformed, repeated or ambiguous headers are rejected before verifying", async () => {
  let calls = 0; const counting: IdTokenVerifier = async () => { calls++; return rider; };
  for (const headers of [{}, { authorization: "" }, { authorization: "Basic abc" }, { authorization: "Bearer" },
    { authorization: "Bearer a.b" }, { authorization: "Bearer a.b.c.d" }, { authorization: "Bearer a.b.c extra" },
    { authorization: "bearer " + shape }, { authorization: ["Bearer " + shape, "Bearer " + shape] },
    { ...bearer(), "x-serverless-authorization": "Bearer " + shape }, { "x-serverless-authorization": "Bearer " + shape }])
    await denied(assertRiderCaller(headers as any, counting));
  assert.equal(calls, 0);
});

// ---- Part 2: real cryptography (offline): RS256 JWTs signed with a test key, verified by google-auth-library.
const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const kid = "test-kid";
const certs = { [kid]: publicKey.export({ type: "spki", format: "pem" }).toString() };
const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString("base64url");
function jwt(claims: Record<string, unknown>, key = privateKey) {
  const head = b64({ alg: "RS256", typ: "JWT", kid }), body = b64(claims);
  const sig = createSign("RSA-SHA256").update(`${head}.${body}`).sign(key).toString("base64url");
  return `${head}.${body}.${sig}`;
}
const now = () => Math.floor(Date.now() / 1000);
const claims = (over: Record<string, unknown> = {}) => ({ iss: "https://accounts.google.com", aud: RIDER_AUDIENCE, sub: "123",
  email: RIDER_INVOKER, email_verified: true, iat: now() - 10, exp: now() + 600, ...over });
const realVerifier: IdTokenVerifier = async (token, audience) =>
  ((await new OAuth2Client().verifySignedJwtWithCertsAsync(token, certs, audience, [...GOOGLE_ISSUERS])).getPayload() ?? {}) as IdTokenClaims;

test("G7-B crypto: a correctly signed Rider token passes; signature, expiry, issuer and audience are enforced cryptographically", async () => {
  await assertRiderCaller(bearer(jwt(claims())), realVerifier);
  const tampered = jwt(claims()).split("."); tampered[1] = b64(claims({ email: "attacker@example.com" }));
  await denied(assertRiderCaller(bearer(tampered.join(".")), realVerifier)); // payload changed, signature no longer valid
  const other = generateKeyPairSync("rsa", { modulusLength: 2048 }).privateKey;
  await denied(assertRiderCaller(bearer(jwt(claims(), other)), realVerifier)); // signed by an unknown key
  await denied(assertRiderCaller(bearer(jwt(claims({ exp: now() - 3600, iat: now() - 7200 }))), realVerifier)); // expired
  await denied(assertRiderCaller(bearer(jwt(claims({ iss: "https://evil.example" }))), realVerifier)); // issuer
  await denied(assertRiderCaller(bearer(jwt(claims({ aud: "https://foodapi-u65lcqarqa-tl.a.run.app" }))), realVerifier)); // audience
  await denied(assertRiderCaller(bearer(jwt(claims({ email: "220568639274-compute@developer.gserviceaccount.com" }))), realVerifier));
  await denied(assertRiderCaller(bearer(jwt(claims({ email_verified: false }))), realVerifier));
});

// ---- Handler integration (CLOUD path): rejection happens before any business code or Firestore access.
function call(handler: ReturnType<typeof createRiderApiHandler>, headers: Record<string, string>) {
  const res: any = { statusCode: 0, body: undefined, set() { return res; }, status(c: number) { res.statusCode = c; return res; },
    json(b: unknown) { res.body = b; return res; } };
  return (handler as any)({ path: "/integration/v1/orders", method: "GET", headers, query: {}, body: undefined }, res).then(() => res);
}
test("G7-B handler: CLOUD requires the Rider caller (403 CALLER_NOT_ALLOWED, no app/Firestore touched); LOCAL emulator path unchanged", async () => {
  let appTouched = 0;
  const deps = (mode: "CLOUD" | "LOCAL", verifier: IdTokenVerifier) => createRiderApiHandler({
    runtime: () => ({ mode, projectId: mode === "CLOUD" ? "chaski-food-9adba" : "demo-chaski-hu15" }),
    app: () => { appTouched++; throw new Error("STOP_AFTER_AUTH"); }, activation: () => ({ mode: "DISABLED" }), callerVerifier: verifier });
  const r1 = await call(deps("CLOUD", fake({ ...rider, email: "x@developer.gserviceaccount.com" })), bearer());
  assert.equal(r1.statusCode, 403); assert.deepEqual(r1.body, { error: { code: "CALLER_NOT_ALLOWED", retryable: false } });
  const r2 = await call(deps("CLOUD", fake(rider)), {});
  assert.equal(r2.statusCode, 403); assert.equal(appTouched, 0);
  const r3 = await call(deps("CLOUD", fake(rider)), bearer());
  assert.equal(appTouched, 1); assert.equal(r3.statusCode, 503); // passed the caller check, then reached app (stubbed)
  let verifierCalls = 0;
  const r4 = await call(deps("LOCAL", async () => { verifierCalls++; return rider; }), {});
  assert.equal(verifierCalls, 0); assert.equal(appTouched, 2); assert.equal(r4.statusCode, 503); // emulator: no verifier
  // A client header can never switch the mode: CLOUD + spoofed emulator-like headers is still rejected.
  const r5 = await call(deps("CLOUD", fake(new Error("bad"))), { ...bearer(), "x-forwarded-for": "127.0.0.1", "x-functions-emulator": "true" });
  assert.equal(r5.statusCode, 403);
});
