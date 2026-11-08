import { Auth } from "firebase-admin/auth";
import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../../src/access/commercialAccessService";
import { CommercialIdentity } from "../../src/auth/verifyBuyerToken";
import { digest } from "../../src/access/commercialAccessPolicy";
import { LAB_PASSWORD } from "./labEnvironment";

export const LAB = Object.freeze({
  admin: { uid: "lab-admin", email: "admin@chaski.test" },
  owner: { uid: "lab-owner", email: "owner@chaski.test" },
  manager: { uid: "lab-manager", email: "manager@chaski.test" },
  buyer: { uid: "lab-buyer", email: "buyer@chaski.test" },
  businessId: "lab-owner",
  stores: ["lab-store-central", "lab-store-north"],
  categoryId: "lab-category-main",
  products: ["lab-product-one", "lab-product-two"],
  orders: {
    pending: digest("lab-order-pending"),
    preparing: digest("lab-order-preparing"),
    preparingWithDelivery: digest("lab-order-preparing-delivery"),
  },
});

const actors = [LAB.admin, LAB.owner, LAB.manager, LAB.buyer];
const prepareOperation = (uid: string) => `lab-prepare-${uid}`;
const assignmentOperation = "lab-assign-manager";

function auditId(actorUid: string, operationId: string): string {
  return `access_${digest([actorUid, operationId])}`;
}

export async function resetLab(db: Firestore, auth: Auth): Promise<void> {
  const exactPaths = [
    `business_requests/${LAB.businessId}`,
    ...LAB.stores.map(id => `stores/${id}`),
    `categories/${LAB.categoryId}`,
    ...LAB.products.map(id => `products/${id}`),
    ...LAB.stores.flatMap(store => LAB.products.map(product => `store_product_configs/${store}_${product}`)),
    `users/${LAB.buyer.uid}`,
    `store_managers/${LAB.businessId}_${LAB.manager.email}`,
    ...actors.flatMap(actor => [
      `commercial_access_index/uid_${actor.uid}`,
      `commercial_access_index/email_${digest(actor.email)}`,
      `audit_logs/${auditId(LAB.admin.uid, prepareOperation(actor.uid))}`,
    ]),
    `audit_logs/${auditId(LAB.owner.uid, assignmentOperation)}`,
  ];
  for (const path of exactPaths) await db.recursiveDelete(db.doc(path));
  const orders = await db.collection("food_orders").where("labScenario", "==", true).get();
  for (const order of orders.docs) await db.recursiveDelete(order.ref);
  const riderEvents = await db.collection("rider_events").where("labScenario", "==", true).get();
  for (const event of riderEvents.docs) await db.recursiveDelete(event.ref);
  for (const actor of actors) await auth.deleteUser(actor.uid).catch(error => {
    if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
  });
}

export async function seedBase(db: Firestore, auth: Auth): Promise<void> {
  await resetLab(db, auth);
  for (const actor of actors) {
    await auth.createUser({ uid: actor.uid, email: actor.email, password: LAB_PASSWORD,
      emailVerified: true, displayName: actor.uid === LAB.manager.uid ? "Responsable laboratorio" : "Usuario laboratorio" });
  }
  await auth.setCustomUserClaims(LAB.admin.uid, { admin: true });
  const admin: CommercialIdentity = { ...LAB.admin, emailVerified: true, admin: true };
  const owner: CommercialIdentity = { ...LAB.owner, emailVerified: true, admin: false };
  const access = new CommercialAccessService(db, auth);
  const revisions = new Map<string, number>();
  for (const actor of actors) {
    const result = await access.execute(admin, { action: "PREPARE", operationId: prepareOperation(actor.uid),
      email: actor.email, expectedRevision: 0 });
    revisions.set(actor.uid, result.revision);
  }
  await db.doc(`business_requests/${LAB.businessId}`).set({
    ownerUid: LAB.owner.uid, businessName: "Chaski Lab Food", status: "APPROVED", ruc: "20000000001",
    address: "Lima laboratorio", phone: "900000001", contactEmail: LAB.owner.email,
    category: "RESTAURANT", labScenario: true, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
  });
  const now = Timestamp.now();
  const hours = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    .map(dayOfWeek => ({ dayOfWeek, enabled: true, openTime: "00:00", closeTime: "23:59" }));
  for (const [index, storeId] of LAB.stores.entries()) {
    await db.doc(`stores/${storeId}`).set({ businessId: LAB.businessId,
      name: index === 0 ? "Local Central Lab" : "Local Norte Lab", address: `Dirección laboratorio ${index + 1}`,
      phone: `90000000${index + 2}`, status: "ACTIVE", operationalStatus: "OPEN",
      latitude: -12.0464 + index * 0.001, longitude: -77.0428, deliveryRadiusKm: 10,
      operatingHours: hours, labScenario: true, createdAt: now, updatedAt: now });
  }
  await db.doc(`categories/${LAB.categoryId}`).set({ businessId: LAB.businessId, name: "Platos Lab",
    isActive: true, displayOrder: 1, labScenario: true, createdAt: now, updatedAt: now });
  for (const [index, productId] of LAB.products.entries()) {
    await db.doc(`products/${productId}`).set({ businessId: LAB.businessId, categoryId: LAB.categoryId,
      name: index === 0 ? "Plato de prueba" : "Bebida de prueba", description: "Producto exclusivo del laboratorio local",
      price: (index + 1) * 10, prepTimeMinutes: 15, isActive: true, displayOrder: index + 1,
      labScenario: true, createdAt: now, updatedAt: now });
    for (const storeId of LAB.stores) await db.doc(`store_product_configs/${storeId}_${productId}`).set({
      businessId: LAB.businessId, storeId, productId, price: (index + 1) * 10, availability: "AVAILABLE",
      labScenario: true, updatedBy: LAB.owner.uid, updatedAt: now });
  }
  await db.doc(`users/${LAB.buyer.uid}`).set({ uid: LAB.buyer.uid, displayName: "Cliente laboratorio", labScenario: true });
  await db.doc(`users/${LAB.buyer.uid}/addresses/lab-address`).set({ label: "Casa Lab", addressText: "Parque de laboratorio, Lima",
    location: { lat: -12.05, lng: -77.04 }, reference: "Puerta principal", instructions: "Llamar al llegar",
    labScenario: true, createdAt: now, updatedAt: now });
  await access.execute(owner, { action: "ASSIGN", operationId: assignmentOperation, businessId: LAB.businessId,
    email: LAB.manager.email, expectedRevision: revisions.get(LAB.manager.uid)!, storeIds: LAB.stores,
    fullName: "Responsable laboratorio", phone: "900000004" });
}

