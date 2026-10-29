/** Laboratorio Android HU16: solo demo y emuladores, sin tokens en salida ni archivos.
 * Requiere APKs LOCAL instalados y adb reverse para 9099, 8089 y 5001. */
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { spawnSync } from "node:child_process";
import { CommercialAccessService } from "../src/access/commercialAccessService";

async function run() {
  if (process.env.GCLOUD_PROJECT !== "demo-chaski-hu15" || process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8089" ||
      process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099" || !process.env.ANDROID_ADB) throw new Error("LOCAL_EMULATORS_REQUIRED");
  const app=initializeApp({projectId:"demo-chaski-hu15"},"android-order-fixture"),auth=getAuth(app),db=getFirestore(app);
  const access=new CommercialAccessService(db,auth);
  const actors=["hu16-e2e-admin","hu16-e2e-owner","hu16-e2e-buyer","hu16-e2e-manager"];
  const docs=["stores/hu16-e2e-store","categories/hu16-e2e-category","products/hu16-e2e-product",
      "store_product_configs/hu16-e2e-store_hu16-e2e-product","business_requests/hu16-e2e-owner","users/hu16-e2e-buyer"];
  try {
    for(const uid of actors) {
      await auth.deleteUser(uid).catch(()=>undefined);
      await auth.createUser({uid,email:`${uid}@example.test`,password:"emulator-password",emailVerified:true,displayName:"Cliente ficticio"});
    }
    await auth.setCustomUserClaims(actors[0],{admin:true});
    const admin={uid:actors[0],email:`${actors[0]}@example.test`,emailVerified:true,admin:true};
    const revisions=new Map<string,number>();
    for(const uid of actors.slice(1))revisions.set(uid,(await access.execute(admin,{action:"PREPARE",operationId:`prepare-${uid}`,
      email:`${uid}@example.test`,expectedRevision:0})).revision);
    await db.doc(docs[4]).set({ownerUid:actors[1],businessName:"Negocio ficticio",status:"APPROVED"});
    const minute=(Number(new Intl.DateTimeFormat("en-US",{timeZone:"America/Lima",hour:"2-digit",hourCycle:"h23"}).format())*60+
      Number(new Intl.DateTimeFormat("en-US",{timeZone:"America/Lima",minute:"2-digit"}).format())+720)%1440;
    const hh=(m:number)=>`${String(Math.floor(m/60)).padStart(2,"0")}:${String(m%60).padStart(2,"0")}`;
    await db.doc(docs[0]).set({businessId:actors[1],name:"Local ficticio",address:"Plaza pública Lima",phone:"987654321",
      status:"ACTIVE",operationalStatus:"OPEN",latitude:-12.0464,longitude:-77.0428,deliveryRadiusKm:5,
      operatingHours:["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"].map(dayOfWeek=>
        ({dayOfWeek,enabled:true,openTime:hh(minute),closeTime:hh((minute+1439)%1440)}))});
    await db.doc(docs[1]).set({businessId:actors[1],name:"Categoría",isActive:true});
    await db.doc(docs[2]).set({businessId:actors[1],categoryId:"hu16-e2e-category",name:"Plato ficticio",price:9999,prepTimeMinutes:15,isActive:true});
    await db.doc(docs[3]).set({businessId:actors[1],storeId:"hu16-e2e-store",productId:"hu16-e2e-product",price:10,
      availability:"AVAILABLE",updatedBy:actors[1],updatedAt:Timestamp.now()});
    await db.doc(docs[5]).set({uid:actors[2],displayName:"Cliente ficticio"});
    await db.doc(`${docs[5]}/addresses/address`).set({label:"",addressText:"Parque público Lima",location:{lat:-12.05,lng:-77.04},
      reference:"Puerta pública",instructions:"Llamar al llegar",createdAt:Timestamp.now(),updatedAt:Timestamp.now()});
    await access.execute({uid:actors[1],email:`${actors[1]}@example.test`,emailVerified:true,admin:false},
      {action:"ASSIGN",operationId:"assign-android",expectedRevision:revisions.get(actors[3])!,businessId:actors[1],
        email:`${actors[3]}@example.test`,storeIds:["hu16-e2e-store"],fullName:"Responsable ficticio",phone:"000"});
    const inbox=process.env.HU16_INBOX_ANDROID==="enabled";
    const classes="com.chaskifood.app.feature.checkout.data.CheckoutOrderHttpEmulatorTest"+
      (inbox?",com.chaskifood.app.feature.orders.data.StoreOrdersHttpEmulatorTest":"");
    const result=spawnSync(process.env.ANDROID_ADB!,["-s",process.env.ANDROID_SERIAL??"emulator-5554","shell","am","instrument","-w","-r",
      "-e","class",classes,"-e","hu15Host","127.0.0.1","-e","timeout_msec","60000",
      "com.chaskifood.app.test/androidx.test.runner.AndroidJUnitRunner"],{encoding:"utf8",timeout:180000});
    const output=(result.stdout??"")+(result.stderr??"");process.stdout.write(output);
    if(result.error||result.status!==0||!output.includes(inbox?"OK (2 tests)":"OK (1 test)")||output.includes("FAILURES"))throw new Error("ANDROID_ORDER_E2E_FAILED");
  } finally {
    for(const path of docs)await db.recursiveDelete(db.doc(path));
    for(const collection of ["food_orders","delivery_quotes","order_events"]) {
      const all=await db.collection(collection).get();
      for(const doc of all.docs)if(doc.get("buyerUid")==="hu16-e2e-buyer"||doc.get("ownerUid")==="hu16-e2e-buyer")await db.recursiveDelete(doc.ref);
    }
    await db.recursiveDelete(db.doc("order_attempts/hu16-e2e-buyer"));
    for(const uid of actors)await auth.deleteUser(uid).catch(()=>undefined);
    await db.terminate();await deleteApp(app);
  }
}
void run().catch(()=>{console.error("ANDROID_ORDER_FIXTURE_FAILED");process.exitCode=1;});
