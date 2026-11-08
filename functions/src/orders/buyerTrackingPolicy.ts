import { Timestamp } from "firebase-admin/firestore";
import { validCoordinates } from "../delivery/routeReader";
import { OrderHttpError } from "./orderRequest";

/** HU23: whitelist for the authenticated buyer, never the private Rider snapshot. */
export function projectBuyerTracking(orderId: string, data: Record<string, any>, now = Date.now()) {
  const invalid = (): never => { throw new OrderHttpError("DATA_INCONSISTENT", 503, true); };
  const statuses = ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP", "IN_DELIVERY", "COMPLETED", "CANCELLED", "REJECTED"];
  if (data.orderId !== orderId || data.schemaVersion !== 1 || !statuses.includes(data.status)) invalid();
  const integration = data.riderIntegration;
  const assignment = integration?.assignment;
  const assigned = assignment != null;
  if (assigned && (typeof assignment.id !== "string" || !Number.isSafeInteger(assignment.version) ||
      assignment.version < 1 || typeof integration.rider?.displayName !== "string" || !integration.rider.displayName.trim())) invalid();
  const moving = data.status === "IN_DELIVERY";
  if (moving && !assigned) invalid();
  const outcome = data.status === "COMPLETED" ? integration?.deliveryOutcome ?? null : null;
  if (outcome !== null && outcome !== "SUCCESS" && outcome !== "UNSUCCESSFUL") invalid();
  let stage: string = data.status;
  if (data.status === "READY_FOR_PICKUP") {
    stage = assigned ? integration.status : "SEARCHING_RIDER";
    if (!["SEARCHING_RIDER", "ACCEPTED", "AT_MERCHANT"].includes(stage)) invalid();
  } else if (moving) {
    stage = integration.status;
    if (!["ON_THE_WAY", "AT_CUSTOMER"].includes(stage)) invalid();
  }
  const sample = data.riderTracking;
  let position = null;
  let estimatedArrivalAtEpochMillis: number | null = null;
  // Hide coordinates before pickup, after termination, and from any former assignment.
  if (moving && sample && sample.assignmentId === assignment.id && sample.assignmentVersion === assignment.version &&
      sample.riderId === integration.rider?.id) {
    if (!validCoordinates(sample) || !(sample.recordedAt instanceof Timestamp) || !(sample.receivedAt instanceof Timestamp) ||
        typeof sample.accuracyMeters !== "number" || !Number.isFinite(sample.accuracyMeters) ||
        sample.accuracyMeters < 0 || sample.accuracyMeters > 500) invalid();
    position = { latitude: sample.latitude as number, longitude: sample.longitude as number, accuracyMeters: sample.accuracyMeters as number,
      recordedAtEpochMillis: sample.recordedAt.toMillis() as number, receivedAtEpochMillis: sample.receivedAt.toMillis() as number };
    if (sample.estimatedArrivalAt != null) {
      if (!(sample.estimatedArrivalAt instanceof Timestamp)) invalid();
      estimatedArrivalAtEpochMillis = sample.estimatedArrivalAt.toMillis();
    }
  }
  return { contractVersion: 1, orderId, status: data.status as string, stage, assigned,
    riderName: assigned ? integration.rider.displayName as string : null, deliveryOutcome: outcome as string | null,
    position, estimatedArrivalAtEpochMillis, serverTimeEpochMillis: now };
}
