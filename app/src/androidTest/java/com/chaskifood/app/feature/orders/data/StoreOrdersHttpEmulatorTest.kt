package com.chaskifood.app.feature.orders.data

import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.cart.data.FirebaseCartSessionSource
import com.chaskifood.app.feature.checkout.data.FirebaseBuyerTokenSource
import com.chaskifood.app.feature.orders.domain.*
import com.google.firebase.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Test
import org.junit.Assert.*
import retrofit2.*
import retrofit2.http.*
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Extends the existing orderAndroid.fixture.ts; only demo Auth + Functions + Firestore. */
class StoreOrdersHttpEmulatorTest {
    private interface LabOperations {
        @POST("access/operations") suspend fun execute(@Header("Authorization") token:String,@Body body:JsonObject):Response<JsonObject>
    }
    @Test fun realHu34IdentityInboxHttpAndRevocationStayScoped()=runBlocking {
        require(BuildConfig.FIREBASE_MODE=="LOCAL")
        val android=InstrumentationRegistry.getInstrumentation().targetContext
        val host=InstrumentationRegistry.getArguments().getString("hu15Host","127.0.0.1").also {require(it=="127.0.0.1"||it=="10.0.2.2")}
        val app=requireNotNull(FirebaseApp.initializeApp(android,FirebaseOptions.Builder().setProjectId("demo-chaski-hu15")
            .setApplicationId("1:123456789:android:hu16inbox").setApiKey("demo-api-key").build(),"hu16-inbox-e2e"))
        val auth=FirebaseAuth.getInstance(app).apply{useEmulator(host,9099)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            withTimeout(15000){auth.signInWithEmailAndPassword("hu16-e2e-manager@example.test","emulator-password").await()}
            val sessions=FirebaseCartSessionSource(auth)
            val json=Json{ignoreUnknownKeys=true}
            val retrofit=Retrofit.Builder().baseUrl("http://$host:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/")
                .client(OkHttpClient()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            val driver=FirebaseAccessIdentityDriver(auth)
            val identity=SharedAccessIdentitySource(driver,scope)
            val facts=RemoteAccessFacts(retrofit.create(AccessFactsApi::class.java),driver,json,true)
            val shared=SharedAccessContext(identity,facts,scope,true)
            val source=SharedStoreOrdersAccessSource(shared,sessions)
            val api=retrofit.create(StoreOrdersApi::class.java)
            val tokens=FirebaseBuyerTokenSource(auth,sessions)
            val repo=HttpStoreOrdersRepository(api,tokens,source)
            val target=StoreOrdersTarget("hu16-e2e-owner","hu16-e2e-store")
            val allowed=withTimeout(15000){source.observe(target).filterIsInstance<StoreOrdersAuthorization.Allowed>().first()}.context
            val page=repo.page(allowed) as StoreOrdersResult.Loaded
            assertEquals(1,page.page.orders.size);assertEquals("Plato ficticio",page.page.orders.single().items.single().name)
            assertEquals(2,page.page.orders.single().items.single().quantity)
            assertTrue(source.current(StoreOrdersTarget("hu16-e2e-owner","foreign")) is StoreOrdersAuthorization.Denied)
            val revision=allowed.authorizationRevision
            auth.signOut()
            assertEquals(StoreOrdersResult.StaleContext,repo.page(allowed))
            withTimeout(15000){auth.signInWithEmailAndPassword("hu16-e2e-owner@example.test","emulator-password").await()}
            val owner=sessions.current();val ownerToken=tokens.token("hu16-e2e-owner",owner.generation)
            assertEquals(403,api.page(target.storeId,"Bearer $ownerToken",null,20).code())
            // Explicit test-only HU34 revocation against demo Functions; no production or direct private reads.
            val revoked=retrofit.create(LabOperations::class.java).execute("Bearer $ownerToken",buildJsonObject {
                put("action","REVOKE");put("operationId","revoke-inbox-android");put("expectedRevision",revision)
                put("businessId","hu16-e2e-owner");put("email","hu16-e2e-manager@example.test")
            })
            assertEquals(200,revoked.code())
            auth.signOut();withTimeout(15000){auth.signInWithEmailAndPassword("hu16-e2e-manager@example.test","emulator-password").await()}
            val current=sessions.current();val token=tokens.token("hu16-e2e-manager",current.generation)
            val denied=api.page(target.storeId,"Bearer $token",null,20)
            assertEquals(403,denied.code());assertNull(denied.body())
            assertEquals(StoreOrdersResult.StaleContext,repo.page(allowed))
        } finally {scope.cancel();auth.signOut();app.delete()}
    }
}
