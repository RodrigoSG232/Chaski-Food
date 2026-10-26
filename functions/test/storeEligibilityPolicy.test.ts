import { test } from "node:test";
import assert from "node:assert/strict";
import { evaluateEligibility, InconsistentData } from "../src/eligibility/storeEligibilityPolicy";

const store = { businessId: "business", status: "ACTIVE" };
for (const [status, expected] of Object.entries({ APPROVED: "ELIGIBLE", SUSPENDED: "BUSINESS_SUSPENDED",
  PENDING_REVIEW: "BUSINESS_NOT_APPROVED", OBSERVED: "BUSINESS_NOT_APPROVED", REJECTED: "BUSINESS_NOT_APPROVED" })) {
  test(`negocio ${status}`, () => assert.equal(evaluateEligibility(store, { status }), expected));
}
for (const status of ["INACTIVE", "SUSPENDED"]) {
  test(`local ${status}`, () => assert.equal(evaluateEligibility({ ...store, status }, { status: "APPROVED" }), "STORE_UNAVAILABLE"));
}
test("documentos ausentes", () => {
  assert.equal(evaluateEligibility(undefined, undefined), "STORE_UNAVAILABLE");
  assert.equal(evaluateEligibility(store, undefined), "STORE_UNAVAILABLE");
});
test("datos inconsistentes nunca habilitan", () => {
  for (const bad of [{ status: "ACTIVE" }, { businessId: "a/b", status: "ACTIVE" }, { ...store, status: "UNKNOWN" }]) {
    assert.throws(() => evaluateEligibility(bad, { status: "APPROVED" }), InconsistentData);
  }
  assert.throws(() => evaluateEligibility(store, { status: "UNKNOWN" }), InconsistentData);
});
test("operación es pura e ignora privilegios comerciales", () => {
  const business = Object.freeze({ status: "SUSPENDED", admin: true });
  assert.equal(evaluateEligibility(Object.freeze(store), business), "BUSINESS_SUSPENDED");
});
