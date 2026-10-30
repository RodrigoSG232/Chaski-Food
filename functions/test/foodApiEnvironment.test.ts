import { test } from "node:test";
import assert from "node:assert/strict";
import { createFoodApiHandler } from "../src/foodApiHandler";
import { resolveRuntimeEnvironment } from "../src/runtimeEnvironment";

async function probe(path: string, method: string, configured: boolean) {
  let sdkCalls = 0;
  const handler = createFoodApiHandler({
    runtime: () => resolveRuntimeEnvironment(configured ? {
      K_SERVICE: "foodapi", FUNCTION_TARGET: "foodApi", GCLOUD_PROJECT: "chaski-food-9adba",
    } : { GCLOUD_PROJECT: "chaski-food-9adba" }),
    app: () => { sdkCalls++; throw new Error("SDK_MUST_NOT_BE_CALLED"); },
    routeReader: () => { throw new Error("ROUTING_MUST_NOT_BE_CALLED"); },
  });
  const result = { status: 200, body: {} as Record<string, unknown> };
  const response = { set() { return this; }, status(code: number) { result.status=code; return this; },
    json(body: Record<string, unknown>) { result.body=body; return this; }, end() { return this; } };
  await handler({ path, method } as unknown as Parameters<typeof handler>[0],
    response as unknown as Parameters<typeof handler>[1]);
  assert.equal(sdkCalls,0);
  return result;
}

test("B1 keeps CLOUD device registration closed until B2, without accessing SDK or FCM",async()=>{
  for (const method of ["PUT","DELETE"]) {
    const result = await probe("/api/v1/devices/fixture-device",method,true);
    assert.equal(result.status,404);assert.deepEqual(result.body,{error:"NOT_FOUND"});
  }
});
test("unconfigured runtime never reaches auth, data or routing services",async()=>{
  for(const path of ["/api/v1/access/facts","/api/v1/orders","/api/v1/order-attempts/fixture","/api/v1/stores/fixture/orders"])
    assert.equal((await probe(path,"GET",false)).status,503);
});
