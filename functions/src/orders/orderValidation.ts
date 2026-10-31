import { OrderHttpError } from "./orderRequest";
import { minorFromBigInt, OrderRejection, orderText } from "./orderModels";
export class OrderRejected extends Error {
  constructor(readonly reason: OrderRejection, readonly code: string, readonly status = 409) { super(code); }
}
export const inconsistent = (): never => { throw new OrderHttpError("DATA_INCONSISTENT",503,true); };
export function trustedText(value: unknown): string {
  try { return orderText(value); } catch { return inconsistent(); }
}
export function optionalStorePhone(value: unknown): string | null {
  if (value === undefined || value === null || value === "") return null;
  return trustedText(value);
}
/** Exact conversion of the existing Firestore decimal price; no Number multiplication/rounding. */
export function effectivePriceMinor(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) return inconsistent();
  const m = /^(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/i.exec(value.toString());
  if (!m) return inconsistent();
  const digits = BigInt(m[1]+(m[2]??"")), exponent = Number(m[3]??0) - (m[2]?.length??0) + 2;
  if (Math.abs(exponent)>400) return inconsistent();
  let result: bigint;
  if (exponent>=0) result=digits*10n**BigInt(exponent);
  else { const divisor=10n**BigInt(-exponent);if(digits%divisor!==0n)return inconsistent();result=digits/divisor; }
  try { return minorFromBigInt(result); } catch { return inconsistent(); }
}
const days=["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"];
function minutes(v: unknown): number {
  if (typeof v!=="string" || !/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/.test(v)) return inconsistent();
  return Number(v.slice(0,2))*60+Number(v.slice(3));
}
/** Equivalent to BusinessStore.isWithinOperatingHours, including the previous day's overnight shift. */
export function withinStoreHours(value: unknown, now: Date): boolean {
  if (!Array.isArray(value) || value.length!==7 || new Set(value.map(h=>h?.dayOfWeek)).size!==7) return inconsistent();
  const rows=value.map(h=>{
    if(!h || !days.includes(h.dayOfWeek) || typeof h.enabled!=="boolean")return inconsistent();
    const start=minutes(h.openTime),end=minutes(h.closeTime);
    if(h.enabled && start===end)return inconsistent();
    return {day:h.dayOfWeek,start,end,enabled:h.enabled};
  });
  const parts=new Intl.DateTimeFormat("en-US",{timeZone:"America/Lima",weekday:"long",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(now);
  const part=(kind:string)=>parts.find(p=>p.type===kind)!.value;
  const day=days.indexOf(part("weekday").toUpperCase()), target=Number(part("hour"))*60+Number(part("minute"));
  return rows.some(h=>h.enabled && (h.day===days[day] && (h.start<h.end?target>=h.start&&target<h.end:target>=h.start) ||
    h.day===days[(day+6)%7]&&h.start>h.end&&target<h.end));
}
