package com.chaskifood.app.feature.cart.presentation

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.cart.domain.AddToCartRejectionReason
import com.chaskifood.app.feature.cart.domain.CartChangeRejectionReason
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private var observation: Job? = null
    private var operationJob: Job? = null
    private var contextVersion = 0L
    private var nextMessageId = 0L

    init {
        observeCart()
    }

    /** Una colección por ViewModel; se cancela al liberar su ViewModelStore. */
    private fun observeCart() {
        observation?.cancel()
        receive(CartState.Loading)
        observation = viewModelScope.launch {
            try {
                repository.observeCart().collect(::receive)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                receive(CartState.ReadError)
            }
        }
    }

    private fun receive(source: CartState) {
        val current = _uiState.value
        val newUid = (source as? CartState.Ready)?.cart?.ownerUid
        // Loading también separa sesiones nuevas con el mismo UID (A → logout → A).
        if (source !is CartState.Ready || current.cart?.ownerUid != newUid) {
            contextVersion++
            operationJob?.cancel()
            operationJob = null
            _uiState.value = CartUiState(source = source)
        } else {
            val pending = current.confirmation
            val stale = pending != null && pending.currentCart != source.cart
            _uiState.value = current.copy(
                source = source,
                confirmation = pending.takeUnless { stale },
                message = if (stale && !current.isBusy) message(
                    "El carrito cambió. Vuelve a agregar el producto para confirmar el cambio de local.",
                ) else current.message,
            )
        }
    }

    @MainThread
    fun retryLoading() {
        if (_uiState.value.source == CartState.ReadError) observeCart()
    }

    @MainThread
    fun addProduct(storeId: String, productId: String, quantity: Int = 1) {
        if (_uiState.value.confirmation != null) return
        perform(CartUiOperation.Add(productId)) { repository.addProduct(storeId, productId, quantity) }
    }

    /** Recibe una cantidad objetivo; Domain decide si es válida y si requiere lectura remota. */
    @MainThread
    fun changeQuantity(productId: String, quantity: Int) {
        if (_uiState.value.confirmation != null) return
        perform(CartUiOperation.ChangeQuantity(productId)) { repository.changeQuantity(productId, quantity) }
    }

    @MainThread
    fun removeProduct(productId: String) {
        if (_uiState.value.confirmation != null) return
        perform(CartUiOperation.Remove(productId)) { repository.removeProduct(productId) }
    }

    @MainThread
    fun confirmStoreChange(token: String) {
        if (_uiState.value.confirmation?.token != token) return
        perform(CartUiOperation.Confirm) { repository.confirmStoreChange(token) }
    }

    @MainThread
    fun cancelStoreChange(token: String) {
        if (_uiState.value.confirmation?.token != token) return
        perform(CartUiOperation.CancelConfirmation) {
            repository.cancelStoreChange(token)
            // Un token ya invalidado tampoco debe mantener abierto el diálogo.
            null
        }
    }

    @MainThread
    fun dismissMessage(id: Long) {
        if (_uiState.value.message?.id == id) _uiState.value = _uiState.value.copy(message = null)
    }

    private fun perform(operation: CartUiOperation, block: suspend () -> CartOperationResult?) {
        val current = _uiState.value
        if (current.isBusy || current.source !is CartState.Ready) return
        val version = contextVersion
        val uid = current.cart?.ownerUid
        // Se reserva antes del launch: dos pulsaciones en el mismo turno no duplican la llamada.
        _uiState.value = current.copy(operation = operation, message = null)
        operationJob = viewModelScope.launch {
            try {
                val result = block()
                if (version == contextVersion && _uiState.value.cart?.ownerUid == uid) {
                    if (result == null) _uiState.value = _uiState.value.copy(confirmation = null)
                    else receiveResult(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == contextVersion) {
                    _uiState.value = _uiState.value.copy(message = message(
                        "No se pudo completar la operación. Revisa el carrito antes de intentarlo otra vez.",
                    ))
                }
            } finally {
                if (version == contextVersion) {
                    _uiState.value = _uiState.value.copy(operation = null)
                    operationJob = null
                }
            }
        }
    }

    private fun receiveResult(result: CartOperationResult) {
        when (result) {
            is CartOperationResult.Updated -> {
                // No sustituir el flujo por una respuesta que podría ser anterior a otra escritura.
                val added = _uiState.value.operation == CartUiOperation.Confirm ||
                    _uiState.value.operation is CartUiOperation.Add
                _uiState.value = _uiState.value.copy(
                    confirmation = null,
                    message = if (added) message("Producto agregado al carrito.", CartUiMessageKind.Success) else null,
                )
            }
            is CartOperationResult.ConfirmationRequired -> {
                _uiState.value = if (result.currentCart == _uiState.value.cart) {
                    _uiState.value.copy(confirmation = result, message = null)
                } else {
                    _uiState.value.copy(confirmation = null, message = message(
                        "El carrito cambió. Vuelve a agregar el producto para confirmar el cambio de local.",
                    ))
                }
            }
            CartOperationResult.SessionChanged -> observeCart()
            CartOperationResult.SignedOut -> receive(CartState.SignedOut)
            else -> {
                val invalidatesConfirmation = result == CartOperationResult.ConfirmationStale ||
                    result == CartOperationResult.AddressChanged
                _uiState.value = _uiState.value.copy(
                    confirmation = _uiState.value.confirmation.takeUnless { invalidatesConfirmation },
                    message = message(result.message(), action =
                        if (result == CartOperationResult.AddressRequired) CartUiMessageAction.SelectAddress else null),
                )
            }
        }
    }

    private fun message(
        text: String,
        kind: CartUiMessageKind = CartUiMessageKind.Error,
        action: CartUiMessageAction? = null,
    ) = CartUiMessage(++nextMessageId, text, kind, action)
}

