package com.chaskifood.app.feature.cart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.chaskifood.app.feature.cart.data.mapper.toDomain

@Dao
abstract class CartDao {
    @Transaction
    @Query("SELECT * FROM carts WHERE ownerUid = :ownerUid")
    abstract fun observeCart(ownerUid: String): Flow<CartWithItems?>

    @Transaction
    @Query("SELECT * FROM carts WHERE ownerUid = :ownerUid")
    abstract suspend fun getCart(ownerUid: String): CartWithItems?

    @Query("SELECT revision FROM carts WHERE ownerUid = :ownerUid")
    abstract fun observeRevision(ownerUid: String): Flow<Long?>

    @Upsert
    protected abstract suspend fun upsertHeader(cart: CartEntity)

    @Query("DELETE FROM cart_items WHERE ownerUid = :ownerUid")
    protected abstract suspend fun deleteItems(ownerUid: String)

    @Insert
    protected abstract suspend fun insertItems(items: List<CartItemEntity>)

    /** Toda escritura lógica conserva e incrementa la revisión del UID dentro de Room. */
    @Transaction
    open suspend fun saveCart(cart: CartEntity, items: List<CartItemEntity>) {
        require(cart.ownerUid.isNotBlank() && items.all { it.ownerUid == cart.ownerUid })
        val incoming = CartWithItems(cart, items).toDomain()
        val stored = getCart(cart.ownerUid)
        val previous = stored?.toDomain() ?: com.chaskifood.app.feature.cart.domain.Cart(cart.ownerUid)
        val revision = stored?.cart?.revision ?: 0L
        require(revision >= 0)
        if (incoming == previous && stored != null) return
        val next = if (incoming != previous) Math.addExact(revision, 1L) else revision
        upsertHeader(cart.copy(revision = next)) // Nunca aceptar una revisión proporcionada por el llamador.
        deleteItems(cart.ownerUid)
        insertItems(items)
    }

    /** Vaciar no elimina la cabecera ni reinicia la historia: protege reconstrucciones ABA. */
    @Transaction
    open suspend fun deleteCart(ownerUid: String) {
        val old = getCart(ownerUid) ?: return
        saveCart(old.cart.copy(businessId = null, storeId = null), emptyList())
    }
}
