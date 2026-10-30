import { test } from "node:test";
import assert from "node:assert/strict";
import { App } from "firebase-admin/app";
import { FunctionTarget, resolveRuntimeEnvironment, routeSourceForRuntime, RuntimeConfigurationError } from "../src/runtimeEnvironment";
import { FirebaseNoticeSender, noticeSenderForRuntime, SimulatedNoticeSender } from "../src/notifications/fcmSender";

const local = {
  FUNCTIONS_EMULATOR: "true", GCLOUD_PROJECT: "demo-chaski-hu15",
  FIRESTORE_EMULATOR_HOST: "127.0.0.1:8089", FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099",
};
const cloud = { K_SERVICE: "foodapi", FUNCTION_TARGET: "foodApi", GCLOUD_PROJECT: "chaski-food-9adba" };
const notifier = { K_SERVICE: "newordernotifier", FUNCTION_TARGET: "newOrderNotifier", GCLOUD_PROJECT: "chaski-food-9adba" };

test("LOCAL exige el proyecto ficticio y ambos emuladores", () => {
  const runtime = resolveRuntimeEnvironment(local, "foodApi");
  assert.deepEqual(runtime, { mode: "LOCAL", projectId: "demo-chaski-hu15" });
  assert.equal(routeSourceForRuntime(runtime, "enabled"), "SIMULATED");
  assert.equal(routeSourceForRuntime(runtime, undefined), "HEIGIT");
  assert.deepEqual(resolveRuntimeEnvironment({ ...local, K_SERVICE: "foodapi" }, "foodApi"), runtime);
  assert.deepEqual(resolveRuntimeEnvironment(local, "newOrderNotifier"), runtime);
  for (const invalid of [
    { ...local, GCLOUD_PROJECT: "chaski-food-9adba" },
    { ...local, FIRESTORE_EMULATOR_HOST: undefined },
    { ...local, FIREBASE_AUTH_EMULATOR_HOST: "localhost:9099" },
    { ...local, CHASKI_ROUTE_FIXTURE: "invalid" },
  ]) assert.throws(() => resolveRuntimeEnvironment(invalid, "foodApi"), RuntimeConfigurationError);
});

test("CLOUD exige señales de Functions y un proyecto real sin hosts de Emulator", () => {
  const runtime = resolveRuntimeEnvironment(cloud, "foodApi");
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
  ]) assert.throws(() => resolveRuntimeEnvironment(invalid, "foodApi"), RuntimeConfigurationError);
  assert.throws(() => routeSourceForRuntime(runtime, "enabled"), RuntimeConfigurationError);
});

test("CLOUD exige que FUNCTION_TARGET corresponda a la función que se ejecuta", () => {
  assert.deepEqual(resolveRuntimeEnvironment(notifier, "newOrderNotifier"), { mode: "CLOUD", projectId: "chaski-food-9adba" });
  assert.throws(() => resolveRuntimeEnvironment(notifier, "foodApi"), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment(cloud, "newOrderNotifier"), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment(cloud, "anotherFunction" as FunctionTarget), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment({ ...notifier, FUNCTION_TARGET: "anotherFunction" } as NodeJS.ProcessEnv,
    "anotherFunction" as FunctionTarget), RuntimeConfigurationError);
  for (const invalid of [
    { ...notifier, CHASKI_NOTIFICATIONS_FIXTURE: "enabled" },
    { ...notifier, FIRESTORE_EMULATOR_HOST: "127.0.0.1:8089" },
    { ...notifier, K_SERVICE: undefined },
  ]) assert.throws(() => resolveRuntimeEnvironment(invalid, "newOrderNotifier"), RuntimeConfigurationError);
});

test("el emisor de avisos es real solo en CLOUD y simulado solo con fixture LOCAL", () => {
  const app = {} as App;
  const cloudRuntime = resolveRuntimeEnvironment(notifier, "newOrderNotifier");
  const localRuntime = resolveRuntimeEnvironment(local, "newOrderNotifier");
  assert.ok(noticeSenderForRuntime(cloudRuntime, undefined, app) instanceof FirebaseNoticeSender);
  assert.throws(() => noticeSenderForRuntime(cloudRuntime, "enabled", app), RuntimeConfigurationError);
  assert.throws(() => noticeSenderForRuntime(cloudRuntime, "", app), RuntimeConfigurationError);
  assert.ok(noticeSenderForRuntime(localRuntime, "enabled", app) instanceof SimulatedNoticeSender);
  assert.equal(noticeSenderForRuntime(localRuntime, undefined, app), null);
  assert.throws(() => noticeSenderForRuntime(localRuntime, "invalid", app), RuntimeConfigurationError);
});

test("ningún entorno incompleto puede inicializar Firebase Admin", () => {
  assert.throws(() => resolveRuntimeEnvironment({}, "foodApi"), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment({ GCLOUD_PROJECT: "chaski-food-9adba" }, "foodApi"), RuntimeConfigurationError);
  assert.throws(() => resolveRuntimeEnvironment({ GCLOUD_PROJECT: "chaski-food-9adba" }, "newOrderNotifier"), RuntimeConfigurationError);
});
