package com.chaskifood.app.feature.checkout.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.checkout.domain.CheckoutConfirmation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.domain.CheckoutClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val confirmation: CheckoutConfirmation = CheckoutConfirmation.None,
) {
    val reviewComplete: Boolean get() = (result as? CheckoutReviewResult.Ready)?.let {
        paymentMethod != null && (!it.quote.hasPriceChanges || acceptedPriceChanges)
    } == true
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModel @Inject constructor(
    private val repository: CheckoutRepository,
    private val clock: CheckoutClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState = _uiState.asStateFlow()
    private var orderJob: Job? = null
    private var orderTicket = 0L
    private var reviewJob: Job? = null
    private var expiryJob: Job? = null
    private var context: CheckoutContextState = CheckoutContextState.Loading
    private var pendingReview = false
    private var needsFreshReview = false
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
                if (newSession) { orderJob?.cancel(); orderJob = null; orderTicket++ }
                context = current
                reviewJob?.cancel()
                expiryJob?.cancel()
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
                    confirmation = if (newSession) CheckoutConfirmation.None else _uiState.value.confirmation,
                )
                if (requested && current is CheckoutContextState.Ready) pendingReview = true
                if (current is CheckoutContextState.Ready && (previous == null || newSession)) loadPending()
                if (pendingReview && current is CheckoutContextState.Ready &&
                    _uiState.value.confirmation == CheckoutConfirmation.None && orderJob?.isActive != true) startReview(current.context)
            }
        }
    }

    fun review() {
        if (orderJob?.isActive == true && _uiState.value.confirmation == CheckoutConfirmation.None) {
            pendingReview = true
            return
        }
        if (_uiState.value.confirmation is CheckoutConfirmation.Success ||
            _uiState.value.confirmation is CheckoutConfirmation.Pending ||
            _uiState.value.confirmation is CheckoutConfirmation.Conflict || orderJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(confirmation = CheckoutConfirmation.None)
        val resumedAfterLeaving = needsFreshReview
        needsFreshReview = false
        // ON_RESUME puede repetirse mientras el mismo intento está en curso. Una vez terminado,
        // el siguiente regreso sí consulta otra vez precios, horarios y disponibilidad remotos.
        if (context == CheckoutContextState.Loading && pendingReview) return
        if (!resumedAfterLeaving && context is CheckoutContextState.Ready && reviewJob?.isActive == true) {
            pendingReview = true // Si el contexto cambia antes de terminar, revisa el nuevo.
            return
        }
        reviewJob?.cancel()
        expiryJob?.cancel()
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

    /** Un regreso real requiere una revisión nueva aunque la anterior siga esperando la red. */
    fun onScreenPaused() { needsFreshReview = true }

    private fun startReview(expected: CheckoutContext) {
        pendingReview = false
        val request = ++attempt
        expiryJob?.cancel()
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
        val currentResult = if (!stillCurrent) null else if (result is CheckoutReviewResult.Ready &&
            clock.monotonicTimeMillis() >= result.quote.deliveryQuoteDeadlineMillis) {
            CheckoutReviewResult.Error("La cotización de reparto venció. Vuelve a comprobar la compra.")
        } else result
        _uiState.value = _uiState.value.copy(loading = false,
            result = currentResult,
            acceptedPriceChanges = if (stillCurrent) _uiState.value.acceptedPriceChanges else false)
        if (currentResult is CheckoutReviewResult.Ready) {
            expiryJob?.cancel()
            expiryJob = viewModelScope.launch {
                delay((currentResult.quote.deliveryQuoteDeadlineMillis - clock.monotonicTimeMillis()).coerceAtLeast(0L))
                if (request == attempt && (context as? CheckoutContextState.Ready)?.context == expected &&
                    _uiState.value.result == currentResult) {
                    _uiState.value = _uiState.value.copy(result = CheckoutReviewResult.Error(
                        "La cotización de reparto venció. Vuelve a comprobar la compra."),
                        acceptedPriceChanges = false)
                }
            }
        }
    }

    fun selectPaymentMethod(method: CheckoutPaymentMethod) {
        if (expireIfNeeded()) return
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun acceptPriceChanges() {
        if (expireIfNeeded()) return
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        val request = attempt
        if (_uiState.value.result !is CheckoutReviewResult.Ready) return
        viewModelScope.launch {
            val stillCurrent = repository.isContextCurrent(expected)
            currentCoroutineContext().ensureActive()
            if (request == attempt && (context as? CheckoutContextState.Ready)?.context == expected &&
                _uiState.value.result is CheckoutReviewResult.Ready) {
                _uiState.value = if (expireIfNeeded()) _uiState.value
                    else if (stillCurrent) _uiState.value.copy(acceptedPriceChanges = true)
                    else _uiState.value.copy(result = null, acceptedPriceChanges = false)
            }
        }
    }

    fun confirm() {
        if (orderJob?.isActive == true || expireIfNeeded() || !_uiState.value.reviewComplete ||
            _uiState.value.confirmation != CheckoutConfirmation.None) return
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        val quote = (_uiState.value.result as? CheckoutReviewResult.Ready)?.quote ?: return
        orderAction(expected, working = true) { repository.confirm(expected, quote) }
    }

    /** Lee Room al abrir; nunca envía POST automáticamente ni restaura consentimiento. */
    fun loadPending() {
        if (orderJob?.isActive == true) return
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        val id = if (savedState.get<String>("order_owner") == expected.ownerUid)
            savedState.get<String>("order_intent") else null
        orderAction(expected, working = false) { repository.pending(expected.ownerUid, expected.sessionGeneration, id) }
    }

    fun recover() {
        val id = when (val state = _uiState.value.confirmation) {
            is CheckoutConfirmation.Pending -> state.intentId
            is CheckoutConfirmation.Conflict -> state.intentId
            else -> { loadPending(); return }
        }
        if (id == null) { loadPending(); return }
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        orderAction(expected, working = true) { repository.recover(expected.ownerUid, expected.sessionGeneration, id) }
    }

    fun retryOriginal() {
        val id = (_uiState.value.confirmation as? CheckoutConfirmation.Pending)?.intentId ?: return
        val expected = (context as? CheckoutContextState.Ready)?.context ?: return
        orderAction(expected, working = true) { repository.retry(expected.ownerUid, expected.sessionGeneration, id) }
    }

    private fun orderAction(expected: CheckoutContext, working: Boolean, action: suspend () -> CheckoutConfirmation) {
        if (orderJob?.isActive == true) return
        val ticket = ++orderTicket
        if (working) _uiState.value = _uiState.value.copy(confirmation = CheckoutConfirmation.Working)
        orderJob = viewModelScope.launch {
            val result = try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { CheckoutConfirmation.StorageError }
            currentCoroutineContext().ensureActive()
            val current = (context as? CheckoutContextState.Ready)?.context
            if (ticket != orderTicket || current?.ownerUid != expected.ownerUid ||
                current.sessionGeneration != expected.sessionGeneration) return@launch
            val id = when (result) {
                is CheckoutConfirmation.Pending -> result.intentId
                is CheckoutConfirmation.Success -> result.receipt.intentId
                is CheckoutConfirmation.Rejected -> result.intentId
                is CheckoutConfirmation.Conflict -> result.intentId
                else -> null
            }
            if (id != null) { savedState["order_owner"] = expected.ownerUid; savedState["order_intent"] = id }
            _uiState.value = _uiState.value.copy(confirmation = result,
                acceptedPriceChanges = if (result == CheckoutConfirmation.None) _uiState.value.acceptedPriceChanges else false)
            if (result == CheckoutConfirmation.None && pendingReview) startReview(current)
            else if (result != CheckoutConfirmation.None) pendingReview = false
        }
    }

    private fun expireIfNeeded(): Boolean {
        val quote = (_uiState.value.result as? CheckoutReviewResult.Ready)?.quote ?: return false
        if (clock.monotonicTimeMillis() < quote.deliveryQuoteDeadlineMillis) return false
        _uiState.value = _uiState.value.copy(result = CheckoutReviewResult.Error(
            "La cotización de reparto venció. Vuelve a comprobar la compra."),
            acceptedPriceChanges = false)
        return true
    }
}
