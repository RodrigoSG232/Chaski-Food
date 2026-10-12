package com.chaskifood.app.feature.cart.domain

import kotlinx.coroutines.flow.Flow

/** Contrato de HU14; Data resolverá la cuenta activa, la oferta y la persistencia. */
interface CartRepository {
    /** Null representa una cuenta sin sesión; un carrito vacío conserva su propietario. */
    fun observeCart(): Flow<Cart?>

    suspend fun addProduct(storeId: String, productId: String, quantity: Int): CartChangeResult

    suspend fun changeQuantity(productId: String, quantity: Int): CartChangeResult

    suspend fun removeProduct(productId: String): CartChangeResult
}
