import { Auth } from "firebase-admin/auth";
import { FieldPath, Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { OrderHttpError } from "./orderRequest";
import { orderText } from "./orderModels";
import { encodeInboxCursor, parseInboxQuery, projectStoreOrder } from "./storeOrdersPolicy";

/** Authorization, cursor and query share a consistent read-only transaction; never updates orders. */
export class StoreOrdersReader {
  private readonly access:CommercialAccessService;
  constructor(private readonly db:Firestore,auth:Auth){this.access=new CommercialAccessService(db,auth);}
  async page(actor:CommercialIdentity,storeId:string,query:Record<string,unknown>) {
    const {pageSize,cursor,status}=parseInboxQuery(query,actor.uid,storeId);
    return this.db.runTransaction(async tx=>{
      const scope=await this.access.requireStoreOrderReader(tx,actor,storeId);
      // Unknown or foreign stores are never distinguished for actors lacking the assignment.
      const store=(await tx.get(this.db.doc(`stores/${storeId}`))).data();
      if(!store)throw new OrderHttpError("STORE_NOT_FOUND",404);
      if(store.businessId!==scope.businessId)throw new OrderHttpError("ACCESS_DENIED",403);
      let storeName:string;
      try{storeName=orderText(store.name);}catch{throw new OrderHttpError("DATA_INCONSISTENT",503,true);}
      let orders=this.db.collection("food_orders").where("storeId","==",storeId).where("businessId","==",scope.businessId)
        .where("status","==",status).orderBy("createdAt","desc").orderBy(FieldPath.documentId(),"desc");
      if(cursor) {
        const anchor=await tx.get(this.db.doc(`food_orders/${cursor.anchorId}`));
        const a=anchor.data();
        if(!a||a.schemaVersion!==1||a.orderId!==anchor.id||a.storeId!==storeId||a.businessId!==scope.businessId||a.status!==status||!(a.createdAt instanceof Timestamp))
          throw new OrderHttpError("INVALID_CURSOR",400);
        orders=orders.startAfter(a.createdAt,anchor.id); // No client-supplied time, offset or document path.
      }
      const snapshot=await tx.get(orders.limit(pageSize+1));
      const page=snapshot.docs.slice(0,pageSize);
      const rows=page.map(doc=>projectStoreOrder(doc.id,doc.data(),status));
      return {contractVersion:1,storeId,storeName,orders:rows,
        nextCursor:snapshot.size>pageSize?encodeInboxCursor(actor.uid,storeId,page[page.length-1].id,status):null};
    },{readOnly:true});
  }
}
