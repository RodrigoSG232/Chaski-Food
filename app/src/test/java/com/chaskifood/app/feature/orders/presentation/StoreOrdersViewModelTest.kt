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
        var block:suspend()->StoreOrdersResult={next}
        override fun observeAuthorization(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization> = auth
        override fun isCurrent(context:StoreOrdersContext)=current && auth.value==StoreOrdersAuthorization.Allowed(context)
        override suspend fun page(context:StoreOrdersContext,cursor:String?):StoreOrdersResult {calls++;cursors+=cursor;return block()}
    }
    private fun vm(repo:Repo,store:String="store")=StoreOrdersViewModel(repo,SavedStateHandle(mapOf("businessId" to "business","storeId" to store)))
    @Test fun firstLoadRefreshAndPagingUseExistingContextWithoutDuplicateCalls()=runTest {
        val repo=Repo(StoreOrdersAuthorization.Allowed(context()));repo.next=loaded(cursor="next");val vm=vm(repo);runCurrent()
        assertEquals(1,repo.calls);assertEquals(1,vm.state.value.orders.size)
        repo.next=loaded("second");vm.loadMore();vm.loadMore();runCurrent()
        assertEquals(listOf(null,"next"),repo.cursors);assertEquals(2,vm.state.value.orders.size)
        repo.next=loaded("new");vm.refresh();runCurrent();assertEquals(listOf("new"),vm.state.value.orders.map{it.orderId})
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
}
