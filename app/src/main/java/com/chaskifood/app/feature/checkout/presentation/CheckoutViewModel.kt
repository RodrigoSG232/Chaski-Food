package com.chaskifood.app.feature.checkout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class CheckoutPaymentMethod { CASH_ON_DELIVERY }

data class CheckoutUiState(
    val loading: Boolean = true,
    val result: CheckoutReviewResult? = null,
    val paymentMethod: CheckoutPaymentMethod? = null,
    val acceptedPriceChanges: Boolean = false,
) {
    val reviewComplete: Boolean get() = (result as? CheckoutReviewResult.Ready)?.let {
        paymentMethod != null && (!it.quote.hasPriceChanges || acceptedPriceChanges)
    } == true
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModel @Inject constructor(
    private val repository: CheckoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState = _uiState.asStateFlow()
    private var reviewJob: Job? = null
    private var context: CheckoutContextState = CheckoutContextState.Loading
    private var pendingReview = false
    private var attempt = 0L
    private val observationGeneration = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            observationGeneration.flatMapLatest {
                repository.observeContext().catch { error ->
                    if (error is CancellationException) throw error
                    emit(CheckoutContextState.ReadError)
                }
            }.collect { current ->
                if (current == context) return@collect
                val previous = context as? CheckoutContextState.Ready
                val newSession = current == CheckoutContextState.SignedOut ||
                    (previous != null && current == CheckoutContextState.Loading) ||
                    (previous != null && current is CheckoutContextState.Ready &&
                        (previous.context.ownerUid != current.context.ownerUid ||
                            previous.context.sessionGeneration != current.context.sessionGeneration))
                val requested = pendingReview
                context = current
                reviewJob?.cancel()
                attempt++
                pendingReview = requested && current == CheckoutContextState.Loading
                _uiState.value = _uiState.value.copy(
                    loading = current == CheckoutContextState.Loading,
                    result = when (current) {
                        CheckoutContextState.SignedOut -> CheckoutReviewResult.SignedOut
                        CheckoutContextState.ReadError -> CheckoutReviewResult.Error("No pudimos leer tu carrito o dirección.")
                        else -> null
                    },
                    paymentMethod = if (newSession) null else _uiState.value.paymentMethod,
                    acceptedPriceChanges = false,
                )
                if (requested && current is CheckoutContextState.Ready) startReview(current.context)
            }
        }
    }

    fun review() {
        // ON_RESUME puede repetirse mientras el mismo intento está en curso. Una vez terminado,
        // el siguiente regreso sí consulta otra vez precios, horarios y disponibilidad remotos.
        if (context == CheckoutContextState.Loading && pendingReview) return
        if (context is CheckoutContextState.Ready && reviewJob?.isActive == true) {
            pendingReview = true // Si el contexto cambia antes de terminar, revisa el nuevo.
            return
        }
        reviewJob?.cancel()
        attempt++
        if (context == CheckoutContextState.ReadError) {
            context = CheckoutContextState.Loading
            pendingReview = true
            _uiState.value = _uiState.value.copy(loading = true, result = null,
                acceptedPriceChanges = false)
            observationGeneration.value++
            return
        }
        pendingReview = context == CheckoutContextState.Loading
        _uiState.value = _uiState.value.copy(loading = true, result = null,
            acceptedPriceChanges = false)
        (context as? CheckoutContextState.Ready)?.let { startReview(it.context) }
        if (!pendingReview && context !is CheckoutContextState.Ready) {
            _uiState.value = _uiState.value.copy(loading = false, result = when (context) {
                CheckoutContextState.SignedOut -> CheckoutReviewResult.SignedOut
                else -> CheckoutReviewResult.Error("No pudimos leer tu carrito o dirección.")
            })
        }
    }

    private fun startReview(expected: CheckoutContext) {
        pendingReview = false
        val request = ++attempt
        _uiState.value = _uiState.value.copy(loading = true, result = null,
            acceptedPriceChanges = false)
        reviewJob = viewModelScope.launch {
            try {
                val result = repository.review(expected)
                currentCoroutineContext().ensureActive()
                publish(request, expected, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                publish(request, expected,
                    CheckoutReviewResult.Error("No pudimos comprobar la compra. Reintenta."))
            } finally {
                if (request == attempt) pendingReview = false
            }
        }
    }

    /** Relee las fuentes locales; después compara intento y contexto sin otra suspensión. */
    private suspend fun publish(request: Long, expected: CheckoutContext, result: CheckoutReviewResult) {
        val stillCurrent = repository.isContextCurrent(expected)
        currentCoroutineContext().ensureActive()
        if (request != attempt || (context as? CheckoutContextState.Ready)?.context != expected) return
        _uiState.value = _uiState.value.copy(loading = false,
            result = if (stillCurrent) result else null,
            acceptedPriceChanges = if (stillCurrent) _uiState.value.acceptedPriceChanges else false)
    }

    fun selectPaymentMethod(method: CheckoutPaymentMethod) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun acceptPriceChanges() {
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        val request = attempt
        if (_uiState.value.result !is CheckoutReviewResult.Ready) return
        viewModelScope.launch {
            val stillCurrent = repository.isContextCurrent(expected)
            currentCoroutineContext().ensureActive()
            if (request == attempt && (context as? CheckoutContextState.Ready)?.context == expected &&
                _uiState.value.result is CheckoutReviewResult.Ready) {
                _uiState.value = if (stillCurrent) _uiState.value.copy(acceptedPriceChanges = true)
                    else _uiState.value.copy(result = null, acceptedPriceChanges = false)
            }
        }
    }
}
