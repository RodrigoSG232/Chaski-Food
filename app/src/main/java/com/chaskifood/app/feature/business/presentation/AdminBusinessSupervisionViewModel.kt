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
class AdminBusinessSupervisionViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val selectedFilter = MutableStateFlow<BusinessStatus?>(BusinessStatus.APPROVED)

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
                val list = result.data.filter { it.status == BusinessStatus.APPROVED || it.status == BusinessStatus.SUSPENDED }
                val filtered = if (filter == null) {
                    list
                } else {
                    list.filter { it.status == filter }
                }
                ApiResult.Success(filtered)
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

    fun suspendBusiness(
        request: BusinessRequest,
        reason: String,
        onSuccess: () -> Unit,
    ) {
        val adminEmail = currentUser.value?.email ?: currentUser.value?.uid ?: "Desconocido"

        if (reason.isBlank()) {
            _actionState.value = UiState.Error("Debes ingresar obligatoriamente el motivo de la suspensión.") {
                _actionState.value = UiState.Success(null)
            }
            return
        }

        _actionState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = businessRepository.suspendBusiness(request.id, reason.trim(), adminEmail, request.businessName, request.ruc)) {
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

    fun reactivateBusiness(
        request: BusinessRequest,
        onSuccess: () -> Unit,
    ) {
        val adminEmail = currentUser.value?.email ?: currentUser.value?.uid ?: "Desconocido"

        _actionState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = businessRepository.reactivateBusiness(request.id, adminEmail, request.businessName, request.ruc)) {
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