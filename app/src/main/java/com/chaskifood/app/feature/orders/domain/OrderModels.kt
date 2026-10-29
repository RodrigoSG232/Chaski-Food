package com.chaskifood.app.feature.orders.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import java.util.Collections

/** Límite de representación JSON interoperable, no límite comercial. */
const val MAX_ORDER_MINOR: Long = 9_007_199_254_740_991L

/** Rango temporal compatible con Timestamp Firestore (hasta 9999-12-31). */
const val MAX_ORDER_EPOCH_MILLIS: Long = 253_402_300_799_999L

enum class OrderPaymentMethod { CASH_ON_DELIVERY }
enum class OrderStatus { PENDING_CONFIRMATION }

fun orderMinor(value: Long): Long = value.also { require(it in 0..MAX_ORDER_MINOR) }

internal fun orderText(value: String, optional: Boolean = false) {
    require(optional || value.isNotBlank())
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        if (c.isHighSurrogate()) require(i < value.length && value[i++].isLowSurrogate())
        else require(!c.isLowSurrogate())
    }
}

internal fun orderId(value: String) {
    orderText(value)
    require(value != "." && value != ".." && '/' !in value)
}

data class OrderItem(
    val productId: String, val categoryId: String, val name: String,
    val quantity: Int, val unitPriceMinor: Long,
) {
    val lineTotalMinor: Long
    init {
        orderId(productId); orderId(categoryId); orderText(name)
        require(quantity > 0 && orderMinor(unitPriceMinor) > 0)
        lineTotalMinor = orderMinor(Math.multiplyExact(unitPriceMinor, quantity.toLong()))
    }
}

data class OrderAmounts(val subtotalMinor: Long, val deliveryFeeMinor: Long, val totalMinor: Long) {
    init {
        orderMinor(subtotalMinor); orderMinor(deliveryFeeMinor); orderMinor(totalMinor)
        require(totalMinor == Math.addExact(subtotalMinor, deliveryFeeMinor))
    }
}

fun orderAmounts(items: List<OrderItem>, deliveryFeeMinor: Long): OrderAmounts {
    require(items.isNotEmpty())
    val subtotal = items.fold(0L) { sum, item -> orderMinor(Math.addExact(sum, item.lineTotalMinor)) }
    return OrderAmounts(subtotal, deliveryFeeMinor, orderMinor(Math.addExact(subtotal, deliveryFeeMinor)))
}

data class OrderLocationSnapshot(
    val addressText: String, val location: AddressCoordinates,
    val reference: String? = null, val instructions: String? = null,
) {
    init {
        orderText(addressText); require(location.isValid)
        reference?.let { orderText(it, optional = true) }
        instructions?.let { orderText(it, optional = true) }
    }
}

data class OrderDeliverySnapshot(val addressId: String, val label: String, val destination: OrderLocationSnapshot) {
    init { orderId(addressId); orderText(label) }
}

/** Listas defensivas como Cart: el estado aceptado no cambia por referencias externas. */
internal fun <T> frozenOrderList(items: List<T>): List<T> = Collections.unmodifiableList(ArrayList(items))
