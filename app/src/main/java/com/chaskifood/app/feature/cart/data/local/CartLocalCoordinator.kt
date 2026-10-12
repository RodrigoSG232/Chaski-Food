@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.cart.data.local

import androidx.room.withTransaction
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.cart.data.mapper.toEntity
import com.chaskifood.app.feature.cart.data.mapper.toItemEntities
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartChangeRejectionReason
import com.chaskifood.app.feature.cart.domain.CartChangeResult
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartPolicy
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PendingCartConfirmation(
    val token: String,
    val session: CartSession,
    val expectedCart: Cart,
    val incomingItem: CartProductSnapshot,
    val address: Address?,
)

/**
 * Coordina todas las escrituras locales. Las operaciones con productos validados solo son
 * internas: el repositorio consulta la oferta antes de invocarlas.
 */
@Singleton
internal class CartLocalCoordinator @Inject constructor(
    private val database: ChaskiDatabase,
    private val dao: CartDao,
    private val sessions: CartSessionSource,
) {
    private val policy = CartPolicy()
    // Protege exclusivamente el token temporal y su invalidación tras una transacción.
    // La serialización persistente de leer/aplicar/guardar corresponde a Room.
    private val pendingMutex = Mutex()
    private var pending: PendingCartConfirmation? = null

    fun observeCart(): Flow<CartState> = sessions.sessions.flatMapLatest { observed ->
        val uid = observed.uid
        if (uid == null) {
            flowOf(CartState.SignedOut)
        } else {
            dao.observeCart(uid).map { stored ->
                if (sessions.isCurrent(observed)) {
                    CartState.Ready(stored?.toDomain() ?: Cart(uid)) as CartState
                } else {
                    null
                }
            }.filterNotNull().onStart {
                emit(CartState.Loading)
            }.catch { error ->
                if (error is CancellationException) throw error
                if (sessions.isCurrent(observed)) emit(CartState.ReadError)
            }
        }
    }.buffer(0)

    /** Lectura puntual para preparar un aumento; la escritura posterior vuelve a leer en Room. */
    suspend fun currentCart(session: CartSession): CartState {
        return try {
            val uid = session.uid ?: return CartState.SignedOut
            if (!sessions.isCurrent(session)) return CartState.SignedOut
            val cart = dao.getCart(uid)?.toDomain() ?: Cart(uid)
            if (sessions.isCurrent(session)) CartState.Ready(cart) else CartState.SignedOut
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CartState.ReadError
        }
    }

    /** Solo recibe referencias comprobadas por la fuente remota. */
    suspend fun addValidatedItem(
        session: CartSession,
        item: CartProductSnapshot,
        address: Address? = null,
    ): CartOperationResult = mutate(session, address) { cart -> fromPolicy(policy.add(cart, item)) }

    /** La cantidad objetivo se calcula con el carrito leído dentro de la transacción. */
    suspend fun setValidatedQuantity(
        session: CartSession,
        productId: String,
        quantity: Int,
        validatedItem: CartProductSnapshot,
    ): CartOperationResult = mutate(session) { cart ->
        val previous = cart.items.firstOrNull { it.productId == productId }
            ?: return@mutate CartOperationResult.RuleRejected(CartChangeRejectionReason.PRODUCT_NOT_IN_CART)
        if (previous.storeId != validatedItem.storeId || previous.businessId != validatedItem.businessId ||
            validatedItem.productId != productId) {
            return@mutate CartOperationResult.ConfirmationStale
        }
        when {
            quantity <= 0 -> CartOperationResult.RuleRejected(CartChangeRejectionReason.INVALID_QUANTITY)
            quantity > previous.quantity -> fromPolicy(policy.add(cart,
                validatedItem.copy(quantity = quantity - previous.quantity)))
            else -> fromPolicy(policy.changeQuantity(cart, productId, quantity))
        }
    }

    /** Reducir no cambia el precio provisional; aumentar requiere la validación de 3B. */
    suspend fun decreaseQuantity(
        productId: String, quantity: Int, session: CartSession = sessions.current(),
    ): CartOperationResult =
        mutate(session) { cart ->
            when {
                quantity <= 0 -> CartOperationResult.RuleRejected(CartChangeRejectionReason.INVALID_QUANTITY)
                cart.items.none { it.productId == productId } ->
                    CartOperationResult.RuleRejected(CartChangeRejectionReason.PRODUCT_NOT_IN_CART)
                quantity > cart.items.single { it.productId == productId }.quantity ->
                    CartOperationResult.RemoteValidationRequired
                else -> fromPolicy(policy.changeQuantity(cart, productId, quantity))
            }
        }

    suspend fun removeProduct(productId: String, session: CartSession = sessions.current()): CartOperationResult =
        mutate(session) { cart -> fromPolicy(policy.remove(cart, productId)) }

    /** El repositorio consulta la oferta después de obtener esta solicitud y antes de confirmar. */
    suspend fun pendingConfirmation(token: String): PendingCartConfirmation? = pendingMutex.withLock {
        val request = pending
        if (request != null && !sessions.isCurrent(request.session)) pending = null
        pending?.takeIf { it.token == token }
    }

    suspend fun cancelConfirmation(token: String): Boolean = pendingMutex.withLock {
        val request = pending
        if (request == null || request.token != token) return@withLock false
        pending = null
        true
    }

    /** Una oferta cambiada exige otro token y conserva el carrito actual intacto. */
    suspend fun renewConfirmation(
        session: CartSession,
        token: String,
        refreshedItem: CartProductSnapshot,
        address: Address?,
    ): CartOperationResult = withStorageResult {
        pendingMutex.withLock {
            val request = pending
            if (request == null || request.token != token) return@withLock CartOperationResult.ConfirmationStale
            if (request.session != session || !sessions.isCurrent(session)) {
                pending = null
                return@withLock CartOperationResult.SessionChanged
            }
            if (request.address != address) {
                pending = null
                return@withLock CartOperationResult.AddressChanged
            }
            val uid = session.uid ?: return@withLock CartOperationResult.SignedOut
            val current = database.withTransaction { dao.getCart(uid)?.toDomain() ?: Cart(uid) }
            if (!sessions.isCurrent(session)) {
                pending = null
                return@withLock CartOperationResult.SessionChanged
            }
            if (current != request.expectedCart) {
                pending = null
                return@withLock CartOperationResult.ConfirmationStale
            }
            val newToken = UUID.randomUUID().toString()
            pending = request.copy(token = newToken, incomingItem = refreshedItem)
            CartOperationResult.ConfirmationRequired(newToken, current, refreshedItem, offerChanged = true)
        }
    }

    /** La nueva referencia debe haber sido revalidada por 3B; un precio distinto exige otro diálogo. */
    suspend fun confirmValidatedItem(
        session: CartSession,
        token: String,
        refreshedItem: CartProductSnapshot,
        address: Address? = null,
    ): CartOperationResult = withStorageResult {
        pendingMutex.withLock {
            val request = pending
            if (request == null || request.token != token) return@withLock CartOperationResult.ConfirmationStale
            if (request.session != session || !sessions.isCurrent(session)) {
                pending = null
                return@withLock CartOperationResult.SessionChanged
            }
            if (request.address != address) {
                pending = null
                return@withLock CartOperationResult.AddressChanged
            }
            if (request.incomingItem != refreshedItem) {
                pending = null
                return@withLock CartOperationResult.ConfirmationStale
            }
            val uid = session.uid ?: return@withLock CartOperationResult.SignedOut
            val result = database.withTransaction {
                if (!sessions.isCurrent(session)) return@withTransaction CartOperationResult.SessionChanged
                val current = dao.getCart(uid)?.toDomain() ?: Cart(uid)
                if (current != request.expectedCart) return@withTransaction CartOperationResult.ConfirmationStale
                val replacement = fromPolicy(policy.add(Cart(uid), refreshedItem))
                if (replacement is CartOperationResult.Updated) {
                    dao.saveCart(replacement.cart.toEntity(), replacement.cart.toItemEntities())
                }
                replacement
            }
            pending = null
            if (sessions.isCurrent(session)) result else CartOperationResult.SessionChanged
        }
    }

    private suspend fun mutate(
        session: CartSession,
        address: Address? = null,
        action: (Cart) -> CartOperationResult,
    ): CartOperationResult = withStorageResult {
        val uid = session.uid ?: return@withStorageResult CartOperationResult.SignedOut
        pendingMutex.withLock {
            if (!sessions.isCurrent(session)) return@withLock CartOperationResult.SessionChanged
            val (result, changed) = database.withTransaction {
                if (!sessions.isCurrent(session)) {
                    return@withTransaction CartOperationResult.SessionChanged to false
                }
                val current = dao.getCart(uid)?.toDomain() ?: Cart(uid)
                val outcome = action(current)
                if (outcome is CartOperationResult.Updated && outcome.cart != current) {
                    check(outcome.cart.ownerUid == uid)
                    dao.saveCart(outcome.cart.toEntity(), outcome.cart.toItemEntities())
                    outcome to true
                } else {
                    outcome to false
                }
            }
            if (changed) pending = null // También detecta A → B → A con igual contenido.
            if (!sessions.isCurrent(session)) {
                pending = null
                return@withLock CartOperationResult.SessionChanged
            }
            if (result is CartOperationResult.ConfirmationRequired) {
                val token = UUID.randomUUID().toString()
                pending = PendingCartConfirmation(token, session, result.currentCart, result.incomingItem, address)
                result.copy(token = token)
            } else {
                result
            }
        }
    }

    private fun fromPolicy(result: CartChangeResult): CartOperationResult = when (result) {
        is CartChangeResult.Updated -> CartOperationResult.Updated(result.cart)
        is CartChangeResult.Rejected -> CartOperationResult.RuleRejected(result.reason)
        is CartChangeResult.RequiresConfirmation -> CartOperationResult.ConfirmationRequired(
            token = "", currentCart = result.currentCart, incomingItem = result.incomingItem,
        )
    }

    private suspend fun withStorageResult(block: suspend () -> CartOperationResult): CartOperationResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CartOperationResult.StorageError
    }
}
