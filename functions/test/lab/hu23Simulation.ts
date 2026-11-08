import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { closeLab, openLab } from "./labEnvironment";
import { LAB } from "./labData";
import { hu23Event, hu23Order, hu23Sample } from "./hu23Fixture";

/** Never accepts a project, host, UID or order ID from the CLI. LOCAL only. */
async function main() {
  const action = process.argv[2] ?? "help";
  const actions = ["prepare", "assign", "merchant", "release", "pickup", "move", "stale", "no-eta", "arrive", "finish", "fail"];
  if (!actions.includes(action)) {
    console.log("HU23 LOCAL: " + actions.join(" | ")); return;
  }
  const context = openLab("hu23-local-simulation");
  try {
    const id = createHash("sha256").update("lab-order-hu23").digest("hex"), ref = context.db.doc("food_orders/" + id);
    if (action === "prepare") {
      // Existing lab identity must be deliberately seeded first; never alters an existing account.
      const buyer = await context.auth.getUser(LAB.buyer.uid);
      if (buyer.email !== LAB.buyer.email) throw new Error("LAB_BUYER_REQUIRED");
      const existing = (await ref.get()).data();
      if (existing && (existing.labScenario !== true || existing.buyerUid !== LAB.buyer.uid))
        throw new Error("LAB_ORDER_REQUIRED");
      await context.db.recursiveDelete(ref);
      const order = hu23Order(id, LAB.buyer.uid);
      await ref.set(order);
      const statuses = ["PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP"];
      for (const [i, status] of statuses.entries())
        await ref.collection("status_history").doc("initial-" + i).set({ schemaVersion: 1, fromStatus: i ? statuses[i - 1] : null,
          toStatus: status, actorUid: LAB.buyer.uid, occurredAt: Timestamp.fromMillis(order.createdAt.toMillis() + i) });
      console.log("HU23_LOCAL_READY orderId=" + id); return;
    }
    const order = (await ref.get()).data();
    if (!order || order.labScenario !== true || order.buyerUid !== LAB.buyer.uid) throw new Error("RUN_PREPARE_FIRST");
    const integration = order.riderIntegration, version = integration?.assignment?.version ?? 1;
    let path: string, method: string, body: unknown;
    if (["move", "stale", "no-eta"].includes(action)) {
      path = "/orders/" + id + "/tracking"; method = "PUT";
      const seq = (order.riderTracking?.sequence ?? 0) + 1;
      const sample = hu23Sample(seq, Date.now() - (action === "stale" ? 180_000 : 0), version);
      if (action === "no-eta") { const { estimatedArrivalAt, ...legacy } = sample; body = legacy; }
      else body = sample;
    } else {
      const types: Record<string, string> = { assign: "ASSIGNMENT_ACCEPTED", merchant: "ARRIVED_AT_MERCHANT",
        release: "ASSIGNMENT_RELEASED", pickup: "PICKED_UP", arrive: "ARRIVED_AT_CUSTOMER",
        finish: "COMPLETED_SUCCESSFULLY", fail: "COMPLETED_UNSUCCESSFULLY" };
      path = "/rider-events"; method = "POST";
      body = hu23Event(id, types[action], (integration?.lastEventSequence ?? 0) + 1,
        action === "assign" ? (integration?.lastAssignmentVersion ?? 0) + 1 : version);
    }
    const response = await fetch("http://127.0.0.1:5001/demo-chaski-hu15/southamerica-west1/riderApi/integration/v1" + path,
      { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body), signal: AbortSignal.timeout(30_000) });
    console.log("HU23_LOCAL " + action + " HTTP " + response.status + " " + await response.text());
    if (!response.ok) process.exitCode = 1;
  } finally { await closeLab(context); }
}
void main().catch(error => { console.error(error instanceof Error ? error.message : "HU23_LOCAL_FAILED"); process.exitCode = 1; });
