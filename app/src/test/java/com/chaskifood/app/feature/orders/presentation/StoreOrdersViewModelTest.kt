package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoreOrdersViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private val target=StoreOrdersTarget("business","store")
    private fun context(uid:String="manager",generation:Long=1,store:String="store")=StoreOrdersContext(uid,generation,
        AccessProvenance(AccessSession(uid,generation),generation,1),2,StoreOrdersTarget("business",store))
    private fun item(id:String="order")=StoreOrderSummary(id,1800000000000,listOf(StoreOrderLine("p","Plato",1,1000,1000)),OrderAmounts(1000,300,1300))
    private fun loaded(id:String="order",cursor:String?=null)=StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",listOf(item(id)),cursor))
    private class Repo(initial:StoreOrdersAuthorization):StoreOrdersRepository {
        val auth=MutableStateFlow(initial);var current=true;var calls=0;val cursors=mutableListOf<String?>()
        var next:StoreOrdersResult=StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",emptyList(),null))
        var decision:StoreOrderDecisionResult=StoreOrderDecisionResult.Unavailable
        var extension:PreparationExtensionResult=PreparationExtensionResult.Unavailable
        val extensions=mutableListOf<Pair<String,PreparationExtension>>()
        var ready:MarkOrderReadyResult=MarkOrderReadyResult.Unavailable
        val decisions=mutableListOf<Pair<String,StoreOrderDecision>>()
        var block:suspend()->StoreOrdersResult={next}
        override fun observeAuthorization(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization> = auth
        override fun isCurrent(context:StoreOrdersContext)=current && auth.value==StoreOrdersAuthorization.Allowed(context)
        override suspend fun page(context:StoreOrdersContext,cursor:String?,status:OrderStatus):StoreOrdersResult {calls++;cursors+=cursor;return block()}
        override suspend fun respond(context:StoreOrdersContext,orderId:String,decision:StoreOrderDecision):StoreOrderDecisionResult {
            decisions+=orderId to decision;return this.decision
        }
        var extensionGate:CompletableDeferred<Unit>?=null
        override suspend fun extendPreparation(context:StoreOrdersContext,orderId:String,extension:PreparationExtension):PreparationExtensionResult {
            extensions+=orderId to extension;extensionGate?.await();return this.extension
        }
        override suspend fun markReady(context:StoreOrdersContext,orderId:String)=ready
    }
    private fun vm(repo:Repo,store:String="store")=StoreOrdersViewModel(repo,SavedStateHandle(mapOf("businessId" to "business","storeId" to store)))
    @Test fun firstLoadRefreshAndPagingUseExistingContextWithoutDuplicateCalls()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        assertEquals(1,repo.calls);assertEquals(1,vm.state.value.orders.size)
        repo.next=loaded("second");vm.loadMore();vm.loadMore();runCurrent()
        assertEquals(listOf(null,"next"),repo.cursors);assertEquals(2,vm.state.value.orders.size)
        repo.next=loaded("new");vm.refresh();runCurrent();assertEquals(listOf("new"),vm.state.value.orders.map{it.orderId})
    }
    @Test fun acceptAndRejectRemoveAnsweredOrdersAndExposeConcreteFeedback()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded();val vm=vm(repo);runCurrent()
        repo.decision=StoreOrderDecisionResult.Accepted(1_800_000_300_000);vm.accept("order",30);runCurrent()
        assertTrue(vm.state.value.orders.isEmpty());assertTrue(vm.state.value.message!!.contains("aceptado"))
        assertEquals(StoreOrderDecision.Accept(30),repo.decisions.single().second)

        repo.next=loaded("other");vm.refresh();runCurrent();repo.decision=StoreOrderDecisionResult.Rejected
        vm.reject("other",StoreOrderRejectionReason.OTHER,"Sin insumos");runCurrent()
        assertTrue(vm.state.value.orders.isEmpty());assertTrue(vm.state.value.message!!.contains("rechazado"))
    }
    @Test fun suspendedAcceptanceKeepsOrderAvailableForRejection()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded();val vm=vm(repo);runCurrent()
        repo.decision=StoreOrderDecisionResult.AcceptanceSuspended;vm.accept("order",20);runCurrent()
        assertEquals(listOf("order"),vm.state.value.orders.map{it.orderId});assertTrue(vm.state.value.message!!.contains("suspendido"))
        assertNull(vm.state.value.respondingOrderId)
    }
    private fun preparing(revision:Long=1,extended:Int=0)=StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",listOf(
        StoreOrderSummary("order",1800000000000,listOf(StoreOrderLine("p","Plato",1,1000,1000)),OrderAmounts(1000,300,1300),
            OrderStatus.PREPARING,estimatedPreparationMinutes=40,estimatedReadyAtEpochMillis=1800002400000,
            estimateRevision=revision,preparationExtendedMinutes=extended)),null))
    @Test fun extensionSendsCurrentRevisionAndUpdatesTheVisibleOrderWithoutTouchingTheInitialEstimate()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing(revision=1);val vm=vm(repo);runCurrent()
        repo.extension=PreparationExtensionResult.Extended(1800003000000,2,10);vm.extendPreparation("order",10);runCurrent()
        val sent=repo.extensions.single().second
        assertEquals(10,sent.additionalMinutes);assertEquals(1L,sent.expectedEstimateRevision)
        val order=vm.state.value.orders.single()
        assertEquals(40,order.estimatedPreparationMinutes);assertEquals(1800003000000,order.estimatedReadyAtEpochMillis)
        assertEquals(2L,order.estimateRevision);assertEquals(10,order.preparationExtendedMinutes)
        assertNull(vm.state.value.pendingExtension);assertTrue(vm.state.value.message!!.contains("ampliado"))
    }
    @Test fun twoIntentionalExtensionsUseDifferentOperationsAndInvalidMinutesAreNeverSent()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing(revision=0);val vm=vm(repo);runCurrent()
        repo.extension=PreparationExtensionResult.Extended(1800003000000,1,10);vm.extendPreparation("order",10);runCurrent()
        repo.extension=PreparationExtensionResult.Extended(1800003600000,2,20);vm.extendPreparation("order",10);runCurrent()
        assertEquals(2,repo.extensions.size)
        assertNotEquals(repo.extensions[0].second.operationId,repo.extensions[1].second.operationId)
        assertEquals(listOf(0L,1L),repo.extensions.map{it.second.expectedEstimateRevision})
        vm.extendPreparation("order",4);vm.extendPreparation("order",61);vm.extendPreparation("missing",10);runCurrent()
        assertEquals(2,repo.extensions.size)
    }
    @Test fun uncertainExtensionKeepsItsOperationAcrossReloadsAndRetriesWithTheSameId()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing();val vm=vm(repo);runCurrent()
        repo.extension=PreparationExtensionResult.Unavailable;vm.extendPreparation("order",15);runCurrent()
        val pending=vm.state.value.pendingExtension!!;assertEquals(15,pending.extension.additionalMinutes)
        assertTrue(vm.state.value.message!!.contains("no se confirmó",ignoreCase=true))
        vm.refresh();runCurrent();assertEquals(pending,vm.state.value.pendingExtension)
        repo.extension=PreparationExtensionResult.Extended(1800003300000,2,15);vm.retryPendingExtension();runCurrent()
        assertEquals(listOf(pending.extension,pending.extension),repo.extensions.map{it.second})
        assertNull(vm.state.value.pendingExtension)
    }
    @Test fun discardingAnUncertainExtensionReloadsInsteadOfSendingANewOne()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing();val vm=vm(repo);runCurrent()
        vm.extendPreparation("order",15);runCurrent();val calls=repo.calls
        repo.next=preparing(revision=2,extended=15);vm.discardPendingExtension();runCurrent()
        assertNull(vm.state.value.pendingExtension);assertEquals(calls+1,repo.calls);assertEquals(1,repo.extensions.size)
        assertEquals(2L,vm.state.value.orders.single().estimateRevision)
    }
    @Test fun revisionConflictOrStateChangeReloadsAndExplainsWithoutRetrying()=runTest {
        for((result,text) in listOf(PreparationExtensionResult.RevisionChanged to "Otro cambio",
            PreparationExtensionResult.OperationConflict to "No pudimos confirmar",
            PreparationExtensionResult.StateChanged to "ya no está en preparación")) {
            val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing();val vm=vm(repo);runCurrent()
            repo.extension=result;repo.next=preparing(revision=3);vm.extendPreparation("order",10);runCurrent()
            assertNull(vm.state.value.pendingExtension);assertEquals(1,repo.extensions.size)
            assertTrue(vm.state.value.message!!.contains(text));assertEquals(3L,vm.state.value.orders.single().estimateRevision)
        }
    }
    @Test fun inFlightExtensionShowsProgressBlocksDuplicatesAndNeverClaimsSuccessEarly()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing();val vm=vm(repo);runCurrent()
        val gate=CompletableDeferred<Unit>();repo.extensionGate=gate
        repo.extension=PreparationExtensionResult.Extended(1800003000000,2,10)
        vm.extendPreparation("order",10);runCurrent()
        assertEquals("order",vm.state.value.extendingOrderId);assertEquals("order",vm.state.value.respondingOrderId)
        assertNull(vm.state.value.message);assertEquals(1800002400000,vm.state.value.orders.single().estimatedReadyAtEpochMillis)
        vm.extendPreparation("order",10);vm.retryPendingExtension();vm.discardPendingExtension();runCurrent()
        assertEquals(1,repo.extensions.size)
        gate.complete(Unit);runCurrent()
        assertNull(vm.state.value.extendingOrderId);assertNull(vm.state.value.respondingOrderId)
        assertTrue(vm.state.value.message!!.contains("ampliado"))
        repo.extensionGate=null;repo.extension=PreparationExtensionResult.Unavailable;vm.extendPreparation("order",5);runCurrent()
        assertNull(vm.state.value.extendingOrderId);assertNotNull(vm.state.value.pendingExtension)
    }
    @Test fun lostAuthorizationDuringExtensionClosesTheInbox()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=preparing();val vm=vm(repo);runCurrent()
        repo.extension=PreparationExtensionResult.AccessDenied;vm.extendPreparation("order",10);runCurrent()
        assertTrue(vm.state.value.accessDenied);assertTrue(vm.state.value.orders.isEmpty())
    }
    @Test fun readyTransitionRemovesPreparingOrderAndShowsPickupCode()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()))
        repo.next=StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",listOf(StoreOrderSummary("order",1800000000000,
            listOf(StoreOrderLine("p","Plato",1,1000,1000)),OrderAmounts(1000,300,1300),OrderStatus.PREPARING,
            estimatedPreparationMinutes=30,estimatedReadyAtEpochMillis=1800001800000)),null))
        val vm=vm(repo);runCurrent();repo.ready=MarkOrderReadyResult.Ready("0042",1800001700000)
        vm.markReady("order");runCurrent()
        assertTrue(vm.state.value.orders.isEmpty());assertTrue(vm.state.value.message!!.contains("0042"))
    }
    @Test fun uidGenerationAndRevocationClearDataAndIgnoreUncooperativeLateResponses()=runTest {
        for(next in listOf(StoreOrdersAuthorization.Denied,StoreOrdersAuthorization.SignedOut,
            StoreOrdersAuthorization.Allowed(context("other",2)),StoreOrdersAuthorization.Allowed(context("manager",3)))) {
            val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded();val vm=vm(repo);runCurrent()
            val gate=CompletableDeferred<Unit>();repo.block={withContext(NonCancellable){gate.await()};loaded("old-private")}
            vm.refresh();runCurrent();repo.auth.value=next;runCurrent()
            // A new session may start its own request; it must not inherit the old page.
            assertTrue(vm.state.value.orders.isEmpty())
            repo.block={StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",emptyList(),null))}
            if(next is StoreOrdersAuthorization.Allowed)repo.auth.value=StoreOrdersAuthorization.Denied
            runCurrent();gate.complete(Unit);runCurrent();assertTrue(vm.state.value.orders.isEmpty())
        }
    }
    @Test fun synchronousRevocationClearsExistingPageEvenBeforeTheFlowEventArrives()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        val gate=CompletableDeferred<Unit>();repo.block={gate.await();loaded("late-private")}
        vm.loadMore();runCurrent();repo.current=false;gate.complete(Unit);runCurrent()
        assertTrue(vm.state.value.accessDenied);assertTrue(vm.state.value.orders.isEmpty());assertNull(vm.state.value.nextCursor)
    }
    @Test fun overlappingPageNeverDuplicatesOrdersOrKeepsAnInvalidContinuation()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        repo.next=loaded(cursor="another");vm.loadMore();runCurrent()
        assertEquals(1,vm.state.value.orders.size);assertNotNull(vm.state.value.message);assertNull(vm.state.value.nextCursor)
    }
    @Test fun http403RemovesAlreadyDisplayedOrdersAndCursor()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        repo.next=StoreOrdersResult.AccessDenied;vm.loadMore();runCurrent()
        assertTrue(vm.state.value.accessDenied);assertTrue(vm.state.value.orders.isEmpty());assertNull(vm.state.value.nextCursor)
    }
    @Test fun timeoutKeepsOnlySameAuthorizedPageAndManualRefreshRecovers()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        repo.next=StoreOrdersResult.Unavailable;vm.loadMore();runCurrent();assertNotNull(vm.state.value.message);assertEquals(1,vm.state.value.orders.size)
        repo.next=StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",emptyList(),null));vm.refresh();runCurrent()
        assertTrue(vm.state.value.orders.isEmpty());assertNull(vm.state.value.message)
    }
    @Test fun invalidTargetAndForeignLocalAuthorizationNeverQueryOrPublish()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context(store="other")));val vm=vm(repo);runCurrent()
        assertTrue(vm.state.value.accessDenied);assertEquals(0,repo.calls)
        val bad=StoreOrdersViewModel(repo,SavedStateHandle(mapOf("businessId" to "business","storeId" to "../other")));runCurrent()
        assertTrue(bad.state.value.accessDenied);assertEquals(0,repo.calls)
    }
    @Test fun differentLocalViewModelDoesNotInheritFirstLocalData()=runTest {
        val first=Repo(StoreOrdersAuthorization.Allowed(context()));first.next=loaded("private-one");val a=vm(first);runCurrent()
        val second=Repo(StoreOrdersAuthorization.Allowed(context(store="second")));val b=vm(second,"second");runCurrent()
        assertEquals("private-one",a.state.value.orders.single().orderId);assertTrue(b.state.value.orders.isEmpty())
    }
    @Test fun repeatedAuthorizationAndDoubleRefreshDoNotMultiplyRequests()=runTest {
        val gate=CompletableDeferred<Unit>();val c=context();val repo=Repo(StoreOrdersAuthorization.Allowed(c))
        repo.block={gate.await();loaded()};val vm=vm(repo);runCurrent();vm.refresh();vm.refresh();runCurrent();assertEquals(1,repo.calls)
        repo.auth.value=StoreOrdersAuthorization.Allowed(c);runCurrent();assertEquals(1,repo.calls);gate.complete(Unit);runCurrent()
    }
    @Test fun notificationStartsFreshHttpEvenWhenAnEarlierReadIsStillInFlight()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));val gate=CompletableDeferred<Unit>()
        repo.block={withContext(NonCancellable){gate.await()};loaded("older")}
        val vm=vm(repo);runCurrent();repo.block={loaded("fresh")};vm.refreshFromNotification();runCurrent()
        assertEquals(2,repo.calls);assertEquals("fresh",vm.state.value.orders.single().orderId)
        gate.complete(Unit);runCurrent();assertEquals("fresh",vm.state.value.orders.single().orderId)
        repo.current=false;vm.refreshFromNotification();runCurrent()
        assertTrue(vm.state.value.orders.isEmpty());assertEquals(2,repo.calls)
    }
}
