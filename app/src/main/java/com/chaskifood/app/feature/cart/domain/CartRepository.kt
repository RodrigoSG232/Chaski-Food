package com.chaskifood.app.feature.cart.domain

import kotlinx.coroutines.flow.Flow

/** Estado observable del carrito de la sesión activa. */
sealed interface CartState {
    data object Loading : CartState
    data object SignedOut : CartState
    data class Ready(val cart: Cart) : CartState
    data object ReadError : CartState
}

/** Resultados de coordinación; las reglas puras conservan CartChangeResult. */
sealed interface CartOperationResult {
    data class Updated(val cart: Cart) : CartOperationResult
    data class ConfirmationRequired(
        val token: String,
        val currentCart: Cart,
        val incomingItem: CartProductSnapshot,
    ) : CartOperationResult
    data class RuleRejected(val reason: CartChangeRejectionReason) : CartOperationResult
    data class OfferRejected(val reason: AddToCartRejectionReason) : CartOperationResult
    data object OfferUnavailable : CartOperationResult
    data object RemoteValidationRequired : CartOperationResult
    data object SignedOut : CartOperationResult
    data object SessionChanged : CartOperationResult
    data object ConfirmationStale : CartOperationResult
    data object StorageError : CartOperationResult
}

/** 3B conectará este contrato con la oferta remota y el coordinador local. */
interface CartRepository {
    fun observeCart(): Flow<CartState>

    suspend fun addProduct(storeId: String, productId: String, quantity: Int): CartOperationResult

    suspend fun changeQuantity(productId: String, quantity: Int): CartOperationResult

    suspend fun removeProduct(productId: String): CartOperationResult
    suspend fun confirmStoreChange(token: String): CartOperationResult
    suspend fun cancelStoreChange(token: String): Boolean
}
