import { closeMarketplace, openMarketplace } from "./environment";
import { seedMarketplace } from "./marketplaceData";

async function main(): Promise<void> {
  const context = openMarketplace("chaski-marketplace-seed");
  try {
    await seedMarketplace(context.db, context.auth, context.bucket);
    console.log("MARKETPLACE_READY businesses=2 stores=6 products=20 profiles=4 storageObjects=26");
  } finally {
    await closeMarketplace(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "MARKETPLACE_SEED_FAILED");
  process.exitCode = 1;
});
