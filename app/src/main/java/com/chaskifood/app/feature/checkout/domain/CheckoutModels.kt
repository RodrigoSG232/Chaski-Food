package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.cart.domain.CartCurrency

/** Una revisión temporal de la compra; HU16 deberá calcularla otra vez en el servidor. */
data class CheckoutQuote(
    val ownerUid: String,
    val storeId: String,
    val storeName: String,
    val addressId: String,
    val addressLabel: String,
    val addressText: String,
    val lines: List<CheckoutLine>,
    val subtotalMinor: Long,
    val deliveryFeeMinor: Long,
    val totalMinor: Long,
    val currency: CartCurrency = CartCurrency.PEN,
) {
    val hasPriceChanges: Boolean get() = lines.any { it.previousUnitPriceMinor != it.unitPriceMinor }
}

data class CheckoutLine(
    val productId: String,
    val name: String,
    val quantity: Int,
    val previousUnitPriceMinor: Long,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)

data class CheckoutPricedItem(
    val productId: String,
    val name: String,
    val quantity: Int,
    val previousUnitPriceMinor: Long,
    val unitPriceMinor: Long,
)

/** Calcula en céntimos y falla si un importe no se puede representar con seguridad. */
fun buildCheckoutQuote(
    ownerUid: String,
    storeId: String,
    storeName: String,
    addressId: String,
    addressLabel: String,
    addressText: String,
    pricedItems: List<CheckoutPricedItem>,
    deliveryFeeMinor: Long,
): CheckoutQuote {
    require(ownerUid.isNotBlank() && storeId.isNotBlank() && addressId.isNotBlank())
    require(pricedItems.isNotEmpty() && deliveryFeeMinor >= 0)
    require(pricedItems.map { it.productId }.distinct().size == pricedItems.size)
    val lines = pricedItems.map { item ->
        require(item.productId.isNotBlank() && item.quantity > 0 && item.unitPriceMinor > 0)
        CheckoutLine(item.productId, item.name, item.quantity, item.previousUnitPriceMinor,
            item.unitPriceMinor, Math.multiplyExact(item.unitPriceMinor, item.quantity.toLong()))
    }
    val subtotal = lines.fold(0L) { amount, line -> Math.addExact(amount, line.lineTotalMinor) }
    return CheckoutQuote(ownerUid, storeId, storeName, addressId, addressLabel, addressText,
        lines, subtotal, deliveryFeeMinor, Math.addExact(subtotal, deliveryFeeMinor))
}

sealed interface CheckoutReviewResult {
    data class Ready(val quote: CheckoutQuote) : CheckoutReviewResult
    data class Blocked(val reasons: List<String>, val canChangeAddress: Boolean = false) : CheckoutReviewResult
    data class Error(val message: String) : CheckoutReviewResult
    data object SignedOut : CheckoutReviewResult
}

interface CheckoutRepository {
    suspend fun review(): CheckoutReviewResult
}

/** Política inicial explícita, compartible con la futura confirmación del pedido. */
const val DELIVERY_FEE_MINOR = 500L
