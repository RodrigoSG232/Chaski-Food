import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { effectivePriceMinor, withinStoreHours } from "../src/orders/orderValidation";
import { parseOrderSubmission } from "../src/orders/orderRequest";
import { parseAcceptedPurchase } from "../src/orders/orderModels";
const vectors=JSON.parse(readFileSync(resolve(__dirname,"../../../app/src/test/resources/hu16/confirmation-v1.json"),"utf8"));
const purchase=()=>structuredClone(vectors.validCases[0].purchase);
const days=["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"];
function body() {
  const a=purchase();const {ownerUid,storeId,paymentMethod,...expected}=a;
  const addressId=expected.delivery.addressId,quoteId=expected.deliveryQuote.quoteId;
  delete expected.delivery.addressId;delete expected.deliveryQuote.quoteId;
  return {contractVersion:1,intentId:vectors.intentId,storeId,addressId,quoteId,paymentMethod,expected};
}
test("exact decimal price conversion, including fractional km-independent money and exponent notation",()=>{
  for(const [price,cents] of [[10,1000],[0.01,1],[12.34,1234],[1e-2,1],[1e6,100000000]])assert.equal(effectivePriceMinor(price),cents);
  for(const bad of [0,-1,0.001,0.1+0.2,Infinity,NaN,1e14,"10",100n])assert.throws(()=>effectivePriceMinor(bad));
});
test("authoritative UID is never a client field and root references are strict",()=>{
  const b=body(),intent=parseOrderSubmission(b,"buyer-verified");assert.equal(intent.accepted.ownerUid,"buyer-verified");
  assert.equal(intent.accepted.delivery.addressId,b.addressId);assert.equal(intent.accepted.deliveryQuote.quoteId,b.quoteId);
  for(const patch of [{uid:"other"},{contractVersion:2},{storeId:"a/b"},{intentId:"bad"}])assert.throws(()=>parseOrderSubmission({...body(),...patch},"buyer"));
  const injected=body();injected.expected.ownerUid="other";assert.throws(()=>parseOrderSubmission(injected,"buyer"));
});
test("request preserves optional buyer contact, real national store phone and empty HU04 label",()=>{
  const b=body();b.expected.storePhone="987654321";b.expected.delivery.label="";
  const intent=parseOrderSubmission(b,"buyer");assert.equal(intent.accepted.storePhone,"987654321");assert.equal(intent.accepted.delivery.label,"");
  assert.equal(intent.accepted.buyerPhone,null);
  const invalid=body();invalid.expected.buyerPhone="987654321";assert.throws(()=>parseOrderSubmission(invalid,"buyer"));
});
test("tampered amounts and technical payload oversize cannot become a valid submission",()=>{
  const b=body();b.expected.amounts.totalMinor++;assert.throws(()=>parseOrderSubmission(b,"buyer"));
  const huge=body();huge.expected.buyerName="x".repeat(513*1024);assert.throws(()=>parseOrderSubmission(huge,"buyer"));
});
test("Lima hours support start inclusive, end exclusive and previous weekday overnight",()=>{
  const hours=days.map(dayOfWeek=>({dayOfWeek,enabled:dayOfWeek==="MONDAY",openTime:"22:00",closeTime:"02:00"}));
  assert.equal(withinStoreHours(hours,new Date("2026-11-03T03:00:00.000Z")),true); // Mon 22 Lima
  assert.equal(withinStoreHours(hours,new Date("2026-11-03T06:59:59.999Z")),true);
  assert.equal(withinStoreHours(hours,new Date("2026-11-03T07:00:00.000Z")),false);
  assert.equal(withinStoreHours(hours,new Date("2026-11-02T06:00:00.000Z")),false);
});
test("incomplete, duplicated, invalid or zero-length schedules fail closed",()=>{
  const valid=days.map(dayOfWeek=>({dayOfWeek,enabled:true,openTime:"08:00",closeTime:"22:00"}));
  for(const bad of [[],valid.slice(1),[...valid.slice(0,6),valid[0]],valid.map(h=>({...h,openTime:"24:00"})),valid.map(h=>({...h,closeTime:"08:00"}))]) {
    assert.throws(()=>withinStoreHours(bad,new Date()));
  }
});
