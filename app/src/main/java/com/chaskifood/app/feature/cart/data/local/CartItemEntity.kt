package com.chaskifood.app.feature.cart.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Datos provisionales de una línea; no representan una reserva de precio o stock. */
@Entity(
    tableName = "cart_items",
    primaryKeys = ["ownerUid", "productId"],
    foreignKeys = [
        ForeignKey(
            entity = CartEntity::class,
            parentColumns = ["ownerUid"],
            childColumns = ["ownerUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ownerUid", "position"], unique = true)],
)
data class CartItemEntity(
    val ownerUid: String,
    val productId: String,
    val productName: String,
    val unitPriceMinor: Long,
    val quantity: Int,
    val position: Int,
)
