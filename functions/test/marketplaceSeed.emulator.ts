import assert from "node:assert/strict";
import test from "node:test";
import { BUSINESSES } from "./marketplace/catalog";
import { closeMarketplace, MARKETPLACE_NAMESPACE, openMarketplace } from "./marketplace/environment";
import { resetMarketplace, seedMarketplace } from "./marketplace/marketplaceData";

test("marketplace seed creates four commercial profiles and Storage-backed catalogs", async () => {
  const context = openMarketplace("marketplace-seed-emulator-test");
  try {
    await seedMarketplace(context.db, context.auth, context.bucket);
    await seedMarketplace(context.db, context.auth, context.bucket);

    const expectedUids = BUSINESSES.flatMap(business => [business.id, business.managerUid]);
    const users = await Promise.all(expectedUids.map(uid => context.auth.getUser(uid)));
    assert.equal(users.length, 4);
    assert.ok(users.every(user => user.emailVerified && user.phoneNumber?.startsWith("+519")));
    await assert.rejects(context.auth.getUser("marketplace-seed-admin"), /no user record|user-not-found/i);

    const [businesses, stores, categories, products, configurations, managers] = await Promise.all([
      context.db.collection("business_requests").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("stores").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("categories").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("products").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("store_product_configs").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("store_managers").get(),
    ]);
    assert.equal(businesses.size, 2);
    assert.equal(stores.size, 6);
    assert.equal(categories.size, 8);
    assert.equal(products.size, 20);
    assert.equal(configurations.size, 60);
    for (const business of BUSINESSES) {
      const assignment = managers.docs.find(doc => doc.id === `${business.id}_${business.managerEmail}`);
      assert.equal(assignment?.get("bindingState"), "VERIFIED");
      assert.deepEqual(assignment?.get("assignedStoreIds"), [business.assignedStoreId]);
    }

    const imageUrls = [...stores.docs, ...products.docs].map(doc => doc.get("imageUrl") as string);
    assert.equal(imageUrls.length, 26);
    assert.ok(imageUrls.every(url => url.startsWith("http://127.0.0.1:9199/v0/b/") && !url.includes("base64")));
    const [files] = await context.bucket.getFiles({ prefix: "businesses/" });
    assert.equal(files.filter(file => BUSINESSES.some(business => file.name.startsWith(`businesses/${business.id}/`))).length, 26);
    const response = await fetch(imageUrls[0]);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type") ?? "", /^image\/jpeg/);
    const bytes = new Uint8Array(await response.arrayBuffer());
    assert.deepEqual([...bytes.slice(0, 3)], [0xff, 0xd8, 0xff]);
  } finally {
    await resetMarketplace(context.db, context.auth, context.bucket);
    await closeMarketplace(context);
  }
});
