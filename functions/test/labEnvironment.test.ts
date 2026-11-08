import assert from "node:assert/strict";
import test from "node:test";
import { requireLocalLab } from "./lab/labEnvironment";

test("lab refuses every environment except the exact local emulator project", () => {
  const before = { project: process.env.GCLOUD_PROJECT, firestore: process.env.FIRESTORE_EMULATOR_HOST,
    auth: process.env.FIREBASE_AUTH_EMULATOR_HOST };
  try {
    for (const values of [
      ["chaski-food-9adba", "127.0.0.1:8089", "127.0.0.1:9099"],
      ["demo-chaski-hu15", undefined, "127.0.0.1:9099"],
      ["demo-chaski-hu15", "firestore.googleapis.com", "127.0.0.1:9099"],
    ] as const) {
      values[0] === undefined ? delete process.env.GCLOUD_PROJECT : process.env.GCLOUD_PROJECT = values[0];
      values[1] === undefined ? delete process.env.FIRESTORE_EMULATOR_HOST : process.env.FIRESTORE_EMULATOR_HOST = values[1];
      values[2] === undefined ? delete process.env.FIREBASE_AUTH_EMULATOR_HOST : process.env.FIREBASE_AUTH_EMULATOR_HOST = values[2];
      assert.throws(requireLocalLab, /LOCAL_EMULATORS_REQUIRED/);
    }
    process.env.GCLOUD_PROJECT = "demo-chaski-hu15";
    process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8089";
    process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
    assert.doesNotThrow(requireLocalLab);
  } finally {
    before.project === undefined ? delete process.env.GCLOUD_PROJECT : process.env.GCLOUD_PROJECT = before.project;
    before.firestore === undefined ? delete process.env.FIRESTORE_EMULATOR_HOST : process.env.FIRESTORE_EMULATOR_HOST = before.firestore;
    before.auth === undefined ? delete process.env.FIREBASE_AUTH_EMULATOR_HOST : process.env.FIREBASE_AUTH_EMULATOR_HOST = before.auth;
  }
});
