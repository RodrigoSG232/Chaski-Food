import { randomUUID } from "node:crypto";
import { resolve } from "node:path";
import { Auth } from "firebase-admin/auth";
import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../../src/access/commercialAccessService";
import { digest } from "../../src/access/commercialAccessPolicy";
import { CommercialIdentity } from "../../src/auth/verifyBuyerToken";
import { BUSINESSES } from "./catalog";
import { MarketplaceBucket, MARKETPLACE_BUCKET, MARKETPLACE_NAMESPACE, MARKETPLACE_PASSWORD } from "./environment";

const TEMP_ADMIN = { uid: "marketplace-seed-admin", email: "marketplace.seed.admin@chaski.test" };
const prepareOperation = (uid: string) => `marketplace-prepare-${uid}`;
const assignmentOperation = (businessId: string) => `marketplace-assign-${businessId}`;
const auditId = (actorUid: string, operationId: string) => `access_${digest([actorUid, operationId])}`;
const emailIndex = (email: string) => `email_${digest(email.toLowerCase())}`;

function imageUrl(path: string, token: string): string {
  return `http://127.0.0.1:9199/v0/b/${MARKETPLACE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
}

async function uploadImage(bucket: MarketplaceBucket, source: string, destination: string): Promise<string> {
  const token = randomUUID();
  const asset = resolve(__dirname, "../../../test/marketplace/assets", source);
  await bucket.upload(asset, {
    destination,
    metadata: { contentType: "image/jpeg", metadata: { firebaseStorageDownloadTokens: token, seedNamespace: MARKETPLACE_NAMESPACE } },
  });
  return imageUrl(destination, token);
}

export async function resetMarketplace(db: Firestore, auth: Auth, bucket: MarketplaceBucket): Promise<void> {
  const documentPaths = BUSINESSES.flatMap(business => [
    `business_requests/${business.id}`,
    `store_managers/${business.id}_${business.managerEmail}`,
    ...business.stores.map(store => `stores/${store.id}`),
    ...business.categories.map(category => `categories/${category.id}`),
    ...business.products.map(product => `products/${product.id}`),
    ...business.stores.flatMap(store => business.products.map(product => `store_product_configs/${store.id}_${product.id}`)),
    ...[business.id, business.managerUid].flatMap(uid => [
      `commercial_access_index/uid_${uid}`,
      `commercial_access_index/${emailIndex(uid === business.id ? business.ownerEmail : business.managerEmail)}`,
      `audit_logs/${auditId(TEMP_ADMIN.uid, prepareOperation(uid))}`,
    ]),
    `audit_logs/${auditId(business.id, assignmentOperation(business.id))}`,
  ]);
  for (const path of documentPaths) await db.recursiveDelete(db.doc(path));
  for (const business of BUSINESSES) {
    const [files] = await bucket.getFiles({ prefix: `businesses/${business.id}/` });
    await Promise.all(files.map(file => file.delete({ ignoreNotFound: true })));
    for (const uid of [business.id, business.managerUid]) {
      await auth.deleteUser(uid).catch(error => {
        if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
      });
    }
  }
  await auth.deleteUser(TEMP_ADMIN.uid).catch(error => {
    if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
  });
}

export async function seedMarketplace(db: Firestore, auth: Auth, bucket: MarketplaceBucket): Promise<void> {
  await resetMarketplace(db, auth, bucket);
  await auth.createUser({ uid: TEMP_ADMIN.uid, email: TEMP_ADMIN.email, password: MARKETPLACE_PASSWORD, emailVerified: true });
  await auth.setCustomUserClaims(TEMP_ADMIN.uid, { admin: true });
  for (const business of BUSINESSES) {
    await auth.createUser({ uid: business.id, email: business.ownerEmail, password: MARKETPLACE_PASSWORD,
      emailVerified: true, displayName: business.ownerName, phoneNumber: business.ownerPhone });
    await auth.createUser({ uid: business.managerUid, email: business.managerEmail, password: MARKETPLACE_PASSWORD,
      emailVerified: true, displayName: business.managerName, phoneNumber: business.managerPhone });
  }

  const now = Timestamp.now();
  const hours = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    .map(dayOfWeek => ({ dayOfWeek, enabled: true, openTime: "08:00", closeTime: "23:00" }));
  for (const business of BUSINESSES) {
    await db.doc(`business_requests/${business.id}`).set({
      ownerUid: business.id, businessName: business.businessName, status: "APPROVED", ruc: business.ruc,
      address: business.stores[0].address, phone: business.ownerPhone, contactEmail: business.ownerEmail,
      category: "RESTAURANT", seedNamespace: MARKETPLACE_NAMESPACE, seedVersion: 1, environment: "DEMO",
      createdAt: now, updatedAt: now,
    });
    for (const store of business.stores) {
      const path = `businesses/${business.id}/stores/${store.id}/cover.jpg`;
      const url = await uploadImage(bucket, business.image, path);
      await db.doc(`stores/${store.id}`).set({
        businessId: business.id, name: store.name, imageUrl: url, address: store.address, phone: store.phone,
        latitude: store.latitude, longitude: store.longitude, deliveryRadiusKm: 8,
        status: "ACTIVE", operationalStatus: "OPEN", operatingHours: hours,
        seedNamespace: MARKETPLACE_NAMESPACE, seedVersion: 1, environment: "DEMO", createdAt: now, updatedAt: now,
      });
    }
    for (const [index, category] of business.categories.entries()) {
      await db.doc(`categories/${category.id}`).set({
        businessId: business.id, name: category.name, isActive: true, displayOrder: index + 1,
        seedNamespace: MARKETPLACE_NAMESPACE, seedVersion: 1, environment: "DEMO", createdAt: now, updatedAt: now,
      });
    }
    for (const [index, product] of business.products.entries()) {
      const categoryName = business.categories.find(category => category.id === product.category)?.name ?? "";
      const path = `businesses/${business.id}/products/${product.id}/image.jpg`;
      const url = await uploadImage(bucket, product.image, path);
      await db.doc(`products/${product.id}`).set({
        businessId: business.id, categoryId: product.category, categoryName, name: product.name,
        description: product.description, price: product.price, imageUrl: url,
        prepTimeMinutes: product.prepTimeMinutes, isActive: true, displayOrder: index + 1,
        seedNamespace: MARKETPLACE_NAMESPACE, seedVersion: 1, environment: "DEMO",
        createdAt: Date.now() + index, updatedAt: now,
      });
      for (const [storeIndex, store] of business.stores.entries()) {
        await db.doc(`store_product_configs/${store.id}_${product.id}`).set({
          businessId: business.id, storeId: store.id, productId: product.id,
          price: Number((product.price + storeIndex).toFixed(2)),
          availability: storeIndex === 2 && index % 4 === 0 ? "OUT_OF_STOCK" : "AVAILABLE",
          seedNamespace: MARKETPLACE_NAMESPACE, seedVersion: 1, environment: "DEMO",
          updatedBy: business.id, updatedAt: now,
        });
      }
    }
  }

  const admin: CommercialIdentity = { ...TEMP_ADMIN, emailVerified: true, admin: true };
  const access = new CommercialAccessService(db, auth);
  const revisions = new Map<string, number>();
  for (const business of BUSINESSES) {
    for (const account of [
      { uid: business.id, email: business.ownerEmail },
      { uid: business.managerUid, email: business.managerEmail },
    ]) {
      const result = await access.execute(admin, { action: "PREPARE", operationId: prepareOperation(account.uid),
        email: account.email, expectedRevision: 0 });
      revisions.set(account.uid, result.revision);
    }
    const owner: CommercialIdentity = { uid: business.id, email: business.ownerEmail, emailVerified: true, admin: false };
    await access.execute(owner, { action: "ASSIGN", operationId: assignmentOperation(business.id),
      businessId: business.id, email: business.managerEmail, expectedRevision: revisions.get(business.managerUid)!,
      storeIds: [business.assignedStoreId], fullName: business.managerName, phone: business.managerPhone });
  }
  await auth.deleteUser(TEMP_ADMIN.uid);
}
