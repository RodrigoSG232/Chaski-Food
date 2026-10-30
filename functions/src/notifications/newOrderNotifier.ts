import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { initializeRuntimeApp, resolveRuntimeEnvironment, RuntimeConfigurationError } from "../runtimeEnvironment";
import { OrderEventProcessor } from "./orderEventProcessor";
import { noticeSenderForRuntime } from "./fcmSender";

/** Never acknowledge a recoverable failure: retry:true redelivers the ORIGINAL creation event,
 * even though claim writes do not create another document. The lease alone is not a trigger.
 * CLOUD sends through Messaging; LOCAL proves only the injected simulated transport. */
export const newOrderNotifier=onDocumentCreated({document:"order_events/{eventId}",region:"southamerica-west1",
  retry:true,timeoutSeconds:120,maxInstances:1,concurrency:5},async event=>{
    let app,sender;
    try {
      const runtime=resolveRuntimeEnvironment(process.env,"newOrderNotifier");
      app=getApps().find(a=>a.options.projectId===runtime.projectId)??initializeRuntimeApp(runtime);
      sender=noticeSenderForRuntime(runtime,process.env.CHASKI_NOTIFICATIONS_FIXTURE,app);
    }catch(error){
      // A misconfigured runtime is not transient: acknowledge without touching the event, which stays durable for replay.
      if(error instanceof RuntimeConfigurationError){console.error("NOTIFIER_RUNTIME_INVALID");return;}
      throw error;
    }
    if(!sender)return;
    await new OrderEventProcessor(getFirestore(app),getAuth(app),sender).process(event.params.eventId);
  });
