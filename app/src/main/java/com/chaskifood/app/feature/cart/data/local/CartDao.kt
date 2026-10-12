package com.chaskifood.app.feature.cart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    @Transaction
    @Query("SELECT * FROM carts WHERE ownerUid = :ownerUid")
    fun observeCart(ownerUid: String): Flow<CartWithItems?>

    @Transaction
    @Query("SELECT * FROM carts WHERE ownerUid = :ownerUid")
    suspend fun getCart(ownerUid: String): CartWithItems?

    @Upsert
    suspend fun upsertHeader(cart: CartEntity)

    @Query("DELETE FROM cart_items WHERE ownerUid = :ownerUid")
    suspend fun deleteItems(ownerUid: String)

    @Insert
    suspend fun insertItems(items: List<CartItemEntity>)

    /** Sustituye únicamente las líneas de este UID o revierte toda la escritura. */
    @Transaction
    suspend fun saveCart(cart: CartEntity, items: List<CartItemEntity>) {
        require(cart.ownerUid.isNotBlank())
        require(items.all { it.ownerUid == cart.ownerUid })
        upsertHeader(cart)
        deleteItems(cart.ownerUid)
        insertItems(items)
    }

    @Query("DELETE FROM carts WHERE ownerUid = :ownerUid")
    suspend fun deleteCart(ownerUid: String)
}
