package com.chaskifood.app.feature.checkout.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.address.domain.model.*
import com.chaskifood.app.feature.cart.domain.*
import com.chaskifood.app.feature.checkout.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutConfirmationViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val clock=object:CheckoutClock {override fun wallTimeMillis()=1800000000000L;override fun monotonicTimeMillis()=1000L+dispatcher.scheduler.currentTime}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun context(uid:String="a",generation:Long=0,quantity:Int=1)=CheckoutContext(uid,generation,
        Cart(uid,listOf(CartProductSnapshot("business","store","p","Plato",1000,quantity))),
        Address("address","Casa","Lima",AddressCoordinates(-12.0,-77.0),"","",1,1),quantity.toLong())
    private fun quote(c:CheckoutContext)=buildCheckoutQuote(c.ownerUid,"store","Local","address","Casa","Lima",
        listOf(CheckoutPricedItem("p","Plato",1,1000,1000)),DeliveryQuote(1250.0,300,CartCurrency.PEN,"v1",1800000000000,1800000300000,
            c.selectedAddress!!.location,c.selectedAddress.location,"quote"),200000L)
    private inner class Repo:CheckoutRepository {
        val contexts=MutableStateFlow<CheckoutContextState>(CheckoutContextState.Ready(context()))
        var confirms=0;var gets=0;var retries=0;var reviews=0;var pending:CheckoutConfirmation=CheckoutConfirmation.None
        var result:CheckoutConfirmation=CheckoutConfirmation.Pending("original-id")
        var gate:CompletableDeferred<Unit>?=null
        override fun observeContext():Flow<CheckoutContextState> = contexts
        override suspend fun isContextCurrent(context:CheckoutContext)=(contexts.value as? CheckoutContextState.Ready)?.context==context
        override suspend fun review(context:CheckoutContext):CheckoutReviewResult {reviews++;return CheckoutReviewResult.Ready(quote(context))}
        override suspend fun pending(ownerUid:String,generation:Long,intentId:String?)=pending
        override suspend fun confirm(context:CheckoutContext,quote:CheckoutQuote):CheckoutConfirmation {
            confirms++;withContext(NonCancellable){gate?.await()};return result
        }
        override suspend fun recover(ownerUid:String,generation:Long,intentId:String):CheckoutConfirmation {gets++;assertEquals("original-id",intentId);return result}
        override suspend fun retry(ownerUid:String,generation:Long,intentId:String):CheckoutConfirmation {retries++;assertEquals("original-id",intentId);return result}
    }
    private fun TestScope.ready(repo:Repo, saved:SavedStateHandle=SavedStateHandle()):CheckoutViewModel {
        val vm=CheckoutViewModel(repo,clock,saved);vm.review();runCurrent();vm.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY);return vm
    }
    @Test fun pendingIntentSuppressesRemoteReviewOnResumeWithoutSending()=runTest {
        val repo=Repo();repo.pending=CheckoutConfirmation.Pending("original-id")
        val vm=CheckoutViewModel(repo,clock);vm.review();runCurrent()
        assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Pending)
        assertEquals(0,repo.reviews);assertEquals(0,repo.confirms)
    }
    @Test fun doubleTapOnlyDispatchesOneConfirmationAndNeverAutomaticallyRetries()=runTest {
        val repo=Repo();repo.gate=CompletableDeferred();val vm=ready(repo)
        vm.confirm();vm.confirm();runCurrent();assertEquals(1,repo.confirms);assertEquals(CheckoutConfirmation.Working,vm.uiState.value.confirmation)
        repo.gate!!.complete(Unit);runCurrent();assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Pending)
        vm.review();runCurrent();assertEquals(1,repo.confirms);assertEquals(0,repo.retries)
    }
    @Test fun noAcceptanceOrExpiredQuoteNeverDispatches()=runTest {
        val repo=Repo();val vm=CheckoutViewModel(repo,clock);vm.review();runCurrent();vm.confirm();runCurrent();assertEquals(0,repo.confirms)
        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY);advanceTimeBy(200000);runCurrent();vm.confirm();runCurrent();assertEquals(0,repo.confirms)
    }
    @Test fun unknownRecoveryAndExplicitRetryKeepOriginalId()=runTest {
        val repo=Repo();val vm=ready(repo);vm.confirm();runCurrent()
        vm.recover();runCurrent();assertEquals(1,repo.gets)
        vm.retryOriginal();runCurrent();assertEquals(1,repo.retries);assertEquals(1,repo.confirms)
    }
    @Test fun recreationAndSameUidReloginLoadPendingWithoutConsentOrPost()=runTest {
        val repo=Repo();repo.pending=CheckoutConfirmation.Pending("original-id")
        val vm=CheckoutViewModel(repo,clock);runCurrent();assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Pending)
        repo.contexts.value=CheckoutContextState.SignedOut;runCurrent()
        repo.contexts.value=CheckoutContextState.Ready(context(generation=2));runCurrent()
        assertFalse(vm.uiState.value.reviewComplete);assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Pending)
        val recreated=CheckoutViewModel(repo,clock);runCurrent();assertTrue(recreated.uiState.value.confirmation is CheckoutConfirmation.Pending)
        assertEquals(0,repo.confirms);assertEquals(0,repo.retries)
    }
    @Test fun lateResponseCannotPublishAcrossUidOrSessionGeneration()=runTest {
        for(next in listOf(context("b",1),context("a",2))) {
            val repo=Repo();repo.gate=CompletableDeferred();val vm=ready(repo);vm.confirm();runCurrent()
            repo.contexts.value=CheckoutContextState.SignedOut;runCurrent()
            repo.contexts.value=CheckoutContextState.Ready(next);runCurrent()
            repo.gate!!.complete(Unit);runCurrent();assertEquals(CheckoutConfirmation.None,vm.uiState.value.confirmation)
            assertFalse(vm.uiState.value.reviewComplete)
        }
    }
    @Test fun cartChangesDoNotDiscardPendingAttemptAndSavedReferenceIsUidScoped()=runTest {
        val repo=Repo();val saved=SavedStateHandle();val vm=ready(repo,saved);vm.confirm();runCurrent()
        repo.contexts.value=CheckoutContextState.Ready(context(quantity=3));runCurrent()
        assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Pending);assertEquals("a",saved.get<String>("order_owner"))
        repo.contexts.value=CheckoutContextState.Ready(context("b",2));runCurrent()
        assertEquals(CheckoutConfirmation.None,vm.uiState.value.confirmation);assertFalse(vm.uiState.value.acceptedPriceChanges)
    }
    @Test fun rejectionAllowsNewReviewButNeverAutomaticResend()=runTest {
        val repo=Repo();repo.result=CheckoutConfirmation.Rejected("original-id",OrderRejection.REVIEW_REQUIRED)
        val vm=ready(repo);vm.confirm();runCurrent();assertTrue(vm.uiState.value.confirmation is CheckoutConfirmation.Rejected)
        vm.review();runCurrent();assertEquals(CheckoutConfirmation.None,vm.uiState.value.confirmation);assertEquals(1,repo.confirms)
    }
}
