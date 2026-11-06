import { closeMarketplace, openMarketplace } from "./environment";
import { resetMarketplace } from "./marketplaceData";

async function main(): Promise<void> {
  const context = openMarketplace("chaski-marketplace-reset");
  try {
    await resetMarketplace(context.db, context.auth, context.bucket);
    console.log("MARKETPLACE_RESET_OK");
  } finally {
    await closeMarketplace(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "MARKETPLACE_RESET_FAILED");
  process.exitCode = 1;
});
