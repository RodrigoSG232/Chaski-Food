"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
// Resolve the one generated directory from this script, never from the caller's cwd.
const root = path.resolve(__dirname, "..");
const output = path.resolve(root, "lib");
if (path.dirname(output) !== root || path.basename(output) !== "lib") {
  throw new Error("Unsafe TypeScript output directory");
}
fs.rmSync(output, { recursive: true, force: true });
const result = spawnSync(process.execPath, [require.resolve("typescript/bin/tsc")], {
  cwd: root, stdio: "inherit",
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
