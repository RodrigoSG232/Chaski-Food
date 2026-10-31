package com.chaskifood.app.feature.checkout.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.address.domain.model.*
import com.chaskifood.app.feature.cart.data.*
import com.chaskifood.app.feature.cart.data.mapper.*
import com.chaskifood.app.feature.cart.domain.*
import com.chaskifood.app.feature.checkout.data.local.*
import com.chaskifood.app.feature.checkout.data.remote.*
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import retrofit2.Response

class CheckoutOrderOperationsTest {
    private lateinit var db:ChaskiDatabase
    private lateinit var store:CheckoutIntentStore
    private lateinit var operations:CheckoutOrderOperations
    private val sessions=object:CartSessionSource {
        override val sessions=MutableStateFlow(CartSession("a",0))
        override fun current()=sessions.value
    }
    private var valid=true;private var purchase=true;private var posts=0;private var gets=0
    private var gate:CompletableDeferred<Unit>?=null
    private var failAfterSend=false
    private var status=201
    private var outcome="SUCCEEDED"
    private var original:JsonObject?=null
    private var sentIntent:OrderIntent?=null
    private var forceUnknown=false
    private val clock=object:CheckoutClock {override fun wallTimeMillis()=1800000000000L;override fun monotonicTimeMillis()=1000L}
    private val guard=object:CheckoutPreparationGuard {
        override suspend fun isCurrent(expected:CheckoutContext)=valid
        override fun isObservedCurrent(expected:CheckoutContext)=valid
    }
    private val cart=Cart("a",listOf(CartProductSnapshot("business","store","p","Plato",1000,2)))
    private val api=object:OrderApi {
        override suspend fun confirm(authorization:String,body:JsonObject):Response<JsonObject> {
            posts++
            val row=requireNotNull(db.checkoutIntentDao().unresolved("a"))
            assertEquals(LocalOrderIntentState.IN_FLIGHT.name,row.state)
            val persisted=OrderIntentCodec.decode(row.payload)
            assertEquals(OrderWireCodec.submission(persisted),body)
            if(original!=null)assertEquals(original,body)
            original=body;sentIntent=persisted
            gate?.await()
            if(failAfterSend)throw java.net.SocketTimeoutException()
            return response(persisted)
        }
        override suspend fun recover(authorization:String,intentId:String):Response<JsonObject> {
            gets++;val i=requireNotNull(sentIntent);assertEquals(i.intentId,intentId)
            if(forceUnknown)return Response.error(404,buildJsonObject {put("contractVersion",1);put("intentId",intentId)
                put("outcome","UNKNOWN");put("reason","NOT_OBSERVED");put("error",buildJsonObject{put("code","ATTEMPT_NOT_OBSERVED")})}.toString().toResponseBody())
            return response(i,200)
        }
    }
    private fun response(i:OrderIntent,http:Int=status):Response<JsonObject> {
        val body=buildJsonObject {put("contractVersion",1);put("intentId",i.intentId);put("outcome",outcome)
            if(outcome=="SUCCEEDED")put("receipt",buildJsonObject {put("orderId","order-local");put("intentId",i.intentId)
                put("status","PENDING_CONFIRMATION");put("createdAtEpochMillis",1800000001000L);put("accepted",OrderWireCodec.accepted(i.accepted))})
            if(outcome=="REJECTED_FINAL")put("reason","REVIEW_REQUIRED")
        }
        return if(http in 200..299)Response.success(http,body) else Response.error(http,body.toString().toResponseBody())
    }
    @Before fun before()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,ChaskiDatabase::class.java).build()
        db.cartDao().saveCart(cart.toEntity(),cart.toItemEntities())
        store=CheckoutIntentStore(db,sessions,guard,clock)
        operations=CheckoutOrderOperations(store,sessions,HttpOrderReader(api,object:BuyerTokenSource {
            override suspend fun token(uid:String,generation:Long)="fixture-token"
            override fun isCurrent(uid:String,generation:Long)=sessions.isCurrent(CartSession(uid,generation))
        }),CheckoutPurchaseGuard {uid,newPurchase->sessions.current().uid==uid && (!newPurchase||purchase)})
    }
    @After fun after(){db.close()}
    private suspend fun review():Pair<CheckoutContext,CheckoutQuote> {
        val a=Address("address","","Lima",AddressCoordinates(-12.0,-77.0),"","",1,1)
        val q=DeliveryQuote(1250.0,300,CartCurrency.PEN,"v1",1800000000000,1800000300000,a.location,a.location,"quote")
        val reviewed=buildCheckoutQuote("a","store","Local",a.id,a.label,a.addressText,
            listOf(CheckoutPricedItem("p","Plato",2,1000,1000)),q,10000)
        val accepted=AcceptedPurchase("a","business","store","Negocio","Local","987654321","Cliente",null,
            OrderLocationSnapshot("Origen",a.location),OrderDeliverySnapshot(a.id,a.label,OrderLocationSnapshot(a.addressText,a.location,"","")),
            listOf(OrderItem("p","category","Plato",2,1000)),OrderAmounts(2000,300,2300),AcceptedDeliveryQuote("quote",q))
        val c=CheckoutContext("a",sessions.current().generation,cart,a,db.cartDao().getCart("a")!!.cart.revision)
        return c to reviewed.copy(acceptedPurchase=accepted)
    }
    private suspend fun confirm():CheckoutConfirmation {val(c,q)=review();return operations.confirm(c,q)}
    private fun pending(r:CheckoutConfirmation)= (r as CheckoutConfirmation.Pending).intentId
    private suspend fun currentCart()=db.cartDao().getCart("a")!!.toDomain()

    @Test fun persistsBeforeHttpAndApplies201ExactlyOnce()=runBlocking {
        val result=confirm() as CheckoutConfirmation.Success
        assertTrue(result.cartConsumed);assertEquals(1,posts);assertTrue(currentCart().items.isEmpty())
        db.cartDao().saveCart(cart.toEntity(),cart.toItemEntities())
        val duplicate=operations.resolve(sessions.current(),result.receipt.intentId,false) as CheckoutConfirmation.Success
        assertEquals(result.receipt.orderId,duplicate.receipt.orderId);assertEquals(cart,currentCart());assertEquals(0,gets)
    }
    @Test fun storageFailureNeverSendsHttp()=runBlocking {
        val(c,q)=review()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_intent BEFORE INSERT ON checkout_intents BEGIN SELECT RAISE(ABORT, 'fixture persistence failure'); END")
        assertEquals(CheckoutConfirmation.StorageError,operations.confirm(c,q));assertEquals(0,posts)
    }
    @Test fun simultaneousConfirmationsShareLockBeyondViewModel()=runBlocking {
        gate=CompletableDeferred();val(c,q)=review()
        val first=async{operations.confirm(c,q)}
        withTimeout(5000){while(posts==0)delay(10)}
        assertEquals(CheckoutConfirmation.Busy,operations.confirm(c,q));assertEquals(1,posts)
        gate!!.complete(Unit);assertTrue(first.await() is CheckoutConfirmation.Success)
    }
    @Test fun timeoutAfterCommitRecovers200WithoutSecondPost()=runBlocking {
        failAfterSend=true;val id=pending(confirm());assertEquals(cart,currentCart())
        failAfterSend=false
        assertTrue(operations.resolve(sessions.current(),id,false) is CheckoutConfirmation.Success)
        assertEquals(1,posts);assertEquals(1,gets);assertTrue(currentCart().items.isEmpty())
    }
    @Test fun unknown404AndExplicitRetryKeepFrozenPayloadDespiteCartChanges()=runBlocking {
        failAfterSend=true;val id=pending(confirm());val payload=original
        forceUnknown=true
        assertTrue(operations.resolve(sessions.current(),id,false) is CheckoutConfirmation.Pending)
        val edited=Cart("a",cart.items.map{it.copy(quantity=3)})
        db.cartDao().saveCart(edited.toEntity(),edited.toItemEntities())
        forceUnknown=false;failAfterSend=false
        val result=operations.resolve(sessions.current(),id,true) as CheckoutConfirmation.Success
        assertFalse(result.cartConsumed);assertEquals(edited,currentCart());assertEquals(payload,original);assertEquals(2,posts)
    }
    @Test fun clearAndIdenticalRebuildSurvivesOldReceiptABA()=runBlocking {
        failAfterSend=true;val id=pending(confirm());db.cartDao().deleteCart("a")
        db.cartDao().saveCart(cart.toEntity(),cart.toItemEntities());failAfterSend=false
        val result=operations.resolve(sessions.current(),id,false) as CheckoutConfirmation.Success
        assertFalse(result.cartConsumed);assertEquals(cart,currentCart())
    }
    @Test fun lateResponsePersistsForOriginalUidWithoutConsumingOtherSession()=runBlocking {
        gate=CompletableDeferred();val(c,q)=review();val first=async{operations.confirm(c,q)}
        withTimeout(5000){while(posts==0)delay(10)}
        sessions.sessions.value=CartSession("b",1);gate!!.complete(Unit)
        assertEquals(CheckoutConfirmation.SessionChanged,first.await());assertEquals(cart,currentCart())
        assertEquals(CheckoutConfirmation.None,operations.pending(sessions.current(),null))
        sessions.sessions.value=CartSession("a",2)
        val recovered=operations.pending(sessions.current(),null) as CheckoutConfirmation.Success
        assertEquals("order-local",recovered.receipt.orderId);assertEquals(1,posts)
    }
    @Test fun sameUidNewGenerationAndRevokedStartStillAllowPersonalRecovery()=runBlocking {
        failAfterSend=true;val id=pending(confirm());purchase=false
        sessions.sessions.value=CartSession(null,1);sessions.sessions.value=CartSession("a",2)
        assertTrue(operations.pending(sessions.current(),null) is CheckoutConfirmation.Pending)
        assertEquals(CheckoutConfirmation.PurchaseForbidden,operations.resolve(sessions.current(),id,true))
        failAfterSend=false
        assertTrue(operations.resolve(sessions.current(),id,false) is CheckoutConfirmation.Success);assertEquals(1,posts)
    }
    @Test fun rejectedFinalKeepsCartAndNextAcceptedReviewUsesNewId()=runBlocking {
        outcome="REJECTED_FINAL";status=409
        val rejected=confirm() as CheckoutConfirmation.Rejected;assertEquals(cart,currentCart())
        original=null;val again=confirm() as CheckoutConfirmation.Rejected
        assertNotEquals(rejected.intentId,again.intentId);assertEquals(2,posts)
    }
    @Test fun staleContextExpiredQuoteOrNoStartNeverSends()=runBlocking {
        val(c,q)=review();valid=false
        assertEquals(CheckoutConfirmation.ReviewRequired,operations.confirm(c,q));valid=true
        assertEquals(CheckoutConfirmation.ReviewRequired,operations.confirm(c,q.copy(deliveryQuoteDeadlineMillis=1000)))
        purchase=false;assertEquals(CheckoutConfirmation.PurchaseForbidden,operations.confirm(c,q));assertEquals(0,posts)
    }
}
