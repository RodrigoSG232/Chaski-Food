import { closeLab, openLab } from "./labEnvironment";
import { resetLab } from "./labData";

async function main(): Promise<void> {
  const context = openLab("chaski-lab-reset");
  try {
    await resetLab(context.db, context.auth);
    console.log("LAB_RESET_OK");
  } finally {
    await closeLab(context);
  }
}

void main().catch(error => {
  console.error(error instanceof Error ? error.message : "LAB_RESET_FAILED");
  process.exitCode = 1;
});
