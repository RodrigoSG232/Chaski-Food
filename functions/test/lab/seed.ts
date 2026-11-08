import { closeLab, openLab } from "./labEnvironment";
import { seedBase, seedHu20 } from "./labData";

async function main(): Promise<void> {
  const scenario = process.argv.slice(2).find(argument => argument !== "--") ?? "base";
  if (scenario !== "base" && scenario !== "hu20") throw new Error(`UNKNOWN_LAB_SCENARIO: ${scenario}`);
  const context = openLab("chaski-lab-seed");
  try {
    if (scenario === "hu20") await seedHu20(context.db, context.auth);
    else await seedBase(context.db, context.auth);
    console.log(`LAB_READY scenario=${scenario}`);
  } finally {
    await closeLab(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "LAB_SEED_FAILED");
  process.exitCode = 1;
});
