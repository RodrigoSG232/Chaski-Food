import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { aggregateDashboard,dashboardWindow,parseDashboardQuery } from "../src/dashboard/dashboardPolicy";

const date=(iso:string)=>Timestamp.fromDate(new Date(iso));
const row=(id:string,storeId:string,createdAt:string,more:Record<string,unknown>={})=>({id,data:{
  schemaVersion:1,orderId:id,businessId:"business",storeId,currency:"PEN",subtotalMinor:1000,
  createdAt:date(createdAt),...more}});

test("dashboard query accepts only bounded days and valid identifiers",()=>{
  assert.deepEqual(parseDashboardQuery({businessId:"business",days:"30",storeId:"store"}),
    {businessId:"business",days:30,storeId:"store"});
  for(const bad of [{days:"31"},{days:["7"]},{storeId:"../secret"},{extra:"x"},{businessId:"../secret"}])
    assert.throws(()=>parseDashboardQuery({businessId:"business",days:"7",...bad}));
});

test("Lima calendar window counts successful deliveries independently of creation date",()=>{
  const now=Date.parse("2026-11-09T01:00:00Z"); // Nov 8, 20:00 in Lima
  const window=dashboardWindow(7,now);
  assert.deepEqual([window.dates[0],window.dates.at(-1)],["2026-11-02","2026-11-08"]);
  const created=[row("one","store","2026-11-08T04:30:00Z",{acceptedAt:date("2026-11-08T05:00:00Z")}),
    row("two","store","2026-11-08T05:30:00Z",{rejectedAt:date("2026-11-08T05:45:00Z")}),
    row("foreign","other","2026-11-08T06:00:00Z",{acceptedAt:date("2026-11-08T07:00:00Z")})];
  const completed=[row("old","store","2026-10-30T12:00:00Z",{completedAt:date("2026-11-08T15:00:00Z"),
    status:"COMPLETED",riderIntegration:{deliveryOutcome:"SUCCESS"},subtotalMinor:2500}),
    row("failed","store","2026-11-03T12:00:00Z",{completedAt:date("2026-11-08T16:00:00Z"),
      status:"COMPLETED",riderIntegration:{deliveryOutcome:"UNSUCCESSFUL"}}),
    row("foreign","other","2026-11-04T12:00:00Z",{completedAt:date("2026-11-08T17:00:00Z"),
      status:"COMPLETED",riderIntegration:{deliveryOutcome:"SUCCESS"}})];
  const metrics=aggregateDashboard({businessId:"business",storeIds:["store"],dates:window.dates,created,completed});
  assert.equal(metrics.orders,2);
  assert.equal(metrics.salesMinor,2500);
  assert.deepEqual([metrics.accepted,metrics.rejected,metrics.acceptanceRate],[1,1,50]);
  assert.equal(metrics.series.find(day=>day.date==="2026-11-07")?.orders,1); // 04:30 UTC is Nov 7 in Lima
  assert.equal(metrics.series.find(day=>day.date==="2026-11-08")?.salesMinor,2500);
});

test("authorized malformed or overflowing amounts fail instead of showing partial sales",()=>{
  const dates=["2026-11-08"];
  const base={businessId:"business",storeIds:["store"],dates,created:[]};
  const completed=(more:Record<string,unknown>)=>[row("one","store","2026-11-08T12:00:00Z",{
    completedAt:date("2026-11-08T13:00:00Z"),status:"COMPLETED",
    riderIntegration:{deliveryOutcome:"SUCCESS"},...more})];
  assert.throws(()=>aggregateDashboard({...base,completed:completed({subtotalMinor:"1000"})}));
  assert.throws(()=>aggregateDashboard({...base,completed:completed({completedAt:"bad"})}));
  assert.throws(()=>aggregateDashboard({...base,completed:completed({riderIntegration:{}})}));
  assert.throws(()=>aggregateDashboard({...base,completed:[...completed({subtotalMinor:Number.MAX_SAFE_INTEGER}),
    ...completed({subtotalMinor:1})]}));
});
