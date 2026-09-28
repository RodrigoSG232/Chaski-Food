package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.google.firebase.auth.FirebaseAuth
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<StoreOperationsUiState>(StoreOperationsUiState.Loading)
    val uiState: StateFlow<StoreOperationsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val currentUserEmailOrUid: String
        get() {
            val user = FirebaseAuth.getInstance().currentUser
            return user?.email?.ifBlank { user.uid } ?: user?.uid ?: ""
        }

    init {
        loadAssignedStores()
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun loadAssignedStores() {
        viewModelScope.launch {
            _uiState.value = StoreOperationsUiState.Loading
            val identifier = currentUserEmailOrUid
            if (identifier.isBlank()) {
                _uiState.value = StoreOperationsUiState.Error("Usuario no autenticado.")
                return@launch
            }

            storeRepository.getStoresForManager(identifier).collectLatest { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val stores = result.data
                        if (stores.isEmpty()) {
                            _uiState.value = StoreOperationsUiState.NoStoresAssigned
                        } else {
                            _uiState.value = StoreOperationsUiState.Success(stores)
                        }
                    }
                    is ApiResult.Failure -> {
                        _uiState.value = StoreOperationsUiState.Error(result.message ?: "Error al cargar los locales asignados.")
                    }
                }
            }
        }
    }

    fun updateStatus(storeId: String, status: OperationalStatus, pauseReason: String? = null) {
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
