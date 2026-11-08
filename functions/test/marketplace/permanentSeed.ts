import { seedPermanentMarketplace } from "./marketplaceData";
import { closePermanentMarketplace, openPermanentMarketplace } from "./permanentEnvironment";

async function main(): Promise<void> {
  const context = openPermanentMarketplace();
  try {
    await seedPermanentMarketplace(context.db, context.auth, context.bucket, process.env.CHASKI_MARKETPLACE_PASSWORD!);
    console.log("PERMANENT_MARKETPLACE_READY project=chaski-food-9adba businesses=2 stores=6 products=20 profiles=4 storageObjects=26");
  } finally {
    await closePermanentMarketplace(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "PERMANENT_MARKETPLACE_SEED_FAILED");
  process.exitCode = 1;
});
