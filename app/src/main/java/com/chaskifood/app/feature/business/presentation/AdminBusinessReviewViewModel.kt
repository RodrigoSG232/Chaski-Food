package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.core.common.retryableQuery
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.canBeEvaluated
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminBusinessReviewViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val selectedFilter = MutableStateFlow<BusinessStatus?>(BusinessStatus.PENDING_REVIEW)

    private val retries = MutableStateFlow(0)
    private val allRequestsFlow = retryableQuery(retries, businessRepository::getAllBusinessRequests)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun retryLoading() { retries.value += 1 }

    val filteredRequests: StateFlow<ApiResult<List<BusinessRequest>>?> = combine(
        allRequestsFlow,
        selectedFilter,
    ) { result, filter ->
        when (result) {
            null -> null
            is ApiResult.Success -> {
                val list = if (filter == null) {
                    result.data
                } else {
                    result.data.filter { it.status == filter }
                }
                ApiResult.Success(list)
            }
            is ApiResult.Failure -> result
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    private val _actionState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
    val actionState: StateFlow<UiState<Unit?>> = _actionState.asStateFlow()

    fun clearActionError() {
        if (_actionState.value !is UiState.Loading) _actionState.value = UiState.Success(null)
    }

    fun evaluateRequest(
        requestId: String,
        status: BusinessStatus,
        observations: String? = null,
        onSuccess: () -> Unit,
    ) {
        if (_actionState.value is UiState.Loading) return
        val request = (allRequestsFlow.value as? ApiResult.Success)?.data?.find { it.id == requestId }
        if (request?.status?.canBeEvaluated != true) {
            _actionState.value = UiState.Error("La solicitud ya no está pendiente o no está disponible. Actualiza la lista.")
            return
        }
        if (status !in setOf(BusinessStatus.APPROVED, BusinessStatus.OBSERVED, BusinessStatus.REJECTED)) {
            _actionState.value = UiState.Error("Selecciona una acción de evaluación válida.")
            return
        }

        if ((status == BusinessStatus.OBSERVED || status == BusinessStatus.REJECTED) && observations.isNullOrBlank()) {
            _actionState.value = UiState.Error("Debes ingresar obligatoriamente las observaciones o motivo.") {
                _actionState.value = UiState.Success(null)
            }
            return
        }

        _actionState.value = UiState.Loading
        viewModelScope.launch {
            val result = try {
                val user = authRepository.currentUserFlow.first()
                if (user == null) {
                    _actionState.value = UiState.Error("Inicia sesión para evaluar solicitudes.")
                    return@launch
                }
                businessRepository.evaluateBusinessRequest(requestId, status, observations?.trim(), user.email ?: user.uid)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ApiResult.Failure("No se pudo completar la evaluación. Intenta nuevamente.") }
            when (result) {
                is ApiResult.Success -> {
                    _actionState.value = UiState.Success(Unit)
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    _actionState.value = UiState.Error(result.message) {
                        _actionState.value = UiState.Success(null)
                    }
                }
            }
        }
    }
}
