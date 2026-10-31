import { Firestore, Timestamp } from "firebase-admin/firestore";
import { digest } from "../access/commercialAccessPolicy";
import { OrderHttpError } from "../orders/orderRequest";

export function installationId(value:unknown):string {
  if(typeof value!=="string"||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value))throw new OrderHttpError("INVALID_REQUEST",400);
  return value;
}
export function deviceToken(value:unknown):string {
  if(typeof value!=="string"||value.length<16||value.length>4096||/\s/.test(value))throw new OrderHttpError("INVALID_REQUEST",400);
  return value;
}
/** Private registry: ownership always comes from Auth, never from the request body.
 * One token document prevents simultaneous bindings of the same token to two UIDs. */
export class DeviceRegistry {
  constructor(private readonly db:Firestore){}
  async put(uid:string,id:string,body:unknown){
    installationId(id);
    const b=body as Record<string,unknown>;
    if(!b||Object.keys(b).sort().join(",")!=="bindingVersion,platform,token"||(b.platform!=="ANDROID"||!Number.isSafeInteger(b.bindingVersion)||Number(b.bindingVersion)<1))throw new OrderHttpError("INVALID_REQUEST",400);
    const token=deviceToken(b.token),hash=digest(token);
    await this.db.runTransaction(async tx=>{
      const old=await tx.get(this.db.collection("notification_devices").where("installationId","==",id));
      const ref=this.db.doc(`notification_devices/${hash}`);const existing=(await tx.get(ref)).data();
      if([...old.docs.map(d=>d.data()),...(existing?[existing]:[])].some(d=>d.installationId===id &&
        (d.bindingVersion>Number(b.bindingVersion)||(d.bindingVersion===b.bindingVersion && (d.uid!==uid||d.token!==token)))))
        throw new OrderHttpError("STALE_DEVICE_BINDING",409);
      for(const doc of old.docs)if(doc.id!==hash && doc.data().uid===uid)tx.delete(doc.ref);
      tx.set(ref,{schemaVersion:1,uid,installationId:id,bindingVersion:b.bindingVersion,token,platform:"ANDROID",enabled:true,authenticatedAt:Timestamp.now(),updatedAt:Timestamp.now()});
    });
  }
  async remove(uid:string,id:string,version:number){
    installationId(id);
    if(!Number.isSafeInteger(version)||version<1)throw new OrderHttpError("INVALID_REQUEST",400);
    await this.db.runTransaction(async tx=>{
      const rows=await tx.get(this.db.collection("notification_devices").where("installationId","==",id));
      for(const doc of rows.docs)if(doc.data().uid===uid && doc.data().bindingVersion<=version)
        tx.set(doc.ref,{schemaVersion:1,uid,installationId:id,bindingVersion:version,enabled:false,updatedAt:Timestamp.now()});
    });
  }
}
