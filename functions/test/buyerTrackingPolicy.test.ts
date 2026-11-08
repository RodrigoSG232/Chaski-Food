import assert from "node:assert/strict";
import test from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { projectBuyerTracking } from "../src/orders/buyerTrackingPolicy";
import { parseRiderTracking } from "../src/integration/riderIntegrationPolicy";

const now = 1_800_000_000_000, id = "a".repeat(64);
const integration = { status: "ON_THE_WAY", assignment: { id: "asg", version: 1 },
  rider: { id: "rider", displayName: "Ana", phone: "PRIVATE_PHONE" } };
const sample = { assignmentId: "asg", assignmentVersion: 1, riderId: "rider", latitude: -12.1, longitude: -77.1,
  accuracyMeters: 10, recordedAt: Timestamp.fromMillis(now), receivedAt: Timestamp.fromMillis(now),
  estimatedArrivalAt: Timestamp.fromMillis(now + 600_000) };
const order = { schemaVersion: 1, orderId: id, status: "IN_DELIVERY", riderIntegration: integration,
  riderTracking: sample, pickupCode: "PRIVATE_PIN", buyerUid: "PRIVATE_UID" };

test("tracking exposes a strict buyer projection and the Rider ETA", () => {
  const view = projectBuyerTracking(id, order, now);
  assert.equal(view.estimatedArrivalAtEpochMillis, now + 600_000);
  assert.equal(view.position?.latitude, -12.1);
  assert.deepEqual(Object.keys(view).sort(), ["contractVersion", "orderId", "status", "stage", "assigned", "riderName",
    "deliveryOutcome", "position", "estimatedArrivalAtEpochMillis", "serverTimeEpochMillis"].sort());
  assert.equal(JSON.stringify(view).includes("PRIVATE"), false);
  assert.equal(JSON.stringify(view).includes("riderId"), false);
});

test("no assignment, pre-pickup and terminal orders never expose GPS or ETA", () => {
  for (const state of [
    { status: "READY_FOR_PICKUP", riderIntegration: null },
    { status: "READY_FOR_PICKUP", riderIntegration: { ...integration, status: "ACCEPTED" } },
    { status: "READY_FOR_PICKUP", riderIntegration: { ...integration, status: "AT_MERCHANT" } },
    { status: "COMPLETED", riderIntegration: { ...integration, deliveryOutcome: "UNSUCCESSFUL" } },
    { status: "CANCELLED" },
  ]) {
    const view = projectBuyerTracking(id, { ...order, ...state }, now);
    assert.equal(view.position, null); assert.equal(view.estimatedArrivalAtEpochMillis, null);
  }
  assert.equal(projectBuyerTracking(id, { ...order, status: "READY_FOR_PICKUP", riderIntegration: null }).stage, "SEARCHING_RIDER");
});

test("stale GPS remains available but an old assignment is never projected", () => {
  assert.equal(projectBuyerTracking(id, order, now + 600_000).position?.recordedAtEpochMillis, now);
  assert.equal(projectBuyerTracking(id, { ...order, riderTracking: { ...sample, assignmentVersion: 2 } }).position, null);
  assert.equal(projectBuyerTracking(id, { ...order, riderTracking: null }).position, null);
  assert.throws(() => projectBuyerTracking(id, { ...order, riderIntegration: null }));
  assert.throws(() => projectBuyerTracking(id, { ...order, riderTracking: { ...sample, latitude: 100 } }));
});

test("optional ETA preserves old Rider payloads and validates the proposed extension", () => {
  const body = { assignmentId: "asg", assignmentVersion: 1, sequence: 1, riderId: "rider", latitude: -12.1,
    longitude: -77.1, accuracyMeters: 10, recordedAt: new Date(now).toISOString() };
  assert.equal(parseRiderTracking(body).estimatedArrivalAt, null);
  assert.equal(parseRiderTracking({ ...body, estimatedArrivalAt: null }).estimatedArrivalAt, null);
  assert.equal(parseRiderTracking({ ...body, estimatedArrivalAt: new Date(now + 600_000).toISOString() })
    .estimatedArrivalAt?.toMillis(), now + 600_000);
  for (const bad of ["soon", 15, new Date(now - 1).toISOString(), new Date(now + 86_400_001).toISOString()])
    assert.throws(() => parseRiderTracking({ ...body, estimatedArrivalAt: bad }));
});
