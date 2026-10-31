import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { encodeInboxCursor,parseInboxQuery,projectPendingOrder } from "../src/orders/storeOrdersPolicy";
const id="a".repeat(64);
const order=()=>({schemaVersion:1,orderId:id,status:"PENDING_CONFIRMATION",createdAt:new Timestamp(1800000000,123456789),
  items:[{productId:"product",name:"Plato",quantity:2,unitPriceMinor:1000,lineTotalMinor:2000}],subtotalMinor:2000,deliveryFeeMinor:300,totalMinor:2300,
  currency:"PEN",paymentMethod:"CASH_ON_DELIVERY",buyerSnapshot:{phone:"PRIVATE",displayName:"PRIVATE"},dropoffSnapshot:{addressText:"PRIVATE"},
  intentId:"PRIVATE",requestHash:"PRIVATE",quoteSnapshot:{quoteId:"PRIVATE"}});
test("explicit minimal projection omits private snapshots and keeps exact amounts",()=>{
  const projected=projectPendingOrder(id,order());
  assert.deepEqual(Object.keys(projected).sort(),["orderId","createdAtEpochMillis","status","items","subtotalMinor","deliveryFeeMinor","totalMinor","currency","paymentMethod"].sort());
  assert.equal(projected.createdAtEpochMillis,1800000000123);assert.equal(projected.totalMinor,2300);
  assert.equal(JSON.stringify(projected).includes("PRIVATE"),false);
});
test("corrupted source cannot silently become a partial or free order",()=>{
  for(const change of [{schemaVersion:2},{orderId:"other"},{status:"PREPARING"},{currency:"USD"},{paymentMethod:"CARD"},{createdAt:"bad"},
    {totalMinor:2301},{subtotalMinor:"2000"},{deliveryFeeMinor:-1},{items:[]},
    {items:[{productId:"p",name:"Plato",quantity:1.5,unitPriceMinor:1000,lineTotalMinor:1500}]},
    {items:[{productId:"p",name:"Plato",quantity:2147483647,unitPriceMinor:Number.MAX_SAFE_INTEGER,lineTotalMinor:2000}]}])
    assert.throws(()=>projectPendingOrder(id,{...order(),...change}));
});
test("cursor is canonical and bound to UID and store; never carries a client timestamp",()=>{
  const cursor=encodeInboxCursor("manager","store",id),parsed=parseInboxQuery({cursor,pageSize:"2"},"manager","store");
  assert.equal(parsed.cursor?.anchorId,id);assert.equal(parsed.pageSize,2);
  assert.equal(Buffer.from(cursor,"base64url").toString("utf8").includes("manager"),false);
  for(const [uid,store] of [["other","store"],["manager","other"]])assert.throws(()=>parseInboxQuery({cursor},uid,store));
  for(const raw of ["",cursor+"=","../orders",Buffer.from(JSON.stringify({...JSON.parse(Buffer.from(cursor,"base64url").toString("utf8")),anchorId:"../bad"})).toString("base64url")])
    assert.throws(()=>parseInboxQuery({cursor:raw},"manager","store"));
});
test("query parameters have strict types and bounded page sizes",()=>{
  assert.equal(parseInboxQuery({},"m","s").pageSize,20);
  for(const q of [{pageSize:"0"},{pageSize:"51"},{pageSize:"1.5"},{pageSize:"01"},{pageSize:2},{pageSize:["2"]},{cursor:[]},{status:"OTHER"},{storeId:"other"}])
    assert.throws(()=>parseInboxQuery(q,"m","s"));
});
