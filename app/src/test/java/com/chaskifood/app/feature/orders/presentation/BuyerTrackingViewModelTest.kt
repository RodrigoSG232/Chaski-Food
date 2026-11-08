package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerTrackingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private val id = "a".repeat(64)
    private val now = 1_800_000_000_000L
    private val tracking = BuyerTracking(id, "IN_DELIVERY", "ON_THE_WAY", true, "Ana", null,
        BuyerTrackingPosition(-12.1, -77.1, 10.0, now, now), now + 600_000, now)
    private class Repo : BuyerOrdersRepository {
        val session = MutableStateFlow<BuyerOrdersSession?>(BuyerOrdersSession("buyer", 1))
        var calls = 0
        var answer: BuyerTrackingResult = BuyerTrackingResult.Unavailable
        var block: (suspend () -> BuyerTrackingResult)? = null
        override fun observeSession() = session
        override fun isCurrent(session: BuyerOrdersSession) = this.session.value == session
        override suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?) = BuyerOrdersResult.Unavailable
        override suspend fun detail(session: BuyerOrdersSession, orderId: String) = BuyerOrderDetailResult.NotFound
        override suspend fun cancel(session: BuyerOrdersSession, orderId: String) = BuyerOrderCancellationResult.Unavailable
        override suspend fun tracking(session: BuyerOrdersSession, orderId: String): BuyerTrackingResult {
            calls++; return block?.invoke() ?: answer
        }
    }
    private fun vm(repo: Repo) = BuyerTrackingViewModel(repo, SavedStateHandle(mapOf("orderId" to id)))

    @Test fun pollsOnlyWhileResumedAndStopsOnTerminal() = runTest {
        val repo = Repo(); repo.answer = BuyerTrackingResult.Loaded(tracking)
        val vm = vm(repo); runCurrent(); assertEquals(0, repo.calls)
        vm.resume(); runCurrent(); assertEquals(1, repo.calls)
        advanceTimeBy(10_000); runCurrent(); assertEquals(2, repo.calls)
        vm.pause(); advanceTimeBy(60_000); runCurrent(); assertEquals(2, repo.calls)
        repo.answer = BuyerTrackingResult.Loaded(tracking.copy(status = "COMPLETED", stage = "COMPLETED",
            deliveryOutcome = "SUCCESS", position = null, estimatedArrivalAtEpochMillis = null))
        vm.resume(); runCurrent(); assertEquals(3, repo.calls)
        advanceTimeBy(60_000); runCurrent(); assertEquals(3, repo.calls)
        assertTrue(vm.state.value.tracking!!.terminal); vm.pause()
    }

    @Test fun transientFailureKeepsSnapshotBacksOffAndRecovers() = runTest {
        val repo = Repo(); repo.answer = BuyerTrackingResult.Loaded(tracking)
        val vm = vm(repo); vm.resume(); runCurrent()
        repo.answer = BuyerTrackingResult.Unavailable
        advanceTimeBy(10_000); runCurrent()
        assertEquals(tracking, vm.state.value.tracking); assertNotNull(vm.state.value.message)
        advanceTimeBy(10_000); runCurrent(); assertEquals(2, repo.calls)
        repo.answer = BuyerTrackingResult.Loaded(tracking.copy(stage = "AT_CUSTOMER"))
        advanceTimeBy(10_000); runCurrent()
        assertEquals(3, repo.calls); assertNull(vm.state.value.message)
        assertEquals("AT_CUSTOMER", vm.state.value.tracking!!.stage); vm.pause()
    }

    @Test fun authorizationErrorsClearCoordinatesAndStopPolling() = runTest {
        for (error in listOf(BuyerTrackingResult.AuthenticationRequired, BuyerTrackingResult.NotFound, BuyerTrackingResult.StaleSession)) {
            val repo = Repo(); repo.answer = BuyerTrackingResult.Loaded(tracking)
            val vm = vm(repo); vm.resume(); runCurrent()
            repo.answer = error; advanceTimeBy(10_000); runCurrent()
            assertNull(vm.state.value.tracking); assertNotNull(vm.state.value.message)
            advanceTimeBy(60_000); runCurrent(); assertEquals(2, repo.calls); vm.pause()
        }
    }

    @Test fun sessionSwitchAndLateResponseCannotRestorePrivateCoordinates() = runTest {
        val repo = Repo(); repo.answer = BuyerTrackingResult.Loaded(tracking)
        val vm = vm(repo); vm.resume(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        repo.block = { withContext(NonCancellable) { gate.await() }; BuyerTrackingResult.Loaded(tracking) }
        advanceTimeBy(10_000); runCurrent()
        repo.block = null; repo.answer = BuyerTrackingResult.Unavailable
        repo.session.value = BuyerOrdersSession("other", 2); runCurrent()
        assertNull(vm.state.value.tracking)
        gate.complete(Unit); runCurrent(); assertNull(vm.state.value.tracking)
        repo.session.value = null; runCurrent(); assertNull(vm.state.value.tracking); vm.pause()
    }
}
