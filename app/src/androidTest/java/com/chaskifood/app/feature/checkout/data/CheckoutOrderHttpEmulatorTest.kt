package com.chaskifood.app.feature.checkout.data

import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.address.domain.model.*
import com.chaskifood.app.feature.cart.data.*
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.data.mapper.*
import com.chaskifood.app.feature.cart.domain.*
import com.chaskifood.app.feature.checkout.data.local.CheckoutIntentStore
import com.chaskifood.app.feature.checkout.data.remote.*
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.search.data.FirebaseProductOfferReader
import com.google.firebase.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Prepared/cleaned by orderAndroid.fixture.ts, exclusively with emulator demo data. */
class CheckoutOrderHttpEmulatorTest {
    @Test fun realReviewRoomPost201AndGet200AreIdempotent()=runBlocking {
        require(BuildConfig.FIREBASE_MODE=="LOCAL")
        val host=CheckoutEmulatorFixture.HOST
        val android=InstrumentationRegistry.getInstrumentation().targetContext
        val app=requireNotNull(FirebaseApp.initializeApp(android,FirebaseOptions.Builder().setProjectId("demo-chaski-hu15")
            .setApplicationId("1:123456789:android:hu16orders").setApiKey("demo-api-key").build(),"hu16-order-e2e"))
        val auth=FirebaseAuth.getInstance(app).apply{useEmulator(host,9099)}
        val db=FirebaseFirestore.getInstance(app).apply{useEmulator(host,8089)
            firestoreSettings=FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()}
        val room=Room.inMemoryDatabaseBuilder(android,ChaskiDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            Log.i("HU16E2E","phase=auth")
            val user=withTimeout(15000){auth.signInWithEmailAndPassword("hu16-e2e-buyer@example.test","emulator-password").await().user!!}
            Log.i("HU16E2E","phase=local-context")
            val sessions=FirebaseCartSessionSource(auth)
            val local=CartLocalCoordinator(room,room.cartDao(),sessions)
            val offers=FirebaseProductOfferReader(db)
            val addresses=TestCheckoutAddresses();val selection=TestCheckoutSelection()
            val address=Address("address","","Parque público Lima",AddressCoordinates(-12.05,-77.04),"Puerta pública","Llamar al llegar",1,1)
            addresses.set(user.uid,address);selection.select(user.uid,address.id)
            val cart=Cart(user.uid,listOf(CartProductSnapshot("hu16-e2e-owner","hu16-e2e-store","hu16-e2e-product","Plato ficticio",1000,2)))
            room.cartDao().saveCart(cart.toEntity(),cart.toItemEntities())
            val contexts=CheckoutContextSource(sessions,DefaultCartRepository(sessions,addresses,offers,local),addresses,
                StoredCheckoutLocalSnapshotReader(local,selection))
            val observed=contexts.observe().stateIn(scope,SharingStarted.Eagerly,CheckoutContextState.Loading)
            val context=withTimeout(10000){observed.filterIsInstance<CheckoutContextState.Ready>().first()}.context
            val json=Json{ignoreUnknownKeys=true}
            val retrofit=Retrofit.Builder().baseUrl("http://$host:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/")
                .client(OkHttpClient()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            val tokens=FirebaseBuyerTokenSource(auth,sessions);val orderHttp=HttpOrderReader(retrofit.create(OrderApi::class.java),tokens)
            val store=CheckoutIntentStore(room,sessions,contexts,SystemCheckoutClock())
            var canPurchase=true
            val operations=CheckoutOrderOperations(store,sessions,orderHttp,CheckoutPurchaseGuard {uid,buy->uid==user.uid&&(!buy||canPurchase)})
            val repository=FirebaseCheckoutRepository(contexts,offers,db,
                HttpStorePurchaseEligibilityReader(retrofit.create(StoreEligibilityApi::class.java),tokens),
                HttpDeliveryQuoteReader(retrofit.create(DeliveryQuoteApi::class.java),tokens,json),SystemCheckoutClock(),
                CheckoutBuyerSnapshotSource(auth,sessions),operations)
            Log.i("HU16E2E","phase=review")
            val result=withTimeout(25000){repository.review(context)}
            assertTrue("Expected ready, got $result",result is CheckoutReviewResult.Ready)
            val reviewed=result as CheckoutReviewResult.Ready
            assertNotNull(reviewed.quote.acceptedPurchase)
            assertEquals("Negocio ficticio",reviewed.quote.acceptedPurchase!!.businessName)
            Log.i("HU16E2E","phase=confirm")
            val first=repository.confirm(context,reviewed.quote) as CheckoutConfirmation.Success
            assertTrue(first.cartConsumed);assertTrue(room.cartDao().getCart(user.uid)!!.items.isEmpty())
            Log.i("HU16E2E","phase=recover")
            val recovered=orderHttp.execute(com.chaskifood.app.feature.checkout.domain.OrderIntent(first.receipt.intentId,first.receipt.accepted),
                sessions.current().generation,false) as OrderHttpResult.Outcome
            assertEquals(first.receipt.orderId,(recovered.result as OrderAttemptResult.Succeeded).receipt.orderId)
            room.cartDao().saveCart(cart.toEntity(),cart.toItemEntities())
            canPurchase=false
            val own=repository.recover(user.uid,sessions.current().generation,first.receipt.intentId) as CheckoutConfirmation.Success
            assertEquals(first.receipt.orderId,own.receipt.orderId);assertEquals(cart,room.cartDao().getCart(user.uid)!!.toDomain())
            assertEquals(CheckoutConfirmation.PurchaseForbidden,repository.confirm(context,reviewed.quote))
        } finally {scope.cancel();room.close();auth.signOut();withTimeoutOrNull(3000){db.terminate().await()};app.delete()}
    }
}
