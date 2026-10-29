package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.orders.domain.*
import java.util.UUID

/** Referencia aún no persistida en este incremento; no concede autorización permanente. */
data class AcceptedDeliveryQuote(val quoteId: String, val quote: DeliveryQuote) {
    init {
        orderId(quoteId)
        require(quote.quotedAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        require(quote.expiresAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        orderText(quote.tariffVersion)
        orderMinor(quote.deliveryFeeMinor)
    }
}

class AcceptedPurchase(
    val ownerUid: String, val businessId: String, val storeId: String,
    val businessName: String, val storeName: String, val storePhone: String?,
    val buyerName: String, val buyerPhone: String?,
    val pickup: OrderLocationSnapshot, val delivery: OrderDeliverySnapshot,
    items: List<OrderItem>, val amounts: OrderAmounts, val deliveryQuote: AcceptedDeliveryQuote,
    val currency: CartCurrency = CartCurrency.PEN,
    val paymentMethod: OrderPaymentMethod = OrderPaymentMethod.CASH_ON_DELIVERY,
) {
    val items: List<OrderItem> = frozenOrderList(items.sortedBy { it.productId })
    init {
        listOf(ownerUid, businessId, storeId).forEach(::orderId)
        listOf(businessName, storeName, buyerName).forEach { orderText(it) }
        listOfNotNull(storePhone, buyerPhone).forEach { require(it.matches(Regex("[+][1-9][0-9]{7,14}"))) }
        require(this.items.map { it.productId }.distinct().size == this.items.size)
        require(amounts == orderAmounts(this.items, deliveryQuote.quote.deliveryFeeMinor))
        require(deliveryQuote.quote.storeLocation == pickup.location)
        require(deliveryQuote.quote.deliveryLocation == delivery.destination.location)
    }
}

class OrderIntent(val intentId: String, val accepted: AcceptedPurchase) {
    init { require(UUID.fromString(intentId).toString() == intentId) }
    val ownerUid: String get() = accepted.ownerUid
    val requestHash: String get() = OrderFingerprints.request(this)
}

data class OrderReceipt(
    val orderId: String, val intentId: String, val accepted: AcceptedPurchase,
    val createdAtEpochMillis: Long, val status: OrderStatus = OrderStatus.PENDING_CONFIRMATION,
) {
    init {
        orderId(orderId)
        require(UUID.fromString(intentId).toString() == intentId)
        require(createdAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
    }
}

enum class OrderRejection { REVIEW_REQUIRED, QUOTE_EXPIRED, PURCHASE_FORBIDDEN, BUSINESS_UNAVAILABLE,
    STORE_UNAVAILABLE, NO_RESPONSIBLE, INVALID_LOCATION, OUTSIDE_COVERAGE }
enum class OrderUncertainty { NOT_OBSERVED, TIMEOUT, CONNECTIVITY, SERVER_UNAVAILABLE }

/** Estado del intento, distinto del estado comercial del pedido. */
sealed interface OrderAttemptResult {
    data class Succeeded(val receipt: OrderReceipt) : OrderAttemptResult
    data class RejectedFinal(val reason: OrderRejection) : OrderAttemptResult
    data class Unknown(val reason: OrderUncertainty) : OrderAttemptResult
}

class RecordedOrderAttempt(
    val ownerUid: String, val intentId: String, val requestHash: String, val result: OrderAttemptResult,
) {
    init {
        orderId(ownerUid)
        require(UUID.fromString(intentId).toString() == intentId)
        require(requestHash.matches(Regex("[0-9a-f]{64}")))
        require(result !is OrderAttemptResult.Unknown) // Ausencia/error no es un resultado terminal durable.
        if (result is OrderAttemptResult.Succeeded) {
            require(result.receipt.accepted.ownerUid == ownerUid && result.receipt.intentId == intentId)
            require(OrderIntent(intentId, result.receipt.accepted).requestHash == requestHash)
        }
    }
}

sealed interface IntentResolution {
    data object NewIntent : IntentResolution
    data object Conflict : IntentResolution
    data class Replay(val result: OrderAttemptResult) : IntentResolution
}

/** Resolver un éxito previo no vuelve a evaluar caducidad/precios/permisos de una compra nueva. */
fun resolveOrderIntent(intent: OrderIntent, recorded: RecordedOrderAttempt?): IntentResolution = when {
    recorded == null -> IntentResolution.NewIntent // No afirma que una solicitud anterior haya fallado.
    recorded.ownerUid != intent.ownerUid || recorded.intentId != intent.intentId ||
        recorded.requestHash != intent.requestHash -> IntentResolution.Conflict
    else -> IntentResolution.Replay(recorded.result)
}

data class ReviewComparison(val requiresAcceptance: Boolean, val requiresNewRoute: Boolean)
fun compareOrderReviews(accepted: AcceptedPurchase, current: AcceptedPurchase): ReviewComparison =
    ReviewComparison(OrderFingerprints.consent(accepted) != OrderFingerprints.consent(current),
        OrderFingerprints.logistics(accepted) != OrderFingerprints.logistics(current))

/** Ausencia en una lectura no demuestra que otro POST no pueda terminar después. */
fun recoverOrderAttempt(ownerUid: String, intentId: String, recorded: RecordedOrderAttempt?): OrderAttemptResult {
    orderId(ownerUid)
    require(UUID.fromString(intentId).toString() == intentId)
    if (recorded == null) return OrderAttemptResult.Unknown(OrderUncertainty.NOT_OBSERVED)
    require(recorded.ownerUid == ownerUid && recorded.intentId == intentId)
    return recorded.result
}
