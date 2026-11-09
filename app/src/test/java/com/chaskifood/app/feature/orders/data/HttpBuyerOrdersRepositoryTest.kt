package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.checkout.data.BuyerAuthenticationRequired
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class HttpBuyerOrdersRepositoryTest {
    private val id = "c".repeat(64)
    private val session = BuyerOrdersSession("buyer", 3)
    private val sessions = object : CartSessionSource {
        val state = MutableStateFlow(CartSession("buyer", 3))
        override val sessions: StateFlow<CartSession> = state
        override fun current() = state.value
    }
    private var tokenError: Exception? = null
    private val tokens = object : BuyerTokenSource {
        override suspend fun token(uid: String, generation: Long): String { tokenError?.let { throw it }; assertEquals("buyer", uid); return "fixture-token" }
        override fun isCurrent(uid: String, generation: Long) = sessions.isCurrent(CartSession(uid, generation))
    }
    private fun summary(phase: String = "ACTIVE") = BuyerOrderSummaryResponse(id, 1_800_000_000_000, "PREPARING", phase, true, "Local", "Negocio", 2, 2300, "PEN",
        listOf(BuyerOrderItemPreviewResponse("Plato", 2)), 1)
    private fun detail() = BuyerOrderDetailResponse(id, 1_800_000_000_000, "PREPARING", "ACTIVE", true, "Local", "Negocio", 2, 2300, "PEN", null,
        "Av. Local 1", BuyerOrderDeliveryResponse("Casa", "Calle 2", null, null), listOf(StoreOrderLineResponse("p", "Plato", 2, 1000, 2000)),
        2000, 300, "CASH_ON_DELIVERY", listOf(BuyerOrderStatusResponse("PENDING_CONFIRMATION", true, 1_800_000_000_000),
            BuyerOrderStatusResponse("PREPARING", true, 1_800_000_000_100)),
        listOf(BuyerOrderItemPreviewResponse("Plato", 2)), 1)
    private fun repo(page: (String, String?) -> Response<BuyerOrdersResponse> = { _, _ -> Response.success(BuyerOrdersResponse(1, "ACTIVE", listOf(summary()), null)) },
        detail: (String) -> Response<BuyerOrderDetailEnvelope> = { Response.success(BuyerOrderDetailEnvelope(1, detail())) },
        cancel: (String) -> Response<BuyerOrderCancellationResponse> = { Response.success(BuyerOrderCancellationResponse(1, "CANCELLED", "CANCELLED")) },
        payment: (String) -> Response<SimulatedPaymentResponse> = { Response.success(SimulatedPaymentResponse(1, "PAID", "PAID")) },
        tracking: (String) -> Response<BuyerTrackingResponse> = { error(503) }) =
        HttpBuyerOrdersRepository(object : BuyerOrdersApi {
            override suspend fun tracking(authorization: String, orderId: String): Response<BuyerTrackingResponse> {
                assertEquals("Bearer fixture-token", authorization); return tracking(orderId)
            }
            override suspend fun page(authorization: String, phase: String, cursor: String?, pageSize: Int): Response<BuyerOrdersResponse> {
                assertEquals("Bearer fixture-token", authorization); assertEquals(20, pageSize); return page(phase, cursor)
            }
            override suspend fun detail(authorization: String, orderId: String) = detail(orderId)
            override suspend fun cancel(authorization: String, orderId: String) = cancel(orderId)
            override suspend fun simulatePayment(authorization: String, orderId: String) = payment(orderId)
        }, tokens, sessions)
    private fun <T> error(code: Int): Response<T> = Response.error(code, "{}".toResponseBody())

    @Test fun trackingValidatesIdentityPrivacyAndContract() = runTest {
        val body = BuyerTrackingResponse(1, id, "IN_DELIVERY", "ON_THE_WAY", true, "Ana", null,
            BuyerTrackingPositionResponse(-12.1, -77.1, 10.0, 1_800_000_000_000, 1_800_000_000_000),
            1_800_000_600_000, 1_800_000_000_000)
        val loaded = repo(tracking = { Response.success(body) }).tracking(session, id) as BuyerTrackingResult.Loaded
        assertEquals(-12.1, loaded.tracking.position!!.latitude, 0.0)
        for (invalid in listOf(body.copy(orderId = "d".repeat(64)), body.copy(contractVersion = 2),
            body.copy(status = "READY_FOR_PICKUP", stage = "ACCEPTED"), body.copy(assigned = false),
            body.copy(position = body.position!!.copy(latitude = 91.0))))
            assertEquals(BuyerTrackingResult.Unavailable, repo(tracking = { Response.success(invalid) }).tracking(session, id))
        assertEquals(BuyerTrackingResult.NotFound, repo().tracking(session, "../x"))
        assertEquals(BuyerTrackingResult.NotFound, repo(tracking = { error(404) }).tracking(session, id))
        assertEquals(BuyerTrackingResult.AuthenticationRequired, repo(tracking = { error(401) }).tracking(session, id))
        assertEquals(BuyerTrackingResult.Unavailable, repo(tracking = { error(503) }).tracking(session, id))
        val changed = repo(tracking = { sessions.state.value = CartSession("other", 4); Response.success(body) })
        assertEquals(BuyerTrackingResult.StaleSession, changed.tracking(session, id))
    }

    @Test fun cancellationMapsServerOutcomesAndConflicts() = runTest {
        assertEquals(BuyerOrderCancellationResult.Cancelled, repo().cancel(session, id))
        assertEquals(BuyerOrderCancellationResult.Unavailable, repo(cancel = {
            Response.success(BuyerOrderCancellationResponse(1, "REQUESTED", "PREPARING")) }).cancel(session, id))
        assertEquals(BuyerOrderCancellationResult.NotAllowed, repo(cancel = { error(409) }).cancel(session, id))
        assertEquals(BuyerOrderCancellationResult.Unavailable, repo(cancel = {
            Response.success(BuyerOrderCancellationResponse(1, "CANCELLED", "PREPARING")) }).cancel(session, id))
    }

    @Test fun simulatedPaymentMapsSuccessAndConflicts() = runTest {
        assertEquals(SimulatedPaymentResult.Paid, repo().simulatePayment(session, id))
        assertEquals(SimulatedPaymentResult.NotAllowed,
            repo(payment = { error(409) }).simulatePayment(session, id))
        assertEquals(SimulatedPaymentResult.Unavailable,
            repo(payment = { Response.success(SimulatedPaymentResponse(1, "PAID", "PENDING")) })
                .simulatePayment(session, id))
    }

    @Test fun mapsPagesAndDetailWithExactAmountsAndHistory() = runTest {
        val loaded = repo().page(session, BuyerOrderPhase.ACTIVE) as BuyerOrdersResult.Loaded
        assertEquals(2300L, loaded.page.orders.single().totalMinor)
        assertEquals(listOf(BuyerOrderItemPreview("Plato", 2)), loaded.page.orders.single().itemPreview)
        val d = (repo().detail(session, id) as BuyerOrderDetailResult.Loaded).detail
        assertEquals(listOf("PENDING_CONFIRMATION", "PREPARING"), d.statusHistory.map { it.status }); assertEquals("Calle 2", d.delivery.addressText)
    }

    @Test fun malformedOrMismatchedContractsAreUnavailableNeverPartialData() = runTest {
        val s = summary(); val ok = BuyerOrdersResponse(1, "ACTIVE", listOf(s), null)
        for (body in listOf(ok.copy(contractVersion = 2), ok.copy(phase = "HISTORICAL"), ok.copy(orders = listOf(s.copy(phase = "HISTORICAL"))),
            ok.copy(orders = listOf(s.copy(currency = "USD"))), ok.copy(orders = listOf(s, s)), ok.copy(orders = listOf(s.copy(phase = "OTHER"))),
            ok.copy(orders = listOf(s.copy(itemPreview = emptyList()))), ok.copy(orders = listOf(s.copy(itemLineCount = 3)))))
            assertEquals(BuyerOrdersResult.Unavailable, repo(page = { _, _ -> Response.success(body) }).page(session, BuyerOrderPhase.ACTIVE))
        val d = detail()
        for (body in listOf(d.copy(orderId = "d".repeat(64)), d.copy(totalMinor = 1), d.copy(paymentMethod = "CARD"), d.copy(statusHistory = emptyList()),
            d.copy(itemPreview = listOf(BuyerOrderItemPreviewResponse("Otro", 2))), d.copy(itemLineCount = 3)))
            assertEquals(BuyerOrderDetailResult.Unavailable, repo(detail = { Response.success(BuyerOrderDetailEnvelope(1, body)) }).detail(session, id))
        assertEquals(BuyerOrdersResult.Unavailable, repo(page = { _, c -> Response.success(BuyerOrdersResponse(1, "ACTIVE", listOf(s), c)) })
            .page(session, BuyerOrderPhase.ACTIVE, "same"))
    }

    @Test fun readyForPickupDetailAcceptsThePreparationEstimateSentByFood() = runTest {
        val body = detail().copy(status = "READY_FOR_PICKUP", estimatedPreparationMinutes = 20,
            estimatedReadyAtEpochMillis = 1_800_001_200_000)
        val result = repo(detail = { Response.success(BuyerOrderDetailEnvelope(1, body)) }).detail(session, id)
        assertTrue(result is BuyerOrderDetailResult.Loaded)
        assertEquals(20, (result as BuyerOrderDetailResult.Loaded).detail.estimatedPreparationMinutes)
    }

    @Test fun httpCodesMapToTypedResults() = runTest {
        assertEquals(BuyerOrdersResult.InvalidCursor, repo(page = { _, _ -> error(400) }).page(session, BuyerOrderPhase.ACTIVE, "cursor"))
        assertEquals(BuyerOrdersResult.AuthenticationRequired, repo(page = { _, _ -> error(401) }).page(session, BuyerOrderPhase.ACTIVE))
        assertEquals(BuyerOrdersResult.Unavailable, repo(page = { _, _ -> error(503) }).page(session, BuyerOrderPhase.ACTIVE))
        assertEquals(BuyerOrderDetailResult.NotFound, repo(detail = { error(404) }).detail(session, id))
        assertEquals(BuyerOrderDetailResult.NotFound, repo().detail(session, "../x"))
        tokenError = BuyerAuthenticationRequired()
        assertEquals(BuyerOrdersResult.AuthenticationRequired, repo().page(session, BuyerOrderPhase.ACTIVE))
    }

    @Test fun responseArrivingAfterAccountChangeIsDiscarded() = runTest {
        val repo = repo(page = { _, _ -> sessions.state.value = CartSession("other", 4); Response.success(BuyerOrdersResponse(1, "ACTIVE", listOf(summary()), null)) },
            detail = { sessions.state.value = CartSession("other", 4); Response.success(BuyerOrderDetailEnvelope(1, detail())) })
        assertEquals(BuyerOrdersResult.StaleSession, repo.page(session, BuyerOrderPhase.ACTIVE))
        sessions.state.value = CartSession("buyer", 3)
        assertEquals(BuyerOrderDetailResult.StaleSession, repo.detail(session, id))
        assertEquals(BuyerOrdersResult.StaleSession, repo.page(BuyerOrdersSession("buyer", 2), BuyerOrderPhase.ACTIVE))
    }
}
