import { BUSINESSES } from "./catalog";
import { MARKETPLACE_NAMESPACE } from "./environment";
import { closePermanentMarketplace, openPermanentMarketplace } from "./permanentEnvironment";

async function main(): Promise<void> {
  const context = openPermanentMarketplace();
  try {
    const expectedUids = BUSINESSES.flatMap(business => [business.id, business.managerUid]);
    const users = await Promise.all(expectedUids.map(uid => context.auth.getUser(uid).catch(() => null)));
    const [businesses, stores, categories, products, configurations] = await Promise.all([
      context.db.collection("business_requests").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("stores").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("categories").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("products").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
      context.db.collection("store_product_configs").where("seedNamespace", "==", MARKETPLACE_NAMESPACE).get(),
    ]);
    const [files] = await context.bucket.getFiles({ prefix: "businesses/" });
    const objects = files.filter(file => BUSINESSES.some(business => file.name.startsWith(`businesses/${business.id}/`)));
    const managers = await Promise.all(BUSINESSES.map(business =>
      context.db.doc(`store_managers/${business.id}_${business.managerEmail}`).get()));
    const urls = [...stores.docs, ...products.docs].map(doc => doc.get("imageUrl") as string).filter(Boolean);
    const valid = users.every(Boolean) && businesses.size === 2 && stores.size === 6 && categories.size === 8 &&
      products.size === 20 && configurations.size === 60 && objects.length === 26 && urls.length === 26 &&
      urls.every(url => url.startsWith("https://firebasestorage.googleapis.com/") && !url.includes("base64")) &&
      managers.every((manager, index) => manager.get("bindingState") === "VERIFIED" &&
        (manager.get("assignedStoreIds") as string[])?.[0] === BUSINESSES[index].assignedStoreId);
    console.log(`PERMANENT_MARKETPLACE_STATUS valid=${valid} profiles=${users.filter(Boolean).length} businesses=${businesses.size} stores=${stores.size} categories=${categories.size} products=${products.size} configs=${configurations.size} storageObjects=${objects.length} managers=${managers.filter(manager => manager.exists).length}`);
    if (!valid) process.exitCode = 2;
  } finally {
    await closePermanentMarketplace(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "PERMANENT_MARKETPLACE_VERIFY_FAILED");
  process.exitCode = 1;
});
