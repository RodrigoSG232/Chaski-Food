package com.chaskifood.app.feature.checkout.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CheckoutPaymentMethod { CASH_ON_DELIVERY }

data class CheckoutUiState(
    val loading: Boolean = false,
    val result: CheckoutReviewResult? = null,
    val paymentMethod: CheckoutPaymentMethod? = null,
    val acceptedPriceChanges: Boolean = false,
) {
    val reviewComplete: Boolean get() = (result as? CheckoutReviewResult.Ready)?.let {
        paymentMethod != null && (!it.quote.hasPriceChanges || acceptedPriceChanges)
    } == true
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val repository: CheckoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState = _uiState.asStateFlow()
    private var reviewJob: Job? = null

    fun review() {
        reviewJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, result = null,
            acceptedPriceChanges = false)
        reviewJob = viewModelScope.launch {
            try {
                val result = repository.review()
                _uiState.value = _uiState.value.copy(loading = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(loading = false,
                    result = CheckoutReviewResult.Error("No pudimos comprobar la compra. Reintenta."))
            }
        }
    }

    fun selectPaymentMethod(method: CheckoutPaymentMethod) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun acceptPriceChanges() {
        if (_uiState.value.result is CheckoutReviewResult.Ready) {
            _uiState.value = _uiState.value.copy(acceptedPriceChanges = true)
        }
    }
}
