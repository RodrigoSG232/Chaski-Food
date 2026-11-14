"use strict";
const fs = require("node:fs");
const path = require("node:path");
// Same enumerator and ignore semantics as the pinned Firebase CLI; no archive or network.
const { readdirRecursive } = require("firebase-tools/lib/fsAsync");
const root = path.resolve(__dirname, "..");
// npm is the single package manager: Cloud Build installs exactly what package-lock.json pins (npm ci).
const required = ["package.json", "package-lock.json", "tsconfig.json", "lib/src/index.js", "scripts/build.cjs"];
const forbidden = ["pnpm-lock.yaml", "pnpm-workspace.yaml", "yarn.lock"];
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
  if (!Array.isArray(food.predeploy) || food.predeploy.some(step => !/^npm --prefix "\$RESOURCE_DIR" run /.test(step))) {
    throw new Error("Predeploy must use npm against the Functions directory");
  }
  for (const file of forbidden) {
    if (fs.existsSync(path.join(root, file))) throw new Error(`Second lockfile or workspace present: ${file}`);
  }
  const allowedRoot = new Set([...required.slice(0, 3), "scripts/build.cjs"]);
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
