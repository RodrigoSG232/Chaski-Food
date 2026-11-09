import { Auth } from "firebase-admin/auth";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { CommercialAccessService } from "../access/commercialAccessService";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { OrderHttpError } from "../orders/orderRequest";
import { aggregateDashboard, dashboardWindow, DashboardQuery } from "./dashboardPolicy";

const maxRows=3000;
export class DashboardReader {
  constructor(private readonly db:Firestore,private readonly auth:Auth,private readonly now:()=>number=Date.now){}
  async read(actor:CommercialIdentity,query:DashboardQuery) {
    const window=dashboardWindow(query.days,this.now());
    return this.db.runTransaction(async tx=>{
      const scope=await new CommercialAccessService(this.db,this.auth).dashboardScope(tx,actor,query.businessId);
      const stores=scope.allStores
        ? (await tx.get(this.db.collection("stores").where("businessId","==",query.businessId))).docs
        : await Promise.all(scope.storeIds.map(id=>tx.get(this.db.doc(`stores/${id}`))));
      const options=stores.map(doc=>{
        const data=doc.data();
        if(!data || data.businessId!==query.businessId || typeof data.name!=="string" || !data.name.trim())
          throw new OrderHttpError("DATA_INCONSISTENT",503,true);
        return {storeId:doc.id,name:data.name.trim()};
      }).sort((a,b)=>a.name.localeCompare(b.name));
      if(query.storeId && !options.some(option=>option.storeId===query.storeId))throw new OrderHttpError("ACCESS_DENIED",403);
      const storeIds=query.storeId?[query.storeId]:options.map(option=>option.storeId);
      // Two independent windows: creations and successful completions may fall in different periods.
      const created=await tx.get(this.db.collection("food_orders").where("businessId","==",query.businessId)
        .where("createdAt",">=",Timestamp.fromMillis(window.start))
        .where("createdAt","<=",Timestamp.fromMillis(window.end)).limit(maxRows+1));
      const completed=await tx.get(this.db.collection("food_orders").where("businessId","==",query.businessId)
        .where("completedAt",">=",Timestamp.fromMillis(window.start))
        .where("completedAt","<=",Timestamp.fromMillis(window.end)).limit(maxRows+1));
      if(created.size>maxRows || completed.size>maxRows)throw new OrderHttpError("DASHBOARD_LIMIT_EXCEEDED",503,true);
      const metrics=aggregateDashboard({businessId:query.businessId,storeIds,dates:window.dates,
        created:created.docs.map(doc=>({id:doc.id,data:doc.data()})),
        completed:completed.docs.map(doc=>({id:doc.id,data:doc.data()}))});
      return {contractVersion:1,businessId:query.businessId,days:query.days,storeId:query.storeId,
        generatedAtEpochMillis:window.end,timeZone:"America/Lima",currency:"PEN",stores:options,...metrics};
    },{readOnly:true});
  }
}