function orderData(orderId: string, status: "PENDING_CONFIRMATION" | "PREPARING", createdAt: Timestamp,
    minutes?: number, deliveryId?: string) {
  const subtotalMinor = 2000, deliveryFeeMinor = 300, totalMinor = 2300;
  const updatedAt = Timestamp.fromMillis(createdAt.toMillis() + (status === "PREPARING" ? 60_000 : 0));
  return { schemaVersion: 1, orderId, intentId: `lab-intent-${orderId.slice(0, 12)}`,
    requestHash: digest(["lab-request", orderId]), buyerUid: LAB.buyer.uid, businessId: LAB.businessId,
    storeId: LAB.stores[0], status, createdAt, updatedAt, labScenario: true,
    buyerSnapshot: { uid: LAB.buyer.uid, displayName: "Cliente laboratorio", phone: "900000005" },
    businessSnapshot: { id: LAB.businessId, name: "Chaski Lab Food" },
    storeSnapshot: { id: LAB.stores[0], name: "Local Central Lab", phone: "900000002" },
    pickupSnapshot: { addressText: "Dirección laboratorio 1", location: { lat: -12.0464, lng: -77.0428 } },
    dropoffSnapshot: { sourceAddressId: "lab-address", label: "Casa Lab", addressText: "Parque de laboratorio, Lima",
      location: { lat: -12.05, lng: -77.04 }, reference: "Puerta principal", instructions: "Llamar al llegar" },
    items: [{ productId: LAB.products[0], categoryId: LAB.categoryId, name: "Plato de prueba",
      quantity: 2, unitPriceMinor: 1000, lineTotalMinor: subtotalMinor }],
    subtotalMinor, deliveryFeeMinor, totalMinor, currency: "PEN", paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PENDING",
    quoteSnapshot: { quoteId: `lab-quote-${orderId.slice(0, 12)}`, distanceMeters: 1500,
      tariffVersion: "lima-driving-car-v1", quotedAt: createdAt, expiresAt: Timestamp.fromMillis(createdAt.toMillis() + 300_000) },
    ...(status === "PREPARING" ? { acceptedByUid: LAB.manager.uid, acceptedAt: updatedAt,
      estimatedPreparationMinutes: minutes, estimatedReadyAt: Timestamp.fromMillis(updatedAt.toMillis() + minutes! * 60_000),
      estimateRevision: 0 } : {}),
    ...(deliveryId ? { deliveryId } : {}),
  };
}

export async function seedHu20(db: Firestore, auth: Auth): Promise<void> {
  await seedBase(db, auth);
  const base = Date.now() - 5 * 60_000;
  const scenarios = [
    { id: LAB.orders.pending, status: "PENDING_CONFIRMATION" as const, offset: 0 },
    { id: LAB.orders.preparing, status: "PREPARING" as const, offset: 1, minutes: 25 },
    { id: LAB.orders.preparingWithDelivery, status: "PREPARING" as const, offset: 2, minutes: 35, deliveryId: "lab-delivery-1" },
  ];
  for (const scenario of scenarios) {
    const createdAt = Timestamp.fromMillis(base + scenario.offset * 1_000);
    const ref = db.doc(`food_orders/${scenario.id}`);
    const data = orderData(scenario.id, scenario.status, createdAt, scenario.minutes, scenario.deliveryId);
    await ref.set(data);
    await ref.collection("status_history").doc("initial").set({ schemaVersion: 1, fromStatus: null,
      toStatus: "PENDING_CONFIRMATION", actorUid: LAB.buyer.uid, occurredAt: createdAt, labScenario: true });
    if (scenario.status === "PREPARING") await ref.collection("status_history").doc("manager_decision").set({
      schemaVersion: 1, fromStatus: "PENDING_CONFIRMATION", toStatus: "PREPARING", actorUid: LAB.manager.uid,
      occurredAt: data.updatedAt, labScenario: true });
  }
}
