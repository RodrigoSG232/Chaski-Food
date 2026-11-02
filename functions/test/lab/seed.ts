import { closeLab, openLab } from "./labEnvironment";
import { seedBase } from "./labData";

async function main(): Promise<void> {
  const scenario = process.argv[2] ?? "base";
  if (scenario !== "base") throw new Error(`UNKNOWN_LAB_SCENARIO: ${scenario}`);
  const context = openLab("chaski-lab-seed");
  try {
    await seedBase(context.db, context.auth);
    console.log("LAB_READY scenario=base");
  } finally {
    await closeLab(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "LAB_SEED_FAILED");
  process.exitCode = 1;
});
