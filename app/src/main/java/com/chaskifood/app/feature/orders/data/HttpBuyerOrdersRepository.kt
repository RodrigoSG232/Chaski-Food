package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.checkout.data.*
import com.chaskifood.app.feature.orders.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** HU17: compras propias vía foodApi. El UID sale de la sesión Firebase; ninguna respuesta se publica si la sesión cambió. */
internal class HttpBuyerOrdersRepository @Inject constructor(private val api: BuyerOrdersApi, private val tokens: BuyerTokenSource,
    private val sessions: CartSessionSource) : BuyerOrdersRepository {
    override fun observeSession(): Flow<BuyerOrdersSession?> =
        sessions.sessions.map { s -> s.uid?.let { BuyerOrdersSession(it, s.generation) } }.distinctUntilChanged()
    override fun isCurrent(session: BuyerOrdersSession) =
        sessions.isCurrent(CartSession(session.uid, session.generation)) && tokens.isCurrent(session.uid, session.generation)

    override suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?): BuyerOrdersResult =
        guarded(BuyerOrdersResult.StaleSession, BuyerOrdersResult.AuthenticationRequired, BuyerOrdersResult.Unavailable, session) { bearer ->
            val response = api.page(bearer, phase.name, cursor)
            if (!isCurrent(session)) return@guarded BuyerOrdersResult.StaleSession
            when (response.code()) {
                200 -> {
                    val body = requireNotNull(response.body())
                    require(body.contractVersion == 1 && body.phase == phase.name && body.orders.size <= 20)
                    require(cursor == null || body.nextCursor != cursor)
                    BuyerOrdersResult.Loaded(BuyerOrdersPage(phase, body.orders.map { it.toDomain() }, body.nextCursor))
                }
                401 -> BuyerOrdersResult.AuthenticationRequired
                400 -> if (cursor != null) BuyerOrdersResult.InvalidCursor else BuyerOrdersResult.Unavailable
                else -> BuyerOrdersResult.Unavailable
            }
        }

    override suspend fun detail(session: BuyerOrdersSession, orderId: String): BuyerOrderDetailResult =
        guarded(BuyerOrderDetailResult.StaleSession, BuyerOrderDetailResult.AuthenticationRequired, BuyerOrderDetailResult.Unavailable, session) { bearer ->
            if (!orderId.matches(Regex("[0-9a-f]{64}"))) return@guarded BuyerOrderDetailResult.NotFound
            val response = api.detail(bearer, orderId)
            if (!isCurrent(session)) return@guarded BuyerOrderDetailResult.StaleSession
            when (response.code()) {
                200 -> {
                    val body = requireNotNull(response.body())
                    require(body.contractVersion == 1 && body.order.orderId == orderId)
                    BuyerOrderDetailResult.Loaded(body.order.toDomain())
                }
                404 -> BuyerOrderDetailResult.NotFound
                401 -> BuyerOrderDetailResult.AuthenticationRequired
                else -> BuyerOrderDetailResult.Unavailable
            }
        }

    /** Token por sesión, timeout y traducción de fallos; un contrato inesperado nunca se presenta como dato válido. */
    private suspend fun <T> guarded(stale: T, auth: T, unavailable: T, session: BuyerOrdersSession, call: suspend (String) -> T): T {
        if (!isCurrent(session)) return stale
        return try {
            withTimeoutOrNull(15_000) {
                val token = tokens.token(session.uid, session.generation)
                if (!isCurrent(session)) stale else call("Bearer $token")
            } ?: unavailable
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: BuyerSessionChanged) { stale }
        catch (_: BuyerAuthenticationRequired) { auth }
        catch (_: Exception) { unavailable }
    }
}

private fun phaseOf(value: String) = BuyerOrderPhase.valueOf(value)

private fun BuyerOrderSummaryResponse.toDomain(): BuyerOrderSummary {
    require(currency == "PEN")
    return BuyerOrderSummary(orderId, createdAtEpochMillis, status, statusKnown, phaseOf(phase), storeName, businessName, itemCount, totalMinor,
        itemPreview.map { BuyerOrderItemPreview(it.name, it.quantity) }, itemLineCount)
}

private fun BuyerOrderDetailResponse.toDomain(): BuyerOrderDetail {
    require(currency == "PEN" && paymentMethod == "CASH_ON_DELIVERY")
    val summary = BuyerOrderSummary(orderId, createdAtEpochMillis, status, statusKnown, phaseOf(phase), storeName, businessName, itemCount, totalMinor,
        itemPreview.map { BuyerOrderItemPreview(it.name, it.quantity) }, itemLineCount)
    return BuyerOrderDetail(summary, storePhone, pickupAddressText,
        BuyerOrderDelivery(delivery.label, delivery.addressText, delivery.reference, delivery.instructions),
        items.map { StoreOrderLine(it.productId, it.name, it.quantity, it.unitPriceMinor, it.lineTotalMinor) },
        OrderAmounts(subtotalMinor, deliveryFeeMinor, totalMinor), OrderPaymentMethod.CASH_ON_DELIVERY,
        statusHistory.map { BuyerOrderStatusEntry(it.status, it.statusKnown, it.occurredAtEpochMillis) })
}
