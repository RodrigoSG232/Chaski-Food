"use strict";
const fs = require("node:fs");
const path = require("node:path");
// Same enumerator and ignore semantics as the pinned Firebase CLI; no archive or network.
const { readdirRecursive } = require("firebase-tools/lib/fsAsync");
const root = path.resolve(__dirname, "..");
const required = ["package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "tsconfig.json", "lib/src/index.js", "scripts/build.cjs"];
async function packageFiles(source, ignore) {
  const entries = await readdirRecursive({ path: source, ignoreStrings: [...ignore,
    "firebase-debug.log", "firebase-debug.*.log", ".runtimeconfig.json"] });
  return entries.map(entry => {
    if (!fs.realpathSync(entry.name).startsWith(fs.realpathSync(source) + path.sep)) {
      throw new Error("Package contains a path outside the Functions directory");
    }
    return path.relative(source, entry.name).split(path.sep).join("/");
  }).sort();
}
async function verify() {
  const config = JSON.parse(fs.readFileSync(path.join(root, "..", "firebase.json"), "utf8"));
  const food = config.functions.find(entry => entry.codebase === "food-local");
  if (!food || food.source !== "functions" || !Array.isArray(food.ignore)) {
    throw new Error("Missing explicit food-local packaging configuration");
  }
  const files = await packageFiles(root, food.ignore);
  const allowedRoot = new Set([...required.slice(0, 4), "scripts/build.cjs"]);
  if (files.some(file => !allowedRoot.has(file) &&
      !/^src\/(?:[\w-]+\/)*[\w-]+\.ts$/.test(file) &&
      !/^lib\/src\/(?:[\w-]+\/)*[\w-]+\.js$/.test(file))) {
    throw new Error("Unexpected file in Functions upload set; review exclusions before deploying");
  }
  for (const file of required) {
    if (!files.includes(file)) throw new Error(`Required deploy file missing: ${file}`);
  }
  console.log(`Verified Functions upload set: ${files.length} files; no archive created.`);
  console.log(files.join("\n"));
  return files;
}
module.exports = { packageFiles, verify };
if (require.main === module) verify().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
