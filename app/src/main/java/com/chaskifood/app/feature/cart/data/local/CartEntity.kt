package com.chaskifood.app.feature.cart.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** La cabecera conserva el propietario incluso cuando el carrito está vacío. */
@Entity(tableName = "carts")
data class CartEntity(
    @PrimaryKey val ownerUid: String,
    val businessId: String?,
    val storeId: String?,
    val currencyCode: String,
)
