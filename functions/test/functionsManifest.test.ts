import { test } from "node:test";
import assert from "node:assert/strict";
import * as functions from "../src/index";
import { RIDER_INVOKER } from "../src/integration/riderActivation";

/** E6/G7-A: the deploy manifest decides the Cloud Run invoker binding; it must be exactly the Rider service account. */
const endpoint = (fn: unknown) => (fn as { __endpoint: any }).__endpoint;

test("G7-A: riderApi declares only the Rider service account as direct invoker (never public, never empty)", () => {
  const rider = endpoint(functions.riderApi);
  assert.deepEqual(rider.httpsTrigger.invoker, [RIDER_INVOKER]);
  assert.equal(RIDER_INVOKER, "chaski-food-client@chaski-rider.iam.gserviceaccount.com");
  for (const forbidden of ["public", "private", "allUsers", "allAuthenticatedUsers"])
    assert.equal(rider.httpsTrigger.invoker.includes(forbidden), false, forbidden);
  assert.ok(rider.httpsTrigger.invoker.every((m: string) => m.endsWith(".iam.gserviceaccount.com")), "service accounts only");
  assert.deepEqual(rider.region, ["southamerica-west1"]);
});

test("G7-A: foodApi keeps its public invoker and newOrderNotifier is untouched (event trigger, no invoker)", () => {
  assert.deepEqual(endpoint(functions.foodApi).httpsTrigger.invoker, ["public"]);
  const notifier = endpoint(functions.newOrderNotifier);
  assert.equal(notifier.httpsTrigger, undefined); assert.ok(notifier.eventTrigger);
});
