package com.chaskifood.app.feature.cart.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class CartWithItems(
    @Embedded val cart: CartEntity,
    @Relation(parentColumn = "ownerUid", entityColumn = "ownerUid")
    val items: List<CartItemEntity>,
)
