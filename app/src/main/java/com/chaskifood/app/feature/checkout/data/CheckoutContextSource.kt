package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.data.local.AddressSelectionStore
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

/** Lecturas locales puntuales; no abre listeners de Firestore durante la comprobación final. */
internal interface CheckoutLocalSnapshotReader {
    suspend fun read(session: CartSession): CheckoutLocalSnapshot
}

internal data class CheckoutLocalSnapshot(val cart: CartState, val selectedAddressId: String?)

internal class StoredCheckoutLocalSnapshotReader @Inject constructor(
    private val carts: CartLocalCoordinator,
    private val selection: AddressSelectionStore,
) : CheckoutLocalSnapshotReader {
    override suspend fun read(session: CartSession): CheckoutLocalSnapshot {
        val uid = session.uid ?: return CheckoutLocalSnapshot(CartState.SignedOut, null)
        val cart = carts.currentCart(session)
        val selectedId = selection.observe(uid).first()
        return CheckoutLocalSnapshot(cart, selectedId)
    }
}

/** Proyecta únicamente la sesión, el carrito y la dirección seleccionada de Checkout. */
internal class CheckoutContextSource @Inject constructor(
    private val sessions: CartSessionSource,
    private val carts: CartRepository,
    private val addresses: AddressRepository,
    private val local: CheckoutLocalSnapshotReader,
) {
    @Volatile private var observed: CheckoutContextState = CheckoutContextState.Loading

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(): Flow<CheckoutContextState> = sessions.sessions.flatMapLatest { session ->
        val uid = session.uid
        if (uid == null) flowOf(CheckoutContextState.SignedOut)
        else combine(carts.observeCart(), addresses.observeAddressBook()) { cart, book ->
            if (!sessions.isCurrent(session)) CheckoutContextState.Loading
            else contextState(session, uid, cart, book)
        }
    }.distinctUntilChanged().onEach { observed = it }

    /** Compara el flujo con Room y DataStore, sin otro listener remoto de Address. */
    suspend fun isCurrent(expected: CheckoutContext): Boolean {
        val ready = CheckoutContextState.Ready(expected)
        if (!isCurrentSession(expected) || observed != ready) return false
        return try {
            val snapshot = local.read(CartSession(expected.ownerUid, expected.sessionGeneration))
            isCurrentSession(expected) && observed == ready &&
                snapshot.cart == CartState.Ready(expected.cart) &&
                snapshot.selectedAddressId == expected.selectedAddress?.id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    fun isCurrentSession(expected: CheckoutContext): Boolean =
        sessions.isCurrent(CartSession(expected.ownerUid, expected.sessionGeneration))

    private fun contextState(
        session: CartSession, uid: String, cart: CartState, book: AddressBookState,
    ): CheckoutContextState {
        if (cart == CartState.SignedOut || book == AddressBookState.SignedOut) return CheckoutContextState.SignedOut
        if (cart == CartState.ReadError || book is AddressBookState.Error) return CheckoutContextState.ReadError
        if (cart !is CartState.Ready || book !is AddressBookState.Ready) return CheckoutContextState.Loading
        if (cart.cart.ownerUid != uid || book.book.ownerUid != uid) return CheckoutContextState.Loading
        val selected = book.book.addresses.singleOrNull { it.id == book.book.selectedAddressId }
        return CheckoutContextState.Ready(CheckoutContext(uid, session.generation, cart.cart, selected))
    }
}
