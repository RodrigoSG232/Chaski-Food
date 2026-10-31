package com.chaskifood.app.feature.orders.domain

import kotlinx.coroutines.flow.Flow

/** HU17: activos e históricos los clasifica foodApi; la app solo muestra la fase recibida. */
enum class BuyerOrderPhase { ACTIVE, HISTORICAL }

/** Sesión del comprador que hace la consulta; una generación nueva invalida respuestas anteriores aunque el UID se repita. */
data class BuyerOrdersSession(val uid: String, val generation: Long) {
    init { require(uid.isNotBlank() && generation >= 0) }
}

/** Línea mostrada en la fila de la lista, copiada del snapshot histórico del pedido. */
data class BuyerOrderItemPreview(val name: String, val quantity: Int) {
    init { orderText(name); require(quantity > 0) }
}

/** `statusKnown = false`: estado válido que esta versión no reconoce (se muestra como «Estado en actualización»). */
data class BuyerOrderSummary(
    val orderId: String, val createdAtEpochMillis: Long, val status: String, val statusKnown: Boolean,
    val phase: BuyerOrderPhase, val storeName: String, val businessName: String, val itemCount: Int, val totalMinor: Long,
    /** Hasta [MAX_ITEM_PREVIEW] líneas; el resto se muestra como «+N más». */
    val itemPreview: List<BuyerOrderItemPreview>, val itemLineCount: Int,
) {
    val hiddenLineCount: Int get() = itemLineCount - itemPreview.size
    init {
        orderId(orderId); orderText(status); orderText(storeName); orderText(businessName)
        require(createdAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS && itemCount > 0)
        require(itemLineCount in 1..itemCount && this.itemPreview.size == minOf(itemLineCount, MAX_ITEM_PREVIEW) &&
            this.itemPreview.sumOf { it.quantity.toLong() } <= itemCount)
        orderMinor(totalMinor)
    }
    companion object { const val MAX_ITEM_PREVIEW = 2 }
}

data class BuyerOrderDelivery(val label: String?, val addressText: String, val reference: String?, val instructions: String?) {
    init { orderText(addressText); label?.let { orderText(it) }; reference?.let { orderText(it) }; instructions?.let { orderText(it) } }
}

data class BuyerOrderStatusEntry(val status: String, val statusKnown: Boolean, val occurredAtEpochMillis: Long) {
    init { orderText(status); require(occurredAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS) }
}

/** Copia histórica del pedido (snapshots de HU16): no cambia aunque se editen la dirección o el catálogo actuales. */
class BuyerOrderDetail(
    val summary: BuyerOrderSummary, val storePhone: String?, val pickupAddressText: String, val delivery: BuyerOrderDelivery,
    items: List<StoreOrderLine>, val amounts: OrderAmounts, val paymentMethod: OrderPaymentMethod, statusHistory: List<BuyerOrderStatusEntry>,
) {
    val items: List<StoreOrderLine> = frozenOrderList(items)
    val statusHistory: List<BuyerOrderStatusEntry> = frozenOrderList(statusHistory)
    init {
        orderText(pickupAddressText); storePhone?.let { orderText(it) }
        require(this.items.isNotEmpty() && this.items.map { it.productId }.distinct().size == this.items.size)
        require(this.items.fold(0L) { sum, i -> orderMinor(Math.addExact(sum, i.lineTotalMinor)) } == amounts.subtotalMinor)
        require(amounts.totalMinor == summary.totalMinor && this.items.sumOf { it.quantity } == summary.itemCount)
        require(summary.itemLineCount == this.items.size && summary.itemPreview ==
            this.items.take(BuyerOrderSummary.MAX_ITEM_PREVIEW).map { BuyerOrderItemPreview(it.name, it.quantity) })
        require(this.statusHistory.isNotEmpty() && this.statusHistory.zipWithNext().all { (a, b) -> a.occurredAtEpochMillis <= b.occurredAtEpochMillis })
    }
}

/** Una página puede venir vacía con cursor: el servidor examinó su presupuesto sin encontrar pedidos de esa fase. */
class BuyerOrdersPage(val phase: BuyerOrderPhase, orders: List<BuyerOrderSummary>, val nextCursor: String?) {
    val orders: List<BuyerOrderSummary> = frozenOrderList(orders)
    init {
        require(this.orders.all { it.phase == phase } && this.orders.map { it.orderId }.distinct().size == this.orders.size)
        require(nextCursor == null || (nextCursor.length <= 1024 && nextCursor.matches(Regex("[A-Za-z0-9_-]+"))))
    }
}

sealed interface BuyerOrdersResult {
    data class Loaded(val page: BuyerOrdersPage) : BuyerOrdersResult
    data object AuthenticationRequired : BuyerOrdersResult
    data object StaleSession : BuyerOrdersResult
    data object InvalidCursor : BuyerOrdersResult
    data object Unavailable : BuyerOrdersResult
}

sealed interface BuyerOrderDetailResult {
    data class Loaded(val detail: BuyerOrderDetail) : BuyerOrderDetailResult
    data object NotFound : BuyerOrderDetailResult
    data object AuthenticationRequired : BuyerOrderDetailResult
    data object StaleSession : BuyerOrderDetailResult
    data object Unavailable : BuyerOrderDetailResult
}

interface BuyerOrdersRepository {
    /** Sesión Firebase vigente (null sin sesión); cambia también en un nuevo inicio con el mismo UID. */
    fun observeSession(): Flow<BuyerOrdersSession?>
    fun isCurrent(session: BuyerOrdersSession): Boolean
    suspend fun page(session: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String? = null): BuyerOrdersResult
    suspend fun detail(session: BuyerOrdersSession, orderId: String): BuyerOrderDetailResult
}
