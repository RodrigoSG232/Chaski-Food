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
import kotlinx.coroutines.CancellationException
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

enum class StoreOperation { STATUS, HOURS }

data class StoreOperationState(
    val operation: StoreOperation? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val successRevision: Long = 0,
)

@HiltViewModel
class StoreOperationsViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StoreOperationsUiState>(StoreOperationsUiState.Loading)
    val uiState: StateFlow<StoreOperationsUiState> = _uiState.asStateFlow()

    private val _operations = MutableStateFlow<Map<String, StoreOperationState>>(emptyMap())
    val operations = _operations.asStateFlow()

    private var observation: Job? = null
    init { loadAssignedStores() }
    fun clearOperationFeedback(storeId: String) {
        val state = _operations.value[storeId] ?: return
        if (!state.saving) _operations.value += storeId to state.copy(error = null, message = null)
    }

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
        val message = when (status) {
            OperationalStatus.OPEN -> "Local abierto para recibir pedidos."
            OperationalStatus.PAUSED -> "Local pausado temporalmente."
            OperationalStatus.CLOSED -> "Local cerrado."
        }
        performOperation(storeId, StoreOperation.STATUS, message) {
            storeRepository.updateOperationalStatus(storeId, status, pauseReason?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun updateHours(storeId: String, hours: List<DayOperatingHours>) {
        if (_operations.value[storeId]?.saving == true) return
        if (!validOperatingHours(hours)) {
            showError(storeId, StoreOperation.HOURS, "Usa horas HH:mm válidas; apertura y cierre deben diferir.")
            return
        }
        val snapshot = hours.toList()
        performOperation(storeId, StoreOperation.HOURS, "Horario de atención guardado correctamente.") {
            storeRepository.updateOperatingHours(storeId, snapshot)
        }
    }

    private fun showError(storeId: String, operation: StoreOperation, error: String) {
        val previous = _operations.value[storeId] ?: StoreOperationState()
        _operations.value += storeId to previous.copy(operation = operation, saving = false, error = error, message = null)
    }

    private fun performOperation(
        storeId: String,
        operation: StoreOperation,
        successMessage: String,
        save: suspend () -> ApiResult<Unit>,
    ) {
        if (_operations.value[storeId]?.saving == true) return
        if (!assigned(storeId)) { showError(storeId, operation, "Local no autorizado. Actualiza la lista."); return }
        val previous = _operations.value[storeId] ?: StoreOperationState()
        _operations.value += storeId to previous.copy(operation = operation, saving = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val result = try { save() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ApiResult.Failure("No se pudo guardar el cambio. Intenta nuevamente.") }
                when (result) {
                    is ApiResult.Success -> _operations.value += storeId to StoreOperationState(
                        operation = operation, message = successMessage, successRevision = previous.successRevision + 1,
                    )
                    is ApiResult.Failure -> showError(storeId, operation, result.message ?: "No se pudo guardar el cambio. Intenta nuevamente.")
                }
            } finally {
                val current = _operations.value[storeId]
                if (current?.saving == true) _operations.value += storeId to current.copy(saving = false)
            }
        }
    }
}
