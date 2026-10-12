package com.chaskifood.app.feature.cart.data

import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.domain.AddToCartValidationResult
import com.chaskifood.app.feature.cart.domain.CartChangeRejectionReason
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.cart.domain.ValidateProductForCart
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Firebase se consulta antes de entrar en las transacciones Room del coordinador. */
internal class DefaultCartRepository @Inject constructor(
    private val sessions: CartSessionSource,
    private val addresses: AddressRepository,
    private val offers: ProductOfferReader,
    private val local: CartLocalCoordinator,
) : CartRepository {
    private val validate = ValidateProductForCart()

    override fun observeCart(): Flow<CartState> = local.observeCart()

    override suspend fun addProduct(storeId: String, productId: String, quantity: Int): CartOperationResult {
        if (quantity <= 0) return CartOperationResult.RuleRejected(CartChangeRejectionReason.INVALID_QUANTITY)
        val session = sessions.current()
        if (session.uid == null) return CartOperationResult.SignedOut
        return guarded {
            val address = when (val selected = selectedAddress(session)) {
                is AddressLookup.Ready -> selected.address
                is AddressLookup.Failed -> return@guarded selected.result
            }
            val item = when (val validated = validatedItem(storeId, productId, quantity, address)) {
                is OfferValidation.Ready -> validated.item
                is OfferValidation.Failed -> return@guarded validated.result
            }
            currentAddressResult(session, address)?.let { return@guarded it }
            local.addValidatedItem(session, item, address)
        }
    }

    override suspend fun changeQuantity(productId: String, quantity: Int): CartOperationResult {
        val session = sessions.current()
        if (session.uid == null) return CartOperationResult.SignedOut
        val localResult = local.decreaseQuantity(productId, quantity, session)
        if (localResult != CartOperationResult.RemoteValidationRequired) return localResult
        return guarded {
            val state = local.currentCart(session)
            if (!sessions.isCurrent(session)) return@guarded CartOperationResult.SessionChanged
            if (state == CartState.ReadError) return@guarded CartOperationResult.StorageError
            val item = (state as? CartState.Ready)?.cart?.items?.firstOrNull { it.productId == productId }
                ?: return@guarded CartOperationResult.RuleRejected(CartChangeRejectionReason.PRODUCT_NOT_IN_CART)
            val address = when (val selected = selectedAddress(session)) {
                is AddressLookup.Ready -> selected.address
                is AddressLookup.Failed -> return@guarded selected.result
            }
            val refreshed = when (val validated = validatedItem(item.storeId, productId, 1, address)) {
                is OfferValidation.Ready -> validated.item
                is OfferValidation.Failed -> return@guarded validated.result
            }
            currentAddressResult(session, address)?.let { return@guarded it }
            local.setValidatedQuantity(session, productId, quantity, refreshed)
        }
    }

    override suspend fun removeProduct(productId: String): CartOperationResult {
        val session = sessions.current()
        return if (session.uid == null) CartOperationResult.SignedOut
        else local.removeProduct(productId, session)
    }

    override suspend fun confirmStoreChange(token: String): CartOperationResult = guarded {
        val request = local.pendingConfirmation(token) ?: return@guarded CartOperationResult.ConfirmationStale
        val session = request.session
        if (!sessions.isCurrent(session)) return@guarded CartOperationResult.SessionChanged
        val address = when (val selected = selectedAddress(session)) {
            is AddressLookup.Ready -> selected.address
            is AddressLookup.Failed -> return@guarded selected.result
        }
        if (address != request.address) {
            local.cancelConfirmation(token)
            return@guarded CartOperationResult.AddressChanged
        }
        val incoming = request.incomingItem
        val refreshed = when (val validated = validatedItem(incoming.storeId, incoming.productId,
                incoming.quantity, address)) {
            is OfferValidation.Ready -> validated.item
            is OfferValidation.Failed -> return@guarded validated.result
        }
        currentAddressResult(session, address)?.let { return@guarded it }
        if (refreshed != incoming) {
            local.renewConfirmation(session, token, refreshed, address)
        } else {
            local.confirmValidatedItem(session, token, refreshed, address)
        }
    }

    override suspend fun cancelStoreChange(token: String): Boolean = local.cancelConfirmation(token)

    private suspend fun validatedItem(
        storeId: String, productId: String, quantity: Int, address: Address,
    ): OfferValidation = when (val offer = offers.read(storeId, productId, address.location)) {
        is ProductOfferReadResult.Found -> when (val result =
            validate(storeId, offer.product, offer.configuration, quantity)) {
            is AddToCartValidationResult.Allowed -> OfferValidation.Ready(result.item)
            is AddToCartValidationResult.Rejected -> OfferValidation.Failed(
                CartOperationResult.OfferRejected(result.reason))
        }
        ProductOfferReadResult.ProductNotFound -> OfferValidation.Failed(CartOperationResult.OfferNotFound)
        ProductOfferReadResult.Unavailable -> OfferValidation.Failed(CartOperationResult.OfferUnavailable)
        ProductOfferReadResult.PermissionDenied -> OfferValidation.Failed(CartOperationResult.OfferPermissionDenied)
        ProductOfferReadResult.NetworkError -> OfferValidation.Failed(CartOperationResult.OfferNetworkError)
        ProductOfferReadResult.Unverified -> OfferValidation.Failed(CartOperationResult.OfferUnverified)
        ProductOfferReadResult.InvalidData -> OfferValidation.Failed(CartOperationResult.OfferInvalidData)
    }

    private suspend fun selectedAddress(session: CartSession): AddressLookup {
        if (!sessions.isCurrent(session)) return AddressLookup.Failed(CartOperationResult.SessionChanged)
        val state = addresses.observeAddressBook().first { it !is AddressBookState.Loading }
        if (!sessions.isCurrent(session)) return AddressLookup.Failed(CartOperationResult.SessionChanged)
        return when (state) {
            AddressBookState.SignedOut -> AddressLookup.Failed(CartOperationResult.SessionChanged)
            is AddressBookState.Error -> AddressLookup.Failed(CartOperationResult.AddressUnavailable)
            is AddressBookState.Ready -> {
                if (state.book.ownerUid != session.uid) AddressLookup.Failed(CartOperationResult.SessionChanged)
                else state.book.addresses.singleOrNull { it.id == state.book.selectedAddressId }
                    ?.let(AddressLookup::Ready)
                    ?: AddressLookup.Failed(CartOperationResult.AddressRequired)
            }
            is AddressBookState.Loading -> error("El estado de carga ya fue filtrado.")
        }
    }

    private suspend fun currentAddressResult(session: CartSession, expected: Address): CartOperationResult? {
        val selected = selectedAddress(session)
        return when (selected) {
            is AddressLookup.Ready -> if (selected.address == expected) null else CartOperationResult.AddressChanged
            is AddressLookup.Failed -> if (selected.result == CartOperationResult.SessionChanged) selected.result
                else CartOperationResult.AddressChanged
        }
    }

    private suspend fun guarded(block: suspend () -> CartOperationResult): CartOperationResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CartOperationResult.StorageError
    }

    private sealed interface AddressLookup {
        data class Ready(val address: Address) : AddressLookup
        data class Failed(val result: CartOperationResult) : AddressLookup
    }

    private sealed interface OfferValidation {
        data class Ready(val item: CartProductSnapshot) : OfferValidation
        data class Failed(val result: CartOperationResult) : OfferValidation
    }
}
