import { App, deleteApp, initializeApp } from "firebase-admin/app";
import { Auth, getAuth } from "firebase-admin/auth";
import { Firestore, getFirestore } from "firebase-admin/firestore";

export const LAB_PROJECT = "demo-chaski-hu15";
export const LAB_PASSWORD = "emulator-password";

export type LabContext = Readonly<{ app: App; auth: Auth; db: Firestore }>;

export function requireLocalLab(): void {
  if (process.env.GCLOUD_PROJECT !== LAB_PROJECT ||
      process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8089" ||
      process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099") {
    throw new Error("LOCAL_EMULATORS_REQUIRED");
  }
}

export function openLab(name: string): LabContext {
  requireLocalLab();
  const app = initializeApp({ projectId: LAB_PROJECT }, name);
  return { app, auth: getAuth(app), db: getFirestore(app) };
}

export async function closeLab(context: LabContext): Promise<void> {
  await context.db.terminate();
  await deleteApp(context.app);
}
