import { App, deleteApp, initializeApp } from "firebase-admin/app";
import { Auth, getAuth } from "firebase-admin/auth";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

export const MARKETPLACE_PROJECT = "demo-chaski-hu15";
export const MARKETPLACE_BUCKET = `${MARKETPLACE_PROJECT}.appspot.com`;
export const MARKETPLACE_PASSWORD = "emulator-password";
export const MARKETPLACE_NAMESPACE = "marketplace-demo";

export type MarketplaceBucket = ReturnType<ReturnType<typeof getStorage>["bucket"]>;
export type MarketplaceContext = Readonly<{ app: App; auth: Auth; db: Firestore; bucket: MarketplaceBucket }>;

export function requireLocalMarketplace(): void {
  if (process.env.GCLOUD_PROJECT !== MARKETPLACE_PROJECT ||
      process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8089" ||
      process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099" ||
      process.env.STORAGE_EMULATOR_HOST !== "http://127.0.0.1:9199") {
    throw new Error("LOCAL_AUTH_FIRESTORE_STORAGE_EMULATORS_REQUIRED");
  }
}

export function openMarketplace(name: string): MarketplaceContext {
  requireLocalMarketplace();
  const app = initializeApp({ projectId: MARKETPLACE_PROJECT, storageBucket: MARKETPLACE_BUCKET }, name);
  return { app, auth: getAuth(app), db: getFirestore(app), bucket: getStorage(app).bucket() };
}

export async function closeMarketplace(context: MarketplaceContext): Promise<void> {
  await context.db.terminate();
  await deleteApp(context.app);
}
