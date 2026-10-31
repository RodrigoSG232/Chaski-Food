import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
const { packageFiles, verify } = require("../../scripts/verifyPackage.cjs");
const config = JSON.parse(readFileSync(resolve(__dirname, "../../../firebase.json"), "utf8"));
const food = config.functions.find((entry: { codebase: string }) => entry.codebase === "food-local");

test("CLI upload exclusions discard synthetic private and generated test files", async () => {
  const directory = mkdtempSync(join(tmpdir(), "chaski-package-test-"));
  try {
    const excluded = [".secret.local", ".env.local", "nested/.env.production", "nested/private.key",
      "nested/account.pem", "nested/service-account.json", "google-services.json",
      "node_modules/demo/index.js", "test/example.ts", "lib/test/example.js",
      "scripts/verifyPackage.cjs", ".npmrc", "nested/credentials.json", "nested/key.jks", "firebase-debug.log", "coverage/report.json", "tmp/private.json"];
    const included = ["package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "tsconfig.json",
      "src/index.ts", "lib/src/index.js", "scripts/build.cjs"];
    for (const file of [...excluded, ...included]) {
      const target = join(directory, file);
      mkdirSync(resolve(target, ".."), { recursive: true });
      writeFileSync(target, "synthetic fixture only");
    }
    assert.deepEqual(await packageFiles(directory, food.ignore), included.sort());
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test("predeploy builds first and verifies the actual upload file set", async () => {
  assert.deepEqual(food.predeploy, ['pnpm --dir "$RESOURCE_DIR" run build',
    'pnpm --dir "$RESOURCE_DIR" run verify:package']);
  const files: string[] = await verify();
  assert.ok(files.includes("lib/src/index.js"));
  assert.ok(files.includes("pnpm-workspace.yaml"));
});
