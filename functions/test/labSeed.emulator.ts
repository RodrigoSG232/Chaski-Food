import assert from "node:assert/strict";
import test from "node:test";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { LAB, resetLab, seedHu20 } from "./lab/labData";

test("HU20 lab seed is repeatable and leaves isolated usable actors and orders", async () => {
  const app = initializeApp({ projectId: "demo-chaski-hu15" }, "hu20-lab-seed-test");
  const auth = getAuth(app), db = getFirestore(app);
  try {
    await seedHu20(db, auth);
    await seedHu20(db, auth);
    for (const actor of [LAB.admin, LAB.owner, LAB.manager, LAB.buyer])
      assert.equal((await auth.getUser(actor.uid)).email, actor.email);
    const manager = await db.doc(`store_managers/${LAB.businessId}_${LAB.manager.email}`).get();
    assert.equal(manager.get("bindingState"), "VERIFIED");
    assert.deepEqual(manager.get("assignedStoreIds"), LAB.stores);
    const orders = await db.collection("food_orders").where("labScenario", "==", true).get();
    assert.equal(orders.size, 3);
    assert.equal((await db.doc(`food_orders/${LAB.orders.pending}`).get()).get("status"), "PENDING_CONFIRMATION");
    const preparing = await db.doc(`food_orders/${LAB.orders.preparing}`).get();
    assert.equal(preparing.get("status"), "PREPARING");
    assert.equal(preparing.get("estimatedPreparationMinutes"), 25);
    assert.equal((await db.doc(`food_orders/${LAB.orders.preparingWithDelivery}`).get()).get("deliveryId"), "lab-delivery-1");
  } finally {
    await resetLab(db, auth);
    await db.terminate();
    await deleteApp(app);
  }
});
