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
import com.google.firebase.auth.FirebaseAuth
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManageStoresUiState>(ManageStoresUiState.Loading)
    val uiState: StateFlow<ManageStoresUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val currentAuthUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    init {
        loadData()
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = ManageStoresUiState.Loading
            val uid = currentAuthUid
            if (uid.isBlank()) {
                _uiState.value = ManageStoresUiState.Error("Usuario no autenticado.")
                return@launch
            }

            businessRepository.getBusinessRequest(uid).collectLatest { businessResult ->
                when (businessResult) {
                    is ApiResult.Success -> {
                        val business = businessResult.data
                        if (business == null) {
                            _uiState.value = ManageStoresUiState.NoBusinessFound
                        } else if (business.status != BusinessStatus.APPROVED) {
                            _uiState.value = ManageStoresUiState.NotApproved
                        } else {
                            observeStoresAndManagers(business)
                        }
                    }
                    is ApiResult.Failure -> {
                        _uiState.value = ManageStoresUiState.Error(businessResult.message ?: "Error al cargar la información del negocio.")
                    }
                }
            }
        }
    }

    private fun observeStoresAndManagers(business: BusinessRequest) {
        viewModelScope.launch {
            storeRepository.getStoresByBusiness(business.id).collectLatest { storesResult ->
                val currentStores = when (storesResult) {
                    is ApiResult.Success -> storesResult.data
                    is ApiResult.Failure -> emptyList()
                }

                storeRepository.getManagersByBusiness(business.id).collectLatest { managersResult ->
                    val currentManagers = when (managersResult) {
                        is ApiResult.Success -> managersResult.data
                        is ApiResult.Failure -> emptyList()
                    }

                    _uiState.value = ManageStoresUiState.Success(
                        business = business,
                        stores = currentStores,
                        managers = currentManagers,
                    )
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
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        val trimmedPhone = phone.trim()
        val latitude = latitudeStr.toDoubleOrNull()
        val longitude = longitudeStr.toDoubleOrNull()

        if (trimmedName.isBlank() || trimmedAddress.isBlank()) {
            _actionMessage.value = "Por favor completa el nombre y la dirección del local."
            return
        }

        if (latitude == null || longitude == null) {
            _actionMessage.value = "Ingresa coordenadas de latitud y longitud válidas."
            return
        }

        if (trimmedPhone.isBlank() || trimmedPhone.length < 7) {
            _actionMessage.value = "Ingresa un número de teléfono de contacto válido."
            return
        }

        viewModelScope.launch {
            val store = BusinessStore(
                id = id,
                businessId = businessId,
                name = trimmedName,
                address = trimmedAddress,
                latitude = latitude,
                longitude = longitude,
                phone = trimmedPhone,
                status = status,
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
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()

        if (trimmedEmail.isBlank() || trimmedName.isBlank()) {
            _actionMessage.value = "Ingresa el correo y nombre completo del responsable."
            return
        }

        if (assignedStoreIds.isEmpty()) {
            _actionMessage.value = "Selecciona al menos un local para asignar al responsable."
            return
        }

        viewModelScope.launch {
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
        }
    }
}
