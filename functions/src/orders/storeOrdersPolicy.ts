import { Timestamp } from "firebase-admin/firestore";
import { MAX_ORDER_EPOCH_MILLIS, orderId, orderMinor, orderText } from "./orderModels";
import { OrderHttpError } from "./orderRequest";
import { validId, digest } from "../access/commercialAccessPolicy";

export const DEFAULT_INBOX_PAGE_SIZE = 20;
export const MAX_INBOX_PAGE_SIZE = 50; // Transport bound, never a business/order limit.
export type InboxCursor = Readonly<{v:1;subject:string;storeId:string;anchorId:string}>;
export function encodeInboxCursor(uid:string,storeId:string,anchorId:string):string {
  return Buffer.from(JSON.stringify({v:1,subject:digest(["inbox-cursor-v1",uid]),storeId,anchorId})).toString("base64url");
}
export function parseInboxQuery(query:Record<string,unknown>,uid:string,storeId:string):{pageSize:number;cursor:InboxCursor|null} {
  if(!validId(storeId)||Object.keys(query).some(k=>!["pageSize","cursor"].includes(k)))throw new OrderHttpError("INVALID_REQUEST",400);
  const size=query.pageSize;
  if(size!==undefined && (typeof size!=="string"||!/^[1-9][0-9]?$/.test(size)||Number(size)>MAX_INBOX_PAGE_SIZE))
    throw new OrderHttpError("INVALID_REQUEST",400);
  let cursor:InboxCursor|null=null;
  if(query.cursor!==undefined) {
    try {
      if(typeof query.cursor!=="string"||query.cursor.length>1024||!/^[A-Za-z0-9_-]+$/.test(query.cursor))throw new Error();
      const raw=JSON.parse(Buffer.from(query.cursor,"base64url").toString("utf8"));
      if(!raw||Array.isArray(raw)||Object.keys(raw).sort().join(",")!=="anchorId,storeId,subject,v"||raw.v!==1||raw.subject!==digest(["inbox-cursor-v1",uid])||
        raw.storeId!==storeId||typeof raw.anchorId!=="string"||!/^[0-9a-f]{64}$/.test(raw.anchorId)||
        encodeInboxCursor(uid,storeId,raw.anchorId)!==query.cursor)throw new Error();
      cursor=Object.freeze(raw);
    }catch{throw new OrderHttpError("INVALID_CURSOR",400);}
  }
  return {pageSize:size===undefined?DEFAULT_INBOX_PAGE_SIZE:Number(size),cursor};
}
/** Shared order-document validation (HU16 inbox and HU17 buyer reads). Throws on any inconsistency; callers map it to 503. */
export function validatedOrderCore(id:string,data:Record<string,any>) {
  if(!/^[0-9a-f]{64}$/.test(id)||data.orderId!==id||data.schemaVersion!==1||
    !(data.createdAt instanceof Timestamp)||data.currency!=="PEN"||data.paymentMethod!=="CASH_ON_DELIVERY"||
    !Array.isArray(data.items)||data.items.length===0)throw new Error();
  const createdAtEpochMillis=Math.floor(data.createdAt.toMillis());
  if(createdAtEpochMillis<1||createdAtEpochMillis>MAX_ORDER_EPOCH_MILLIS)throw new Error();
  const seen=new Set<string>();let subtotal=0n;
  const items=data.items.map((item:Record<string,unknown>)=>{
    const productId=orderId(item.productId),name=orderText(item.name),quantity=item.quantity;
    if(seen.has(productId)||typeof quantity!=="number"||!Number.isInteger(quantity)||quantity<=0||quantity>2147483647)throw new Error();
    seen.add(productId);
    const unitPriceMinor=orderMinor(item.unitPriceMinor),lineTotalMinor=orderMinor(item.lineTotalMinor);
    if(unitPriceMinor===0||BigInt(quantity)*BigInt(unitPriceMinor)!==BigInt(lineTotalMinor))throw new Error();
    subtotal+=BigInt(lineTotalMinor);
    return {productId,name,quantity,unitPriceMinor,lineTotalMinor};
  });
  const subtotalMinor=orderMinor(data.subtotalMinor),deliveryFeeMinor=orderMinor(data.deliveryFeeMinor),totalMinor=orderMinor(data.totalMinor);
  if(subtotal!==BigInt(subtotalMinor)||subtotal+BigInt(deliveryFeeMinor)!==BigInt(totalMinor))throw new Error();
  return {createdAtEpochMillis,items,subtotalMinor,deliveryFeeMinor,totalMinor,currency:"PEN" as const,paymentMethod:"CASH_ON_DELIVERY" as const};
}
/** Explicit whitelist: no contacts, addresses, buyer identity, attempts or event state. */
export function projectPendingOrder(id:string,data:Record<string,any>) {
  try {
    if(data.status!=="PENDING_CONFIRMATION")throw new Error();
    const core=validatedOrderCore(id,data);
    return {orderId:id,createdAtEpochMillis:core.createdAtEpochMillis,status:"PENDING_CONFIRMATION" as const,items:core.items,
      subtotalMinor:core.subtotalMinor,deliveryFeeMinor:core.deliveryFeeMinor,totalMinor:core.totalMinor,
      currency:core.currency,paymentMethod:core.paymentMethod};
  }catch{throw new OrderHttpError("DATA_INCONSISTENT",503,true);}
}