private fun CartOperationResult.message(): String = when (this) {
    is CartOperationResult.RuleRejected -> when (reason) {
        CartChangeRejectionReason.INVALID_QUANTITY -> "La cantidad debe ser mayor que cero."
        CartChangeRejectionReason.QUANTITY_OVERFLOW -> "La cantidad solicitada es demasiado grande."
        CartChangeRejectionReason.AMOUNT_OVERFLOW -> "El importe supera el límite admitido."
        CartChangeRejectionReason.PRODUCT_NOT_IN_CART -> "El producto ya no está en tu carrito."
        CartChangeRejectionReason.INCONSISTENT_BUSINESS -> "El producto no corresponde al negocio del carrito."
    }
    is CartOperationResult.OfferRejected -> when (reason) {
        AddToCartRejectionReason.INVALID_QUANTITY -> "La cantidad debe ser mayor que cero."
        AddToCartRejectionReason.PRODUCT_INACTIVE -> "El producto ya no está disponible."
        AddToCartRejectionReason.OUT_OF_STOCK -> "El producto está agotado en este local."
        AddToCartRejectionReason.CONFIGURATION_MISSING -> "El producto no tiene una oferta disponible en este local."
        AddToCartRejectionReason.CONFIGURATION_INVALID -> "No se pudo validar el precio o la disponibilidad del producto."
        AddToCartRejectionReason.DIFFERENT_BUSINESS,
        AddToCartRejectionReason.DIFFERENT_STORE,
        AddToCartRejectionReason.DIFFERENT_PRODUCT -> "La oferta ya no corresponde al producto y local seleccionados."
    }
    CartOperationResult.OfferUnavailable -> "La oferta no está disponible para tu dirección en este momento."
    CartOperationResult.OfferNotFound -> "El producto ya no existe."
    CartOperationResult.OfferNetworkError -> "No se pudo verificar la oferta. Revisa tu conexión e inténtalo otra vez."
    CartOperationResult.OfferPermissionDenied -> "No tienes permiso para consultar esta oferta."
    CartOperationResult.OfferUnverified -> "No se pudo confirmar la oferta con el servidor. Inténtalo otra vez con conexión."
    CartOperationResult.OfferInvalidData -> "La oferta contiene datos incompletos o inválidos."
    CartOperationResult.AddressRequired -> "Selecciona una dirección de entrega para validar la oferta."
    CartOperationResult.AddressUnavailable -> "No se pudo consultar tu dirección de entrega."
    CartOperationResult.AddressChanged -> "Tu dirección cambió. Vuelve a agregar el producto para validar su cobertura."
    CartOperationResult.RemoteValidationRequired -> "Necesitas validar la oferta con conexión antes de aumentar la cantidad."
    CartOperationResult.ConfirmationStale -> "La confirmación ya no es válida. Vuelve a agregar el producto."
    CartOperationResult.StorageError -> "No se pudo actualizar el carrito. Revisa su contenido antes de intentarlo otra vez."
    is CartOperationResult.Updated,
    is CartOperationResult.ConfirmationRequired,
    CartOperationResult.SessionChanged,
    CartOperationResult.SignedOut -> error("Este resultado se representa mediante su estado, no como error.")
}
