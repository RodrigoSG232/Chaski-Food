package com.chaskifood.app.feature.checkout.domain

/** Resultado de la coordinación, independiente de Room y del transporte. */
sealed interface CheckoutConfirmation {
    data object None : CheckoutConfirmation
    data object Busy : CheckoutConfirmation
    data object Working : CheckoutConfirmation
    data class Pending(val intentId: String, val reason: OrderUncertainty = OrderUncertainty.NOT_OBSERVED) : CheckoutConfirmation
    data class Success(val receipt: OrderReceipt, val cartConsumed: Boolean) : CheckoutConfirmation
    data class Rejected(val intentId: String, val reason: OrderRejection) : CheckoutConfirmation
    data class Conflict(val intentId: String? = null) : CheckoutConfirmation
    data object ReviewRequired : CheckoutConfirmation
    data object SessionChanged : CheckoutConfirmation
    data object PurchaseForbidden : CheckoutConfirmation
    data object StorageError : CheckoutConfirmation
}
