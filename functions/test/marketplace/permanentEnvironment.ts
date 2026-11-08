import { applicationDefault, App, deleteApp, initializeApp } from "firebase-admin/app";
import { Auth, getAuth } from "firebase-admin/auth";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { MarketplaceBucket } from "./environment";

export const PERMANENT_PROJECT = "chaski-food-9adba";
export const PERMANENT_BUCKET = "chaski-food-9adba.firebasestorage.app";

export type PermanentContext = Readonly<{ app: App; auth: Auth; db: Firestore; bucket: MarketplaceBucket }>;

export function openPermanentMarketplace(): PermanentContext {
  if (process.env.CHASKI_PERMANENT_SEED_CONFIRM !== PERMANENT_PROJECT) throw new Error("PERMANENT_PROJECT_CONFIRMATION_REQUIRED");
  if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.STORAGE_EMULATOR_HOST) {
    throw new Error("EMULATOR_VARIABLES_NOT_ALLOWED");
  }
  const password = process.env.CHASKI_MARKETPLACE_PASSWORD ?? "";
  if (password.length < 16) throw new Error("STRONG_MARKETPLACE_PASSWORD_REQUIRED");
  const app = initializeApp({ credential: applicationDefault(), projectId: PERMANENT_PROJECT,
    storageBucket: PERMANENT_BUCKET }, "chaski-marketplace-permanent");
  return { app, auth: getAuth(app), db: getFirestore(app), bucket: getStorage(app).bucket() };
}

export async function closePermanentMarketplace(context: PermanentContext): Promise<void> {
  await context.db.terminate();
  await deleteApp(context.app);
}
