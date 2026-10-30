import { initializeApp } from "firebase-admin/app";

export type RuntimeEnvironment = { mode: "LOCAL" | "CLOUD"; projectId: string };
export class RuntimeConfigurationError extends Error {
  constructor() { super("RUNTIME_CONFIGURATION_INVALID"); }
}

const localProject = "demo-chaski-hu15";
const localFirestore = "127.0.0.1:8089";
const localAuth = "127.0.0.1:9099";

/** Require several independent platform signals before Admin SDK can access any data. */
export function resolveRuntimeEnvironment(env: NodeJS.ProcessEnv): RuntimeEnvironment {
  const projectId = env.GCLOUD_PROJECT || env.GOOGLE_CLOUD_PROJECT;
  if (env.GCLOUD_PROJECT && env.GOOGLE_CLOUD_PROJECT && env.GCLOUD_PROJECT !== env.GOOGLE_CLOUD_PROJECT) {
    throw new RuntimeConfigurationError();
  }
  if (env.FUNCTIONS_EMULATOR === "true") {
    if (projectId !== localProject || env.FIRESTORE_EMULATOR_HOST !== localFirestore ||
        env.FIREBASE_AUTH_EMULATOR_HOST !== localAuth ||
        (env.CHASKI_ROUTE_FIXTURE && env.CHASKI_ROUTE_FIXTURE !== "enabled")) {
      throw new RuntimeConfigurationError();
    }
    return { mode: "LOCAL", projectId };
  }
  if (env.FUNCTIONS_EMULATOR || !env.K_SERVICE || projectId !== "chaski-food-9adba" ||
      env.FIRESTORE_EMULATOR_HOST || env.FIREBASE_AUTH_EMULATOR_HOST || env.FIREBASE_DATABASE_EMULATOR_HOST ||
      env.FIREBASE_STORAGE_EMULATOR_HOST || env.FUNCTIONS_EMULATOR_HOST || env.CHASKI_ROUTE_FIXTURE || env.CHASKI_NOTIFICATIONS_FIXTURE ||
      env.FUNCTION_TARGET !== "foodApi") {
    throw new RuntimeConfigurationError();
  }
  return { mode: "CLOUD", projectId };
}

export function routeSourceForRuntime(runtime: RuntimeEnvironment, fixture: string | undefined): "HEIGIT" | "SIMULATED" {
  if (runtime.mode === "CLOUD") {
    if (fixture) throw new RuntimeConfigurationError();
    return "HEIGIT";
  }
  if (fixture && fixture !== "enabled") throw new RuntimeConfigurationError();
  return fixture === "enabled" ? "SIMULATED" : "HEIGIT";
}

export function initializeRuntimeApp(runtime: RuntimeEnvironment) {
  return initializeApp({ projectId: runtime.projectId });
}
