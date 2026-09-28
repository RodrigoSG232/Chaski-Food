package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminBusinessReviewViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val selectedFilter = MutableStateFlow<BusinessStatus?>(BusinessStatus.PENDING_REVIEW)

    val currentUser = authRepository.currentUserFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    private val allRequestsFlow = businessRepository.getAllBusinessRequests()

    val filteredRequests: StateFlow<ApiResult<List<BusinessRequest>>?> = combine(
        allRequestsFlow,
        selectedFilter,
    ) { result, filter ->
        when (result) {
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

    fun evaluateRequest(
        requestId: String,
        status: BusinessStatus,
        observations: String? = null,
        onSuccess: () -> Unit,
    ) {
        val reviewerEmail = currentUser.value?.email ?: "admin@chaskifood.com"

        if ((status == BusinessStatus.OBSERVED || status == BusinessStatus.REJECTED) && observations.isNullOrBlank()) {
            _actionState.value = UiState.Error("Debes ingresar obligatoriamente las observaciones o motivo.") {
                _actionState.value = UiState.Success(null)
            }
            return
        }

        _actionState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = businessRepository.evaluateBusinessRequest(requestId, status, observations, reviewerEmail)) {
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