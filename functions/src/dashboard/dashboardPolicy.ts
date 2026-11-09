import { Timestamp } from "firebase-admin/firestore";
import { OrderHttpError } from "../orders/orderRequest";
import { validId } from "../access/commercialAccessPolicy";

export type DashboardQuery = { businessId: string; storeId: string | null; days: 7 | 30 | 90 };
export type DashboardOrder = { id: string; data: Record<string, unknown> };
const limaOffset = 5 * 60 * 60 * 1000;

export function parseDashboardQuery(raw: Record<string, unknown>): DashboardQuery {
  if (Object.keys(raw).some(key => !["businessId", "storeId", "days"].includes(key)) ||
      !validId(raw.businessId) || (raw.storeId !== undefined && !validId(raw.storeId)) ||
      typeof raw.days!=="string" || !["7", "30", "90"].includes(raw.days)) throw new OrderHttpError("INVALID_REQUEST",400);
  return {businessId:raw.businessId,storeId:(raw.storeId as string | undefined)??null,
    days:Number(raw.days) as 7|30|90};
}

/** Inclusive Lima calendar days; the server clock determines the window. */
export function dashboardWindow(days: number, now: number) {
  const today = new Date(now-limaOffset).toISOString().slice(0,10);
  const todayStart = Date.parse(`${today}T00:00:00.000Z`)+limaOffset;
  const start = todayStart-(days-1)*86_400_000;
  const dates = Array.from({length:days},(_,index)=>new Date(start+index*86_400_000-limaOffset).toISOString().slice(0,10));
  return {start,end:now,dates};
}

function day(value: Timestamp): string {
  return new Date(value.toMillis()-limaOffset).toISOString().slice(0,10);
}
function checked(row: DashboardOrder, businessId: string): Record<string, unknown> {
  const d=row.data;
  if (d.schemaVersion!==1 || d.orderId!==row.id || d.businessId!==businessId || !validId(d.storeId) ||
      !(d.createdAt instanceof Timestamp) || !Number.isSafeInteger(d.subtotalMinor) ||
      (d.subtotalMinor as number)<0 || d.currency!=="PEN") throw new OrderHttpError("DATA_INCONSISTENT",503,true);
  return d;
}

export function aggregateDashboard(input: {businessId:string;storeIds:string[];dates:string[];
  created:DashboardOrder[];completed:DashboardOrder[]}) {
  const authorized = new Set(input.storeIds);
  const buckets = new Map(input.dates.map(date=>[date,{date,orders:0,salesMinor:0}]));
  let accepted=0,rejected=0;
  for(const row of input.created) {
    if(!authorized.has(row.data.storeId as string))continue;
    const d=checked(row,input.businessId);
    const bucket=buckets.get(day(d.createdAt as Timestamp));
    if(!bucket)throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    bucket.orders++;
    if((d.acceptedAt!==undefined && !(d.acceptedAt instanceof Timestamp)) ||
       (d.rejectedAt!==undefined && !(d.rejectedAt instanceof Timestamp)))
      throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    if(d.acceptedAt instanceof Timestamp && d.rejectedAt instanceof Timestamp)
      throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    if(d.acceptedAt instanceof Timestamp)accepted++;
    else if(d.rejectedAt instanceof Timestamp)rejected++;
  }
  for(const row of input.completed) {
    if(!authorized.has(row.data.storeId as string))continue;
    const d=checked(row,input.businessId);
    const completedAt=d.completedAt;
    if(!(completedAt instanceof Timestamp))throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    const bucket=buckets.get(day(completedAt));
    if(!bucket)throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    const outcome=(d.riderIntegration as {deliveryOutcome?:unknown}|undefined)?.deliveryOutcome;
    if(d.status!=="COMPLETED" || !["SUCCESS","UNSUCCESSFUL"].includes(String(outcome)))
      throw new OrderHttpError("DATA_INCONSISTENT",503,true);
    if(outcome==="SUCCESS") {
      const next=bucket.salesMinor+(d.subtotalMinor as number);
      if(!Number.isSafeInteger(next))throw new OrderHttpError("DATA_INCONSISTENT",503,true);
      bucket.salesMinor=next;
    }
  }
  const series=[...buckets.values()];
  const salesMinor=series.reduce((sum,b)=>sum+b.salesMinor,0);
  if(!Number.isSafeInteger(salesMinor))throw new OrderHttpError("DATA_INCONSISTENT",503,true);
  return {orders:series.reduce((sum,b)=>sum+b.orders,0),salesMinor,accepted,rejected,
    acceptanceRate:accepted+rejected?Math.round(accepted*10000/(accepted+rejected))/100:null,series};
}
