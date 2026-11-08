import { Timestamp } from "firebase-admin/firestore";

/** Synthetic snapshots shared by HTTP integration tests and the LOCAL-only simulator. */
export function hu23Order(orderId: string, buyerUid: string, now = Date.now()) {
  const at = Timestamp.fromMillis(now);
  return { schemaVersion: 1, orderId, buyerUid, intentId: "hu23-local", requestHash: "LOCAL_ONLY",
    businessId: "lab-owner", storeId: "lab-store-central", status: "READY_FOR_PICKUP",
    createdAt: at, updatedAt: at, labScenario: true,
    businessSnapshot: { id: "lab-owner", name: "Negocio laboratorio" },
    storeSnapshot: { id: "lab-store-central", name: "Local laboratorio", phone: null },
    buyerSnapshot: { uid: buyerUid, displayName: "Cliente laboratorio", phone: null },
    pickupSnapshot: { addressText: "Local ficticio", location: { latitude: -12.1, longitude: -77.1 } },
    dropoffSnapshot: { label: "Casa", addressText: "Destino ficticio", location: { latitude: -12.11, longitude: -77.11 },
      reference: null, instructions: null },
    items: [{ productId: "lab-product-one", categoryId: "lab-category-main", name: "Plato de prueba",
      quantity: 1, unitPriceMinor: 1000, lineTotalMinor: 1000 }],
    subtotalMinor: 1000, deliveryFeeMinor: 300, tipMinor: 0, totalMinor: 1300, currency: "PEN",
    paymentMethod: "CASH_ON_DELIVERY", paymentStatus: "PAID", paymentSimulation: true, pickupCode: "0042",
    estimatedPreparationMinutes: 20, estimatedReadyAt: Timestamp.fromMillis(now + 1_200_000) };
}

export function hu23Event(orderId: string, type: string, sequence: number, version = 1) {
  const status: Record<string, string> = { ASSIGNMENT_ACCEPTED: "ACCEPTED", ASSIGNMENT_RELEASED: "SEARCHING_RIDER",
    ARRIVED_AT_MERCHANT: "AT_MERCHANT", PICKED_UP: "ON_THE_WAY", ARRIVED_AT_CUSTOMER: "AT_CUSTOMER",
    COMPLETED_SUCCESSFULLY: "COMPLETED", COMPLETED_UNSUCCESSFULLY: "COMPLETED" };
  return { schemaVersion: 1, eventId: "hu23-event-" + sequence, eventType: type, eventSequence: sequence,
    occurredAt: new Date().toISOString(), orderId, assignment: { id: "hu23-assignment-" + version, version },
    status: status[type], reason: type === "COMPLETED_UNSUCCESSFULLY" ? "LOCAL_TEST" : null,
    rider: type === "ASSIGNMENT_ACCEPTED" ? { id: "hu23-rider", displayName: "Ana (simulación LOCAL)",
      phone: null, vehicleType: "BICYCLE", vehiclePlate: null } : null,
    deliveryOutcome: type === "COMPLETED_SUCCESSFULLY" ? "SUCCESS" : type === "COMPLETED_UNSUCCESSFULLY" ? "UNSUCCESSFUL" : null };
}

export function hu23Sample(sequence: number, now = Date.now(), version = 1) {
  return { assignmentId: "hu23-assignment-" + version, assignmentVersion: version, sequence, riderId: "hu23-rider",
    latitude: -12.105, longitude: -77.105, accuracyMeters: 12, recordedAt: new Date(now).toISOString(),
    estimatedArrivalAt: new Date(now + 600_000).toISOString() };
}
