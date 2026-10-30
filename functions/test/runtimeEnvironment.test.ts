import { test } from "node:test";
import assert from "node:assert/strict";
import { resolveRuntimeEnvironment, routeSourceForRuntime, RuntimeConfigurationError } from "../src/runtimeEnvironment";

const local = {
  FUNCTIONS_EMULATOR: "true", GCLOUD_PROJECT: "demo-chaski-hu15",
  FIRESTORE_EMULATOR_HOST: "127.0.0.1:8089", FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099",
};
const cloud = { K_SERVICE: "foodapi", FUNCTION_TARGET: "foodApi", GCLOUD_PROJECT: "chaski-food-9adba" };

test("LOCAL exige el proyecto ficticio y ambos emuladores", () => {
  const runtime = resolveRuntimeEnvironment(local);
  assert.deepEqual(runtime, { mode: "LOCAL", projectId: "demo-chaski-hu15" });
  assert.equal(routeSourceForRuntime(runtime, "enabled"), "SIMULATED");
  assert.equal(routeSourceForRuntime(runtime, undefined), "HEIGIT");
  assert.deepEqual(resolveRuntimeEnvironment({ ...local, K_SERVICE: "foodapi" }), runtime);
  for (const invalid of [
    { ...local, GCLOUD_PROJECT: "chaski-food-9adba" },
    { ...local, FIRESTORE_EMULATOR_HOST: undefined },
    { ...local, FIREBASE_AUTH_EMULATOR_HOST: "localhost:9099" },
    { ...local, CHASKI_ROUTE_FIXTURE: "invalid" },
  ]) assert.throws(() => resolveRuntimeEnvironment(invalid), RuntimeConfigurationError);
});

test("CLOUD exige señales de Functions y un proyecto real sin hosts de Emulator", () => {
  const runtime = resolveRuntimeEnvironment(cloud);
  assert.deepEqual(runtime, { mode: "CLOUD", projectId: "chaski-food-9adba" });
  assert.equal(routeSourceForRuntime(runtime, undefined), "HEIGIT");
  for (const invalid of [
    { ...cloud, K_SERVICE: undefined },
    { ...cloud, GCLOUD_PROJECT: "demo-chaski-hu15" },
    { ...cloud, GCLOUD_PROJECT: undefined },
    { ...cloud, GOOGLE_CLOUD_PROJECT: "different-project" },
    { ...cloud, FUNCTIONS_EMULATOR: "true" },
    { ...cloud, FIRESTORE_EMULATOR_HOST: "127.0.0.1:8089" },
    { ...cloud, FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" },
    { ...cloud, CHASKI_ROUTE_FIXTURE: "enabled" },
    { ...cloud, FUNCTION_TARGET: "anotherFunction" },
    { ...cloud, FUNCTION_TARGET: undefined },
    { ...cloud, GCLOUD_PROJECT: "another-project" },
    { ...cloud, CHASKI_NOTIFICATIONS_FIXTURE: "enabled" },
  ]) assert.throws(() => resolveRuntimeEnvironment(invalid), RuntimeConfigurationError);
  assert.throws(() => routeSourceForRuntime(runtime, "enabled"), RuntimeConfigurationError);
});

test("ningún entorno incompleto puede inicializar Firebase Admin", () => {
  assert.throws(() => resolveRuntimeEnvironment({}), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment({ GCLOUD_PROJECT: "chaski-food-9adba" }), RuntimeConfigurationError);
});
