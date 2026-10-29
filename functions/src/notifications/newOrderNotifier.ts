import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { initializeRuntimeApp, resolveRuntimeEnvironment } from "../runtimeEnvironment";
import { OrderEventProcessor } from "./orderEventProcessor";
import { SimulatedNoticeSender } from "./fcmSender";

/** Never acknowledge a recoverable failure: retry:true redelivers the ORIGINAL creation event,
 * even though claim writes do not create another document. The lease alone is not a trigger.
 * FCM/Cloud activation is intentionally CLOSED; LOCAL proves only the injected transport. */
export const newOrderNotifier=onDocumentCreated({document:"order_events/{eventId}",region:"southamerica-west1",
  retry:true,timeoutSeconds:120,maxInstances:1,concurrency:5},async event=>{
    if(process.env.FUNCTIONS_EMULATOR!=="true")return;
    const runtime=resolveRuntimeEnvironment(process.env);
    if(runtime.mode!=="LOCAL"||process.env.CHASKI_NOTIFICATIONS_FIXTURE!=="enabled")return;
    const app=getApps().find(a=>a.options.projectId===runtime.projectId)??initializeRuntimeApp(runtime);
    await new OrderEventProcessor(getFirestore(app),getAuth(app),new SimulatedNoticeSender()).process(event.params.eventId);
  });
