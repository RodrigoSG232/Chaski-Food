package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerOrdersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private val buyer = BuyerOrdersSession("buyer", 1)
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun row(n: Int, phase: BuyerOrderPhase = BuyerOrderPhase.ACTIVE, status: String = "PREPARING", known: Boolean = true) =
        BuyerOrderSummary(id(n), 1_800_000_000_000 - n, status, known, phase, "Local", "Negocio", 1, 1300)
    private fun page(phase: BuyerOrderPhase, rows: List<BuyerOrderSummary>, cursor: String?) = BuyerOrdersResult.Loaded(BuyerOrdersPage(phase, rows, cursor))

    private class Repo(initial: BuyerOrdersSession?) : BuyerOrdersRepository {
        val session = MutableStateFlow(initial); var current = true
        val calls = mutableListOf<Pair<BuyerOrderPhase, String?>>()
        val queue = ArrayDeque<BuyerOrdersResult>()
        var block: (suspend () -> BuyerOrdersResult)? = null
        override fun observeSession(): Flow<BuyerOrdersSession?> = session
        override fun isCurrent(session: BuyerOrdersSession) = current && this.session.value == session
        override suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?): BuyerOrdersResult {
            calls += phase to cursor; return block?.invoke() ?: queue.removeFirstOrNull() ?: BuyerOrdersResult.Unavailable
        }
        override suspend fun detail(session: BuyerOrdersSession, orderId: String) = BuyerOrderDetailResult.NotFound
    }

    @Test fun firstLoadPagingAndRefreshKeepChronologicalRowsWithoutDuplicates() = runTest {
        val repo = Repo(buyer); repo.queue += page(BuyerOrderPhase.ACTIVE, (1..20).map { row(it) }, "c1")
        val vm = BuyerOrdersViewModel(repo); runCurrent()
        assertEquals(20, vm.state.value.current.orders.size); assertTrue(vm.state.value.current.canSearchMore)
        repo.queue += page(BuyerOrderPhase.ACTIVE, (21..25).map { row(it) }, null)
        vm.loadMore(); vm.loadMore(); runCurrent()
        assertEquals(listOf(BuyerOrderPhase.ACTIVE to null, BuyerOrderPhase.ACTIVE to "c1"), repo.calls)
        assertEquals((1..25).map { id(it) }, vm.state.value.current.orders.map { it.orderId }); assertFalse(vm.state.value.current.canSearchMore)
        repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(99)), null); vm.refresh(); runCurrent()
        assertEquals(listOf(id(99)), vm.state.value.current.orders.map { it.orderId })
    }

    @Test fun emptyPagesWithCursorAutoContinueAtMostTwiceAndNeverClaimNoOrders() = runTest {
        val repo = Repo(buyer)
        repeat(3) { repo.queue += page(BuyerOrderPhase.ACTIVE, emptyList(), "c$it") }
        val vm = BuyerOrdersViewModel(repo); runCurrent()
        assertEquals(3, repo.calls.size) // 1 + AUTO_CONTINUE
        val state = vm.state.value.current
        assertTrue(state.orders.isEmpty()); assertFalse(state.confirmedEmpty); assertTrue(state.canSearchMore)
        repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(7, status = "AWAITING_RIDER", known = false)), null)
        vm.loadMore(); runCurrent()
        assertEquals(4, repo.calls.size); assertEquals("c2", repo.calls.last().second)
        assertFalse(vm.state.value.current.orders.single().statusKnown)
        repo.queue += page(BuyerOrderPhase.ACTIVE, emptyList(), null); vm.refresh(); runCurrent()
        assertTrue(vm.state.value.current.confirmedEmpty)
    }

    @Test fun phasesAreIndependentAndLoadedOnFirstSelection() = runTest {
        val repo = Repo(buyer); repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(1)), null)
        repo.queue += page(BuyerOrderPhase.HISTORICAL, listOf(row(2, BuyerOrderPhase.HISTORICAL, "COMPLETED")), null)
        val vm = BuyerOrdersViewModel(repo); runCurrent()
        vm.select(BuyerOrderPhase.HISTORICAL); runCurrent(); vm.select(BuyerOrderPhase.ACTIVE); vm.select(BuyerOrderPhase.HISTORICAL); runCurrent()
        assertEquals(listOf(BuyerOrderPhase.ACTIVE to null, BuyerOrderPhase.HISTORICAL to null), repo.calls)
        assertEquals(listOf(id(1)), vm.state.value.phases.getValue(BuyerOrderPhase.ACTIVE).orders.map { it.orderId })
        assertEquals(listOf(id(2)), vm.state.value.phases.getValue(BuyerOrderPhase.HISTORICAL).orders.map { it.orderId })
    }

    @Test fun accountChangeAndSignOutDiscardLateResponsesOfThePreviousSession() = runTest {
        for (next in listOf(null, BuyerOrdersSession("other", 2), BuyerOrdersSession("buyer", 3))) {
            val repo = Repo(buyer); repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(1)), "c")
            val vm = BuyerOrdersViewModel(repo); runCurrent()
            val gate = CompletableDeferred<Unit>(); repo.block = { withContext(NonCancellable) { gate.await() }; page(BuyerOrderPhase.ACTIVE, listOf(row(66)), null) }
            vm.loadMore(); runCurrent(); repo.block = null // the old request is already waiting; the new session gets its own answer
            repo.session.value = next; runCurrent()
            assertTrue(vm.state.value.current.orders.isEmpty())
            assertEquals(next != null, vm.state.value.signedIn)
            gate.complete(Unit); runCurrent()
            assertTrue(vm.state.value.current.orders.none { it.orderId == id(66) || it.orderId == id(1) })
        }
    }

    @Test fun invalidCursorRestartsFromTheBeginningOnceAndDuplicatesStopPaging() = runTest {
        val repo = Repo(buyer); repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(1)), "old")
        val vm = BuyerOrdersViewModel(repo); runCurrent()
        repo.queue += BuyerOrdersResult.InvalidCursor; repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(2)), "c2")
        vm.loadMore(); runCurrent()
        assertEquals(listOf(null, "old", null), repo.calls.map { it.second })
        assertEquals(listOf(id(2)), vm.state.value.current.orders.map { it.orderId })
        repo.queue += page(BuyerOrderPhase.ACTIVE, listOf(row(2)), "c3"); vm.loadMore(); runCurrent()
        assertEquals(listOf(id(2)), vm.state.value.current.orders.map { it.orderId })
        assertNotNull(vm.state.value.current.message); assertFalse(vm.state.value.current.canSearchMore)
    }

    @Test fun detailLoadsOwnOrderAndTreatsMissingOrMalformedIdsAsNotFound() = runTest {
        val repo = object : BuyerOrdersRepository {
            val session = MutableStateFlow<BuyerOrdersSession?>(buyer); var ids = mutableListOf<String>()
            override fun observeSession() = session
            override fun isCurrent(session: BuyerOrdersSession) = this.session.value == session
            override suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?) = BuyerOrdersResult.Unavailable
            override suspend fun detail(session: BuyerOrdersSession, orderId: String): BuyerOrderDetailResult { ids += orderId; return BuyerOrderDetailResult.NotFound }
        }
        val bad = BuyerOrderDetailViewModel(repo, SavedStateHandle(mapOf("orderId" to "../x"))); runCurrent()
        assertTrue(bad.state.value.notFound); assertTrue(repo.ids.isEmpty())
        val vm = BuyerOrderDetailViewModel(repo, SavedStateHandle(mapOf("orderId" to id(5)))); runCurrent()
        assertTrue(vm.state.value.notFound); assertEquals(listOf(id(5)), repo.ids)
        repo.session.value = null; runCurrent(); assertFalse(vm.state.value.signedIn); assertNull(vm.state.value.detail)
    }
}
