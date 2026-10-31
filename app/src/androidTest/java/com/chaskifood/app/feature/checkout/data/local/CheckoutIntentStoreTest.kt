package com.chaskifood.app.feature.checkout.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.address.domain.model.*
import com.chaskifood.app.feature.cart.data.*
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.data.mapper.*
import com.chaskifood.app.feature.cart.domain.*
import com.chaskifood.app.feature.checkout.data.CheckoutPreparationGuard
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

class CheckoutIntentStoreTest {
    private val android = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "hu16-intents-${UUID.randomUUID()}.db"
    private lateinit var db: ChaskiDatabase
    private lateinit var store: CheckoutIntentStore
    private val sessions = Sessions()
    private val guard = Guard()
    private val clock = object : CheckoutClock {
        override fun wallTimeMillis() = 1800000000000L
        override fun monotonicTimeMillis() = 1000L
    }
    private val original = Cart("a", listOf(CartProductSnapshot("business", "store", "p", "Plato", 1000, 2)))
    private val id = UUID.randomUUID().toString()
    @Before fun setup() = runBlocking<Unit> { open(); save(original) }
    @After fun clean() { db.close(); android.deleteDatabase(name) }
    private fun open() {
        db = Room.databaseBuilder(android, ChaskiDatabase::class.java, name).build()
        store = CheckoutIntentStore(db, sessions, guard, clock)
    }
    private suspend fun save(cart: Cart) = db.cartDao().saveCart(cart.toEntity(), cart.toItemEntities())
    private suspend fun revision(uid: String = "a") = db.cartDao().getCart(uid)!!.cart.revision
    private suspend fun reviewed(): Triple<CheckoutContext, CheckoutQuote, AcceptedPurchase> {
        val address = Address("address", "Casa", "Lima", AddressCoordinates(-12.0,-77.0), "", "", 1,1)
        val cart = db.cartDao().getCart("a")!!.toDomain()
        val q = DeliveryQuote(1250.0,300,CartCurrency.PEN,"lima-driving-car-v1",1800000000000,1800000300000,
            address.location,address.location,"quote-local")
        val lines = cart.items.map { CheckoutPricedItem(it.productId,it.productName,it.quantity,it.unitPriceMinor,it.unitPriceMinor) }
        val review = buildCheckoutQuote("a","store","Local","address","Casa","Lima",lines,q,10000)
        val accepted = AcceptedPurchase("a","business","store","Negocio","Local",null,"Cliente",null,
            OrderLocationSnapshot("Origen",address.location), OrderDeliverySnapshot("address","Casa",OrderLocationSnapshot("Lima",address.location,"","")),
            cart.items.map { OrderItem(it.productId,"category",it.productName,it.quantity,it.unitPriceMinor) },
            OrderAmounts(review.subtotalMinor,review.deliveryFeeMinor,review.totalMinor),AcceptedDeliveryQuote("quote-local",q))
        return Triple(CheckoutContext("a",sessions.current().generation,cart,address,revision()),review,accepted)
    }
    private suspend fun prepare(id: String = this.id): LocalOrderIntent {
        val (c,q,a)=reviewed()
        return ready(store.prepare(c,q,a,id))
    }
    private fun ready(result: LocalIntentResult): LocalOrderIntent {
        assertTrue("Expected Ready, got $result",result is LocalIntentResult.Ready)
        return (result as LocalIntentResult.Ready).stored
    }
    private fun success(intent: LocalOrderIntent, order: String="order-local") = OrderAttemptResult.Succeeded(
        OrderReceipt(order,intent.intent.intentId,intent.intent.accepted,1800000001000))

