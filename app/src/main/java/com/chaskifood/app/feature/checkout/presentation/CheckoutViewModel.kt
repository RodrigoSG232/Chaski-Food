package com.chaskifood.app.feature.checkout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.checkout.data.CheckoutLineRequest
import com.chaskifood.app.feature.checkout.data.CheckoutQuote
import com.chaskifood.app.feature.checkout.data.CheckoutRemote
import com.chaskifood.app.feature.checkout.data.CheckoutRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface CheckoutState {
    data object Loading : CheckoutState
    data object AddressRequired : CheckoutState
    data class Error(val message: String) : CheckoutState
    data class Ready(val quote: CheckoutQuote, val addressId: String, val cart: Cart) : CheckoutState
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val addresses: AddressRepository,
    private val remote: CheckoutRemote,
) : ViewModel() {
    private val _state = MutableStateFlow<CheckoutState>(CheckoutState.Loading)
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _paymentSelected = MutableStateFlow(false)
    val paymentSelected = _paymentSelected.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var requestId = UUID.randomUUID().toString()
    private var lastKey: Pair<Cart, String>? = null
    private var lastInput: Pair<Cart, String>? = null

    init {
        viewModelScope.launch {
            combine(cartRepository.observeCart(), addresses.observeAddressBook()) { cart, address -> cart to address }
                .collectLatest { (cartState, addressState) ->
                    if (_busy.value) return@collectLatest
                    val cart = (cartState as? CartState.Ready)?.cart
                    val address = (addressState as? AddressBookState.Ready)?.book
                    when {
                        cartState == CartState.Loading || addressState is AddressBookState.Loading ->
                            _state.value = CheckoutState.Loading
                        cart == null || cart.items.isEmpty() -> _state.value = CheckoutState.Error("Tu carrito está vacío.")
                        address == null -> _state.value = CheckoutState.Error("No se pudo consultar tu dirección.")
                        address.selectedAddressId == null -> _state.value = CheckoutState.AddressRequired
                        else -> {
                            val selected = address.addresses.firstOrNull { it.id == address.selectedAddressId }
                            if (selected == null || address.ownerUid != cart.ownerUid) {
                                _state.value = CheckoutState.AddressRequired
                            } else {
                                val key = cart to selected.id
                                if (key != lastKey) { requestId = UUID.randomUUID().toString(); lastKey = key }
                                refresh(cart, selected.id)
                            }
                        }
                    }
                }
        }
    }

    fun selectCash() { _paymentSelected.value = true }
    fun clearMessage() { _message.value = null }

    fun retry() {
        val (cart, addressId) = lastInput ?: return
        viewModelScope.launch { refresh(cart, addressId) }
    }

    private suspend fun refresh(cart: Cart, addressId: String) {
        lastInput = cart to addressId
        _state.value = CheckoutState.Loading
        try {
            _state.value = CheckoutState.Ready(remote.quote(request(cart, addressId)), addressId, cart)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _state.value = CheckoutState.Error(remote.message(error)) }
    }

    fun confirm(onCreated: (String) -> Unit) {
        val ready = _state.value as? CheckoutState.Ready ?: return
        if (!_paymentSelected.value || _busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val request = CheckoutRequest(
                    storeId = ready.quote.storeId, addressId = ready.addressId,
                    paymentMethod = "CASH_ON_DELIVERY", requestId = requestId,
                    items = ready.quote.items.map { CheckoutLineRequest(it.productId, it.quantity, it.unitPriceMinor) },
                )
                val result = remote.create(request)
                ready.cart.items.forEach { cartRepository.removeProduct(it.productId) }
                onCreated(result.orderId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val explanation = remote.message(error)
                if (error is HttpException && error.code() == 409) {
                    refresh(ready.cart, ready.addressId)
                }
                if (_state.value is CheckoutState.Ready) _message.value = explanation
            }
            finally { _busy.value = false }
        }
    }

    private fun request(cart: Cart, addressId: String) = CheckoutRequest(
        storeId = checkNotNull(cart.storeId), addressId = addressId,
        paymentMethod = "CASH_ON_DELIVERY",
        items = cart.items.map { CheckoutLineRequest(it.productId, it.quantity, it.unitPriceMinor) },
    )
}
