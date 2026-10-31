package com.chaskifood.app.feature.checkout.data

import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.feature.checkout.presentation.CheckoutViewModel
import com.chaskifood.app.feature.checkout.presentation.RealCheckoutScreen
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.orders.presentation.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Rule
import java.util.UUID
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
    @get:Rule val compose=createComposeRule()
    @Test fun realReviewRoomPost201AndGet200AreIdempotent()=runBlocking {
        require(BuildConfig.FIREBASE_MODE=="LOCAL")
        val host=CheckoutEmulatorFixture.HOST
        val android=InstrumentationRegistry.getInstrumentation().targetContext
        val app=requireNotNull(FirebaseApp.initializeApp(android,FirebaseOptions.Builder().setProjectId("demo-chaski-hu15")
            .setApplicationId("1:123456789:android:hu16orders").setApiKey("demo-api-key").build(),"hu16-order-e2e"))
        val auth=FirebaseAuth.getInstance(app).apply{useEmulator(host,9099)}
        val db=FirebaseFirestore.getInstance(app).apply{useEmulator(host,8089)
            firestoreSettings=FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()}
        val closure=InstrumentationRegistry.getArguments().getString("hu16Closure")=="enabled"
        val databaseName="hu16-closure-${UUID.randomUUID()}.db"
        val room=if(closure)Room.databaseBuilder(android,ChaskiDatabase::class.java,databaseName).build()
            else Room.inMemoryDatabaseBuilder(android,ChaskiDatabase::class.java).build()
        val models=ViewModelStore()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        // Una sola composición por prueba: HU17 cambia el contenido mostrado sin volver a llamar a setContent.
        val screen=androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
        var openedOrder:String?=null
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
            val cartRepository=DefaultCartRepository(sessions,addresses,offers,local)
            if(closure){
                assertTrue(cartRepository.addProduct("hu16-e2e-store","hu16-e2e-product",2) is CartOperationResult.Updated)
                assertTrue(cartRepository.changeQuantity("hu16-e2e-product",3) is CartOperationResult.Updated)
                assertTrue(cartRepository.changeQuantity("hu16-e2e-product",2) is CartOperationResult.Updated)
            }else room.cartDao().saveCart(cart.toEntity(),cart.toItemEntities())
            val contexts=CheckoutContextSource(sessions,cartRepository,addresses,
                StoredCheckoutLocalSnapshotReader(local,selection))
            val observed=contexts.observe().stateIn(scope,SharingStarted.Eagerly,CheckoutContextState.Loading)
            val context=withTimeout(10000){observed.filterIsInstance<CheckoutContextState.Ready>().first()}.context
            val json=Json{ignoreUnknownKeys=true}
            var persistedBeforePost=false
            val loseResponse=closure && InstrumentationRegistry.getArguments().getString("hu16LoseResponse")=="enabled"
            val posts=java.util.concurrent.atomic.AtomicInteger(0)
            val lost=java.util.concurrent.atomic.AtomicBoolean(false)
            val client=OkHttpClient.Builder().addInterceptor {chain->
                if(closure && chain.request().method=="POST" && chain.request().url.encodedPath.endsWith("/orders")){
                    posts.incrementAndGet()
                    runBlocking {val frozen=room.checkoutIntentDao().unresolved(user.uid)
                        check(frozen!=null && frozen.state=="IN_FLIGHT");persistedBeforePost=true}
                }
                val response=chain.proceed(chain.request())
                if(loseResponse && chain.request().method=="POST" && chain.request().url.encodedPath.endsWith("/orders") &&
                    response.code==201 && lost.compareAndSet(false,true)){
                    response.close();throw java.net.SocketTimeoutException("LOCAL simulated lost response AFTER backend commit")
                }
                response
            }.build()
            val retrofit=Retrofit.Builder().baseUrl("http://$host:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/")
                .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            val tokens=FirebaseBuyerTokenSource(auth,sessions);val orderHttp=HttpOrderReader(retrofit.create(OrderApi::class.java),tokens)
            val store=CheckoutIntentStore(room,sessions,contexts,SystemCheckoutClock())
            val driver=FirebaseAccessIdentityDriver(auth)
            val identity=SharedAccessIdentitySource(driver,scope)
            val shared=SharedAccessContext(identity,RemoteAccessFacts(retrofit.create(AccessFactsApi::class.java),driver,json,true),scope,true)
            withTimeout(15000){shared.states.first {it.phase==AccessContextPhase.RESOLVED}}
            val authorization=SharedCheckoutPurchaseGuard(shared)
            var canPurchase=true
            val operations=CheckoutOrderOperations(store,sessions,orderHttp,CheckoutPurchaseGuard {uid,buy->authorization.allows(uid,buy)&&(!buy||canPurchase)})
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
            val first=if(closure){
                lateinit var model:CheckoutViewModel
                compose.runOnUiThread {model=CheckoutViewModel(repository,SystemCheckoutClock());models.put("checkout",model)}
                screen.value={RealCheckoutScreen(onBack={},onOpenCart={},onSelectAddress={},viewModel=model,onOpenOrder={openedOrder=it})}
                compose.setContent {ChaskiTheme {screen.value?.invoke()}}
                compose.waitUntil(25000){model.uiState.value.result is CheckoutReviewResult.Ready}
                compose.onNode(hasScrollAction()).performScrollToNode(hasText("Efectivo al recibir"))
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.RadioButton)).performClick()
                compose.onNode(hasScrollAction()).performScrollToNode(hasText("Confirmar pedido"))
                compose.onNode(hasText("Confirmar pedido") and hasClickAction()).performClick()
                if(loseResponse){
                    compose.waitUntil(25000){model.uiState.value.confirmation is CheckoutConfirmation.Pending}
                    assertFalse(room.cartDao().getCart(user.uid)!!.items.isEmpty())
                    val pending=room.checkoutIntentDao().unresolved(user.uid)!!
                    assertEquals("UNKNOWN",pending.state)
                    compose.onNode(hasScrollAction()).performScrollToNode(hasText("Consultar resultado"))
                    compose.onNodeWithText("Consultar resultado").performClick()
                }
                compose.waitUntil(25000){model.uiState.value.confirmation is CheckoutConfirmation.Success}
                val success=model.uiState.value.confirmation as CheckoutConfirmation.Success
                val shown="Pedido #${success.receipt.orderId.take(8).uppercase()}"
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(shown))
                compose.onNodeWithText(shown).assertIsDisplayed()
                // HU17: también tras una respuesta perdida y recuperada, «Ver mi pedido» abre el ID completo.
                compose.onNodeWithText("Pedido enviado").assertIsDisplayed()
                compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ver mi pedido"))
                compose.onNodeWithText("Ver mi pedido").performClick()
                compose.runOnIdle {assertEquals(success.receipt.orderId,openedOrder)}
                assertTrue(persistedBeforePost);assertEquals(1,posts.get())
                assertEquals("987654321",success.receipt.accepted.storePhone)
                assertEquals("",success.receipt.accepted.delivery.label);assertNull(success.receipt.accepted.buyerPhone)
                success
            }else repository.confirm(context,reviewed.quote) as CheckoutConfirmation.Success
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
            if(closure){
                Log.i("HU16E2E","phase=hu17-own-orders")
                verifyOwnOrdersUi(retrofit,tokens,sessions,user.uid,first.receipt.orderId,screen)
                Room.databaseBuilder(android,ChaskiDatabase::class.java,databaseName).build().let {reopened->
                    try {val stored=reopened.checkoutIntentDao().get(user.uid,first.receipt.intentId)!!
                        assertEquals("SUCCEEDED",stored.state);assertEquals(first.receipt.orderId,stored.receiptOrderId)
                        assertTrue(stored.receiptApplied)
                    }finally{reopened.close()}
                }
                Log.i("HU16E2E","phase=ui-room-http-recovery-verified")
            }
        } finally {compose.runOnUiThread {models.clear()};scope.cancel();room.close();if(closure)android.deleteDatabase(databaseName);auth.signOut();withTimeoutOrNull(3000){db.terminate().await()};app.delete()}
    }

    /** HU17 con datos reales de foodApi LOCAL: lista En curso y detalle histórico del pedido recién creado. */
    private suspend fun verifyOwnOrdersUi(retrofit:Retrofit,tokens:FirebaseBuyerTokenSource,sessions:FirebaseCartSessionSource,uid:String,
        orderId:String,screen:androidx.compose.runtime.MutableState<(@androidx.compose.runtime.Composable () -> Unit)?>) {
        val orders=com.chaskifood.app.feature.orders.data.HttpBuyerOrdersRepository(
            retrofit.create(com.chaskifood.app.feature.orders.data.BuyerOrdersApi::class.java),tokens,sessions)
        val session=BuyerOrdersSession(uid,sessions.current().generation)
        val page=(withTimeout(15000){orders.page(session,BuyerOrderPhase.ACTIVE)} as BuyerOrdersResult.Loaded).page
        val row=page.orders.single {it.orderId==orderId}
        assertEquals(listOf(BuyerOrderItemPreview("Plato ficticio",2)),row.itemPreview);assertEquals("Local ficticio",row.storeName)
        val historical=(withTimeout(15000){orders.page(session,BuyerOrderPhase.HISTORICAL)} as BuyerOrdersResult.Loaded).page
        assertTrue(historical.orders.none {it.orderId==orderId})
        val detail=(withTimeout(15000){orders.detail(session,orderId)} as BuyerOrderDetailResult.Loaded).detail
        assertEquals(listOf("PENDING_CONFIRMATION"),detail.statusHistory.map {it.status})
        assertEquals("Parque público Lima",detail.delivery.addressText);assertNull(detail.delivery.label)
        assertEquals(BuyerOrderDetailResult.NotFound,orders.detail(session,"f".repeat(64)))
        val listState=BuyerOrdersUiState(phases=BuyerOrderPhase.entries.associateWith {
            if(it==BuyerOrderPhase.ACTIVE)BuyerOrdersPhaseState(page.orders,page.nextCursor,loaded=true) else BuyerOrdersPhaseState()})
        var opened:String?=null
        compose.runOnUiThread {screen.value={BuyerOrdersContent(listState,{},{},{},{opened=it})}}
        val short="Pedido #${orderId.take(8).uppercase()}"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(short))
        compose.onNodeWithText(short).assertIsDisplayed().performClick()
        compose.onNodeWithText("2× Plato ficticio").assertIsDisplayed()
        compose.runOnIdle {assertEquals(orderId,opened)}
        compose.runOnUiThread {screen.value={BuyerOrderDetailContent(BuyerOrderDetailUiState(loading=false,detail=detail),{},{})}}
        compose.onNodeWithText(short).assertIsDisplayed()
        compose.onNodeWithText("Pendiente de confirmación · actual").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Efectivo contra entrega"))
        compose.onNodeWithText("Efectivo contra entrega").assertIsDisplayed()
        Log.i("HU16E2E","phase=hu17-own-orders-ui-verified")
    }
}