    @Test fun revisionNeverResetsOnClearRebuildNoOpOrReopen() = runBlocking<Unit> {
        assertEquals(1L,revision()); save(original); assertEquals(1L,revision())
        db.cartDao().deleteCart("a"); assertEquals(2L,revision())
        db.cartDao().deleteCart("a"); assertEquals(2L,revision())
        save(original); assertEquals(3L,revision())
        db.close(); open(); assertEquals(3L,revision())
    }
    @Test fun intentIsDurableBeforeFutureNetworkAndRecoversOriginalPayloadAfterReopen() = runBlocking<Unit> {
        val first=prepare(); val payload=db.checkoutIntentDao().get("a",id)!!.payload
        store.markInFlight(sessions.current(),id)
        db.close(); open()
        val restored=ready(store.recover(sessions.current(),id))
        assertEquals(LocalOrderIntentState.UNKNOWN,restored.state)
        assertEquals(first.intent.requestHash,restored.intent.requestHash)
        assertEquals(payload,db.checkoutIntentDao().get("a",id)!!.payload)
        assertTrue(restored.result is OrderAttemptResult.Unknown)
    }
    @Test fun successfulReceiptConsumesOnceAndDuplicateCannotConsumeNewItems() = runBlocking<Unit> {
        val pending=prepare(); val receipt=success(pending)
        val applied=ready(store.recordResult(sessions.current(),id,receipt))
        assertTrue(applied.receiptApplied && applied.cartConsumed)
        assertEquals(2L,revision()); assertTrue(db.cartDao().getCart("a")!!.items.isEmpty())
        save(original); val newRevision=revision()
        repeat(3) { ready(store.recordResult(sessions.current(),id,receipt)); ready(store.applyRecoveredReceipt(sessions.current(),id)) }
        assertEquals(newRevision,revision()); assertEquals(original,db.cartDao().getCart("a")!!.toDomain())
    }
    @Test fun abaIdenticalRebuildSurvivesOldReceipt() = runBlocking<Unit> {
        val pending=prepare(); db.cartDao().deleteCart("a"); save(original)
        val result=ready(store.recordResult(sessions.current(),id,success(pending)))
        assertTrue(result.receiptApplied); assertFalse(result.cartConsumed)
        assertEquals(original,db.cartDao().getCart("a")!!.toDomain()); assertEquals(3L,revision())
    }
    @Test fun changedQuantitySurvivesAndRejectedOrUnknownNeverConsume() = runBlocking<Unit> {
        val pending=prepare(); save(Cart("a",original.items.map { it.copy(quantity=3) }))
        ready(store.recordResult(sessions.current(),id,OrderAttemptResult.Unknown(OrderUncertainty.TIMEOUT)))
        assertTrue(store.prepare(reviewed().first,reviewed().second,reviewed().third,UUID.randomUUID().toString()) is LocalIntentResult.Unresolved)
        ready(store.recordResult(sessions.current(),id,success(pending)))
        assertEquals(3,db.cartDao().getCart("a")!!.toDomain().items.single().quantity)
        val other=prepare(UUID.randomUUID().toString())
        ready(store.recordResult(sessions.current(),other.intent.intentId,OrderAttemptResult.RejectedFinal(OrderRejection.REVIEW_REQUIRED)))
        assertEquals(3,db.cartDao().getCart("a")!!.toDomain().items.single().quantity)
    }
    @Test fun staleRevisionEvenWithIdenticalValuesRequiresReviewAndInsertsNothing() = runBlocking<Unit> {
        val (c,q,a)=reviewed(); db.cartDao().deleteCart("a"); save(original)
        assertEquals(LocalIntentResult.ReviewRequired,store.prepare(c,q,a,id))
        assertNull(db.checkoutIntentDao().get("a",id))
    }
    @Test fun contextOrQuoteNoLongerCurrentCannotPrepare() = runBlocking<Unit> {
        val (c,q,a)=reviewed(); guard.current=false
        assertEquals(LocalIntentResult.ReviewRequired,store.prepare(c,q,a,id)); guard.current=true
        assertEquals(LocalIntentResult.ReviewRequired,store.prepare(c,q.copy(deliveryQuoteDeadlineMillis=1000),a,id))
        assertNull(db.checkoutIntentDao().get("a",id))
    }
    @Test fun mutationDuringContextValidationIsCheckedAgainAtomically() = runBlocking<Unit> {
        val (c,q,a)=reviewed(); guard.onRead={ db.cartDao().deleteCart("a"); save(original) }
        assertEquals(LocalIntentResult.ReviewRequired,store.prepare(c,q,a,id)); assertNull(db.checkoutIntentDao().get("a",id))
    }
    @Test fun concurrentPreparationsCreateOneActiveIntentPerUid() = runBlocking<Unit> {
        val (c,q,a)=reviewed()
        val results=(1..20).map { async(Dispatchers.IO) { store.prepare(c,q,a,UUID.randomUUID().toString()) } }.awaitAll()
        assertEquals(1,results.count { it is LocalIntentResult.Ready })
        assertEquals(19,results.count { it is LocalIntentResult.Unresolved })
        assertEquals(original,db.cartDao().getCart("a")!!.toDomain())
    }
    @Test fun concurrentIdenticalIdAndReceiptsHaveOneConsumption() = runBlocking<Unit> {
        val (c,q,a)=reviewed()
        val prepared=(1..10).map { async(Dispatchers.IO) { ready(store.prepare(c,q,a,id)) } }.awaitAll()
        assertEquals(1,prepared.map { it.intent.requestHash }.distinct().size)
        val receipt=success(prepared.first())
        (1..20).map { async(Dispatchers.IO) { ready(store.recordResult(sessions.current(),id,receipt)) } }.awaitAll()
        assertEquals(2L,revision()); assertTrue(db.cartDao().getCart("a")!!.items.isEmpty())
    }
    @Test fun lateOriginalSessionOnlyPersistsReceiptAndOriginalUidCanReturnLater() = runBlocking<Unit> {
        val pending=prepare(); val old=sessions.current(); sessions.change("b")
        val b=Cart("b",original.items); save(b)
        assertEquals(LocalIntentResult.SessionChanged,store.recordResult(old,id,success(pending)))
        assertEquals(LocalIntentResult.NotFound,store.recover(sessions.current(),id))
        assertEquals(b,db.cartDao().getCart("b")!!.toDomain()); assertEquals(original,db.cartDao().getCart("a")!!.toDomain())
        sessions.change("a"); val restored=ready(store.recover(sessions.current(),id))
        assertEquals(LocalOrderIntentState.SUCCEEDED,restored.state); assertFalse(restored.receiptApplied)
        ready(store.applyRecoveredReceipt(sessions.current(),id)); assertTrue(db.cartDao().getCart("a")!!.items.isEmpty())
        assertEquals(b,db.cartDao().getCart("b")!!.toDomain())
    }
    @Test fun logoutThenSameUidRejectsOldSessionButKeepsIntentRecoverable() = runBlocking<Unit> {
        prepare(); val old=sessions.current(); sessions.change(null); sessions.change("a")
        assertEquals(LocalIntentResult.SessionChanged,store.recover(old,id))
        assertEquals(id,ready(store.recover(sessions.current(),id)).intent.intentId)
    }
    @Test fun invalidReceiptOrConflictingTerminalDoesNotWriteOrConsume() = runBlocking<Unit> {
        val pending=prepare(); val rejected=OrderAttemptResult.RejectedFinal(OrderRejection.REVIEW_REQUIRED)
        ready(store.recordResult(sessions.current(),id,rejected))
        assertEquals(LocalIntentResult.Conflict,store.recordResult(sessions.current(),id,success(pending)))
        assertEquals(original,db.cartDao().getCart("a")!!.toDomain()); assertEquals(1L,revision())
    }
    @Test fun coordinatorMutationsUseSamePersistentRevision() = runBlocking<Unit> {
        val coordinator=CartLocalCoordinator(db,db.cartDao(),sessions)
        coordinator.addValidatedItem(sessions.current(),original.items.single().copy(quantity=1))
        assertEquals(2L,revision())
        coordinator.decreaseQuantity("p",3); assertEquals(2L,revision())
        coordinator.removeProduct("p"); assertEquals(3L,revision())
        coordinator.addValidatedItem(sessions.current(),original.items.single()); assertEquals(4L,revision())
    }
    @Test fun overflowRollsBackCartAndRevision() = runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL("UPDATE carts SET revision = 9223372036854775807 WHERE ownerUid='a'")
        assertTrue(runCatching { db.cartDao().deleteCart("a") }.isFailure)
        assertEquals(Long.MAX_VALUE,revision()); assertEquals(original,db.cartDao().getCart("a")!!.toDomain())
    }
    @Test fun mutationRacingPreparationNeverPairsOldContentWithNewRevision() = runBlocking<Unit> {
        val (c,q,a)=reviewed(); val gate=CompletableDeferred<Unit>()
        val attempt=async(Dispatchers.IO) { gate.await();store.prepare(c,q,a,id) }
        val mutation=async(Dispatchers.IO) { gate.await();save(Cart("a",original.items.map { it.copy(quantity=3) })) }
        gate.complete(Unit); val result=attempt.await();mutation.await()
        if (result is LocalIntentResult.Ready) {
            assertEquals(1L,result.stored.baseCartRevision)
            ready(store.recordResult(sessions.current(),id,success(result.stored)))
        } else assertEquals(LocalIntentResult.ReviewRequired,result)
        assertEquals(3,db.cartDao().getCart("a")!!.toDomain().items.single().quantity)
        assertEquals(2L,revision())
    }
    @Test fun sessionChangeDuringConsumptionRollsBackThenPersistsOnlyOriginalReceipt() = runBlocking<Unit> {
        val pending=prepare();val old=sessions.current()
        sessions.checks=0;sessions.onCheck={ if(it==2) sessions.change("b") }
        assertEquals(LocalIntentResult.SessionChanged,store.recordResult(old,id,success(pending)))
        assertEquals(original,db.cartDao().getCart("a")!!.toDomain());assertEquals(1L,revision())
        assertFalse(db.checkoutIntentDao().get("a",id)!!.receiptApplied)
        assertEquals("SUCCEEDED",db.checkoutIntentDao().get("a",id)!!.state)
    }
    @Test fun uniqueReceiptFailureRollsBackConsumptionAndRevisionTogether() = runBlocking<Unit> {
        val first=prepare();ready(store.recordResult(sessions.current(),id,success(first)))
        save(original);val next=prepare(UUID.randomUUID().toString())
        assertEquals(LocalIntentResult.StorageError,store.recordResult(sessions.current(),next.intent.intentId,success(next)))
        assertEquals(original,db.cartDao().getCart("a")!!.toDomain());assertEquals(3L,revision())
        assertEquals("PREPARED",db.checkoutIntentDao().get("a",next.intent.intentId)!!.state)
    }
    @Test fun cancellationPropagatesWithoutPreparingAnIntent() = runBlocking<Unit> {
        val (c,q,a)=reviewed();guard.onRead={throw CancellationException("controlled")}
        assertTrue(runCatching {store.prepare(c,q,a,id)}.exceptionOrNull() is CancellationException)
        assertNull(db.checkoutIntentDao().get("a",id));assertEquals(original,db.cartDao().getCart("a")!!.toDomain())
    }
    private class Sessions : CartSessionSource {
        override val sessions=MutableStateFlow(CartSession("a",0))
        var checks=0
        var onCheck: (Int)->Unit={}
        override fun isCurrent(session:CartSession):Boolean { checks++;onCheck(checks);return current()==session }
        override fun current()=sessions.value
        fun change(uid:String?) { sessions.value=CartSession(uid,sessions.value.generation+1) }
    }
    private class Guard : CheckoutPreparationGuard {
        var current=true
        var onRead:suspend ()->Unit={}
        override suspend fun isCurrent(expected:CheckoutContext):Boolean { onRead();return current }
        override fun isObservedCurrent(expected:CheckoutContext)=current
    }
}
