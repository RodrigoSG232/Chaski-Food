package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ManageStoresUiState {
    object Loading : ManageStoresUiState
    object NoBusinessFound : ManageStoresUiState
    object NotApproved : ManageStoresUiState
    data class Success(
        val business: BusinessRequest,
        val stores: List<BusinessStore>,
        val managers: List<StoreManager>,
    ) : ManageStoresUiState
    data class Error(val message: String) : ManageStoresUiState
}

@HiltViewModel
class ManageStoresViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManageStoresUiState>(ManageStoresUiState.Loading)
    val uiState: StateFlow<ManageStoresUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private var observation: Job? = null

    init { loadData() }

    fun clearActionMessage() { _actionMessage.value = null }

    fun loadData() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.value = ManageStoresUiState.Loading
            authRepository.currentUserFlow.collectLatest { user ->
                if (user == null) {
                    _uiState.value = ManageStoresUiState.Error("Usuario no autenticado.")
                } else businessRepository.getBusinessRequest(user.uid).collectLatest { businessResult ->
                    when (businessResult) {
                        is ApiResult.Failure -> _uiState.value = ManageStoresUiState.Error(businessResult.message ?: "No se pudo cargar el negocio.")
                        is ApiResult.Success -> {
                            val business = businessResult.data
                            when {
                                business == null -> _uiState.value = ManageStoresUiState.NoBusinessFound
                                business.status != BusinessStatus.APPROVED -> _uiState.value = ManageStoresUiState.NotApproved
                                else -> combine(storeRepository.getStoresByBusiness(business.id),
                                    storeRepository.getManagersByBusiness(business.id)) { stores, managers ->
                                    when {
                                        stores is ApiResult.Failure -> ManageStoresUiState.Error(stores.message ?: "No se pudieron cargar los locales.")
                                        managers is ApiResult.Failure -> ManageStoresUiState.Error(managers.message ?: "No se pudieron cargar los responsables.")
                                        else -> ManageStoresUiState.Success(business,
                                            (stores as ApiResult.Success).data, (managers as ApiResult.Success).data)
                                    }
                                }.collect { _uiState.value = it }
                            }
                        }
                    }
                }
            }
        }
    }

    fun saveStore(
        id: String,
        businessId: String,
        name: String,
        address: String,
        latitudeStr: String,
        longitudeStr: String,
        phone: String,
        status: StoreStatus,
        onComplete: () -> Unit,
    ) {
        if (_saving.value) return
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        val trimmedPhone = phone.trim()
        val latitude = latitudeStr.toDoubleOrNull()
        val longitude = longitudeStr.toDoubleOrNull()

        if (trimmedName.isBlank() || trimmedAddress.isBlank()) {
            _actionMessage.value = "Por favor completa el nombre y la dirección del local."
            return
        }

        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            _actionMessage.value = "Ingresa coordenadas de latitud y longitud válidas."
            return
        }

        if (trimmedPhone.isBlank() || trimmedPhone.length < 7) {
            _actionMessage.value = "Ingresa un número de teléfono de contacto válido."
            return
        }

        val state = _uiState.value as? ManageStoresUiState.Success ?: return
        if (state.business.id != businessId) { _actionMessage.value = "Negocio no autorizado."; return }
        val existing = state.stores.find { it.id == id }
        if (id.isNotBlank() && existing == null) { _actionMessage.value = "El local ya no está disponible."; return }
        _saving.value = true
        viewModelScope.launch {
            try {
                val store = (existing ?: BusinessStore(businessId = businessId)).copy(
                    id = id,
                    businessId = businessId,
                    name = trimmedName,
                    address = trimmedAddress,
                    latitude = latitude,
                    longitude = longitude,
                    phone = trimmedPhone,
                    status = if (existing?.status == StoreStatus.SUSPENDED) StoreStatus.SUSPENDED else status,
                )

                val result = if (id.isBlank()) {
                    storeRepository.createStore(store)
                } else {
                    storeRepository.updateStore(store)
                }

                when (result) {
                    is ApiResult.Success -> {
                        _actionMessage.value = if (id.isBlank()) "Local registrado con éxito." else "Local actualizado con éxito."
                        onComplete()
                    }
                    is ApiResult.Failure -> {
                        _actionMessage.value = result.message
                    }
                }
            } finally { _saving.value = false }
        }
    }

    fun assignManager(
        managerId: String,
        businessId: String,
        email: String,
        fullName: String,
        phone: String,
        assignedStoreIds: List<String>,
        onComplete: () -> Unit,
    ) {
        if (_saving.value) return
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = fullName.trim()

        if (trimmedEmail.isBlank() || trimmedName.isBlank()) {
            _actionMessage.value = "Ingresa el correo y nombre completo del responsable."
            return
        }

        val state = _uiState.value as? ManageStoresUiState.Success ?: return
        if (state.business.id != businessId || assignedStoreIds.any { id -> state.stores.none { it.id == id } }) {
            _actionMessage.value = "Selecciona únicamente locales de tu negocio."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _actionMessage.value = "Ingresa un correo válido."
            return
        }
        if (assignedStoreIds.isEmpty()) {
            _actionMessage.value = "Selecciona al menos un local para asignar al responsable."
            return
        }

        _saving.value = true
        viewModelScope.launch {
            try {
                val manager = StoreManager(
                    id = managerId,
                    businessId = businessId,
                    email = trimmedEmail,
                    fullName = trimmedName,
                    phone = phone.trim(),
                    assignedStoreIds = assignedStoreIds,
                )

                when (val result = storeRepository.assignManager(manager)) {
                    is ApiResult.Success -> {
                        _actionMessage.value = "Responsable asignado con éxito."
                        onComplete()
                    }
                    is ApiResult.Failure -> {
                        _actionMessage.value = result.message
                    }
                }
            } finally { _saving.value = false }
        }
    }
}
