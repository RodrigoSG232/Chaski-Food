package com.chaskifood.app.feature.cart.presentation

import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartState

/** El contenido observado es la fuente de productos, cantidades e importes en céntimos. */
data class CartUiState(
    val source: CartState = CartState.Loading,
    val operation: CartUiOperation? = null,
    val confirmation: CartOperationResult.ConfirmationRequired? = null,
    val message: CartUiMessage? = null,
) {
    val cart: Cart? get() = (source as? CartState.Ready)?.cart
    val isEmpty: Boolean get() = cart?.items?.isEmpty() == true
    val isBusy: Boolean get() = operation != null
}

sealed interface CartUiOperation {
    data class Add(val productId: String) : CartUiOperation
    data class ChangeQuantity(val productId: String) : CartUiOperation
    data class Remove(val productId: String) : CartUiOperation
    data object Confirm : CartUiOperation
    data object CancelConfirmation : CartUiOperation
}

/** Aviso persistente hasta descartarlo; el ID evita descartar un aviso posterior por error. */
data class CartUiMessage(val id: Long, val text: String)
