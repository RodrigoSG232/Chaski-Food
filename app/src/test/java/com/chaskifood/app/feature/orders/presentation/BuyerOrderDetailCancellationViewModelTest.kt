package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerOrderDetailCancellationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = BuyerOrdersSession("buyer", 1)
    private val orderId = "a".repeat(64)

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    private fun detail(status: String): BuyerOrderDetail {
        val historical = status in setOf("CANCELLED", "COMPLETED", "REJECTED")
        val line = StoreOrderLine("product", "Plato", 1, 1000, 1000)
        val summary = BuyerOrderSummary(orderId, 1_800_000_000_000, status, true,
            if (historical) BuyerOrderPhase.HISTORICAL else BuyerOrderPhase.ACTIVE,
            "Local", "Negocio", 1, 1300, listOf(BuyerOrderItemPreview("Plato", 1)), 1)
        return BuyerOrderDetail(summary, null, "Av. Local", BuyerOrderDelivery("Casa", "Av. Cliente", null, null),
            listOf(line), OrderAmounts(1000, 300, 1300), OrderPaymentMethod.CASH_ON_DELIVERY,
            listOf(BuyerOrderStatusEntry(status, true, 1_800_000_000_000)))
    }

    private class Repo(initial: BuyerOrdersSession) : BuyerOrdersRepository {
        val sessions = MutableStateFlow<BuyerOrdersSession?>(initial)
        val details = ArrayDeque<BuyerOrderDetailResult>()
        var cancellationResult: BuyerOrderCancellationResult = BuyerOrderCancellationResult.Unavailable
        var cancellationCalls = 0
        override fun observeSession(): Flow<BuyerOrdersSession?> = sessions
        override fun isCurrent(session: BuyerOrdersSession) = sessions.value == session
        override suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?) = BuyerOrdersResult.Unavailable
        override suspend fun detail(session: BuyerOrdersSession, orderId: String) = details.removeFirst()
        override suspend fun cancel(session: BuyerOrdersSession, orderId: String): BuyerOrderCancellationResult {
            cancellationCalls++
            return cancellationResult
        }
    }

    @Test fun pendingOrderCancelsAndReloadsAsHistorical() = runTest {
        val repo = Repo(session).apply {
            details += BuyerOrderDetailResult.Loaded(detail("PENDING_CONFIRMATION"))
            details += BuyerOrderDetailResult.Loaded(detail("CANCELLED"))
            cancellationResult = BuyerOrderCancellationResult.Cancelled
        }
        val viewModel = BuyerOrderDetailViewModel(repo, SavedStateHandle(mapOf("orderId" to orderId)))
        runCurrent()

        viewModel.cancel()
        runCurrent()

        assertEquals(1, repo.cancellationCalls)
        assertEquals("CANCELLED", viewModel.state.value.detail?.summary?.status)
        assertEquals("Pedido cancelado.", viewModel.state.value.notice)
        assertFalse(viewModel.state.value.cancelling)
    }

    @Test fun preparingOrderNeverCallsCancellationRepository() = runTest {
        val repo = Repo(session).apply { details += BuyerOrderDetailResult.Loaded(detail("PREPARING")) }
        val viewModel = BuyerOrderDetailViewModel(repo, SavedStateHandle(mapOf("orderId" to orderId)))
        runCurrent()

        viewModel.cancel()
        runCurrent()

        assertEquals(0, repo.cancellationCalls)
        assertEquals("PREPARING", viewModel.state.value.detail?.summary?.status)
    }

    @Test fun conflictReloadsLatestStateAndExplainsWhyCancellationStopped() = runTest {
        val repo = Repo(session).apply {
            details += BuyerOrderDetailResult.Loaded(detail("PENDING_CONFIRMATION"))
            details += BuyerOrderDetailResult.Loaded(detail("PREPARING"))
            cancellationResult = BuyerOrderCancellationResult.NotAllowed
        }
        val viewModel = BuyerOrderDetailViewModel(repo, SavedStateHandle(mapOf("orderId" to orderId)))
        runCurrent()

        viewModel.cancel()
        runCurrent()

        assertEquals("PREPARING", viewModel.state.value.detail?.summary?.status)
        assertEquals("El estado del pedido cambió. Ya no se puede cancelar desde aquí.", viewModel.state.value.notice)
    }
}
