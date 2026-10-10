package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.validOperatingHours
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StoreOperationsUiState {
    object Loading : StoreOperationsUiState
    object NoStoresAssigned : StoreOperationsUiState
    data class Success(val stores: List<BusinessStore>) : StoreOperationsUiState
    data class Error(val message: String) : StoreOperationsUiState
}

@HiltViewModel
class StoreOperationsViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StoreOperationsUiState>(StoreOperationsUiState.Loading)
    val uiState: StateFlow<StoreOperationsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var observation: Job? = null
    init { loadAssignedStores() }
    fun clearActionMessage() { _actionMessage.value = null }

    fun loadAssignedStores() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.value = StoreOperationsUiState.Loading
            authRepository.currentUserFlow.collectLatest { user ->
                val identifier = user?.email?.lowercase()
                if (identifier.isNullOrBlank()) {
                    _uiState.value = StoreOperationsUiState.Error("Inicia sesión con la cuenta asignada.")
                } else storeRepository.getStoresForManager(identifier).collectLatest { result ->
                    _uiState.value = when (result) {
                        is ApiResult.Success -> if (result.data.isEmpty()) StoreOperationsUiState.NoStoresAssigned
                            else StoreOperationsUiState.Success(result.data)
                        is ApiResult.Failure -> StoreOperationsUiState.Error(result.message ?: "No se pudieron cargar los locales.")
                    }
                }
            }
        }
    }

    private fun assigned(storeId: String): Boolean =
        (_uiState.value as? StoreOperationsUiState.Success)?.stores?.any { it.id == storeId } == true

    fun updateStatus(storeId: String, status: OperationalStatus, pauseReason: String? = null) {
        if (!assigned(storeId)) { _actionMessage.value = "Local no autorizado."; return }
        viewModelScope.launch {
            when (val result = storeRepository.updateOperationalStatus(storeId, status, pauseReason)) {
                is ApiResult.Success -> {
                    val statusText = when (status) {
                        OperationalStatus.OPEN -> "Local Abierto para recibir pedidos."
                        OperationalStatus.PAUSED -> "Local Pausado temporalmente."
                        OperationalStatus.CLOSED -> "Local Cerrado."
                    }
                    _actionMessage.value = statusText
                }
                is ApiResult.Failure -> {
                    _actionMessage.value = result.message
                }
            }
        }
    }

    fun updateHours(storeId: String, hours: List<DayOperatingHours>) {
        if (!assigned(storeId)) { _actionMessage.value = "Local no autorizado."; return }
        if (!validOperatingHours(hours)) {
            _actionMessage.value = "Usa horas HH:mm válidas; apertura y cierre deben diferir."
            return
        }
        viewModelScope.launch {
            when (val result = storeRepository.updateOperatingHours(storeId, hours)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "Horario de atención actualizado correctamente."
                }
                is ApiResult.Failure -> {
                    _actionMessage.value = result.message
                }
            }
        }
    }
}
