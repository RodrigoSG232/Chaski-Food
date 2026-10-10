package com.chaskifood.app.feature.business.presentation

import android.util.Patterns
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterBusinessViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val businessName = MutableStateFlow("")
    val ruc = MutableStateFlow("")
    val legalAddress = MutableStateFlow("")
    val phone = MutableStateFlow("")
    val email = MutableStateFlow("")
    val category = MutableStateFlow("Restaurante")

    private val _uiState = MutableStateFlow<UiState<BusinessRequest?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<BusinessRequest?>> = _uiState.asStateFlow()

    val observations = MutableStateFlow<String?>(null)
    val readyToResubmit = MutableStateFlow(false)
    private var existingRequest: BusinessRequest? = null

    fun loadExistingForResubmission() {
        if (existingRequest != null) return
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val user = authRepository.currentUserFlow.first()
            if (user == null) { _uiState.value = UiState.Error("Inicia sesión para corregir tu solicitud."); return@launch }
            when (val result = businessRepository.getBusinessRequest(user.uid).first()) {
                is ApiResult.Failure -> _uiState.value = UiState.Error(result.message)
                is ApiResult.Success -> {
                    val request = result.data
                    if (request == null || request.status != BusinessStatus.OBSERVED) {
                        _uiState.value = UiState.Error("La solicitud ya no está observada. Consulta su estado.")
                    } else {
                        existingRequest = request
                        populateFromExisting(request)
                        observations.value = request.observations
                        readyToResubmit.value = true
                        _uiState.value = UiState.Success(null)
                    }
                }
            }
        }
    }

    fun populateFromExisting(request: BusinessRequest) {
        businessName.value = request.businessName
        ruc.value = request.ruc
        legalAddress.value = request.legalAddress
        phone.value = request.phone
        email.value = request.email
        category.value = request.category
    }

    fun submitRequest(isResubmission: Boolean = false, onSuccess: () -> Unit) {
        if (_uiState.value is UiState.Loading) return
        if (isResubmission && !readyToResubmit.value) {
            _uiState.value = UiState.Error("Carga tu solicitud observada antes de reenviar.")
            return
        }
        val name = businessName.value.trim()
        val currentRuc = ruc.value.trim()
        val address = legalAddress.value.trim()
        val currentPhone = phone.value.trim()
        val currentEmail = email.value.trim()
        val currentCategory = category.value.trim()

        if (name.isBlank() || currentRuc.isBlank() || address.isBlank() || currentPhone.isBlank() || currentEmail.isBlank()) {
            _uiState.value = UiState.Error("Por favor completa todos los campos obligatorios del negocio.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (currentRuc.length != 11 || !currentRuc.all { it.isDigit() }) {
            _uiState.value = UiState.Error("El RUC debe contener exactamente 11 dígitos numéricos.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            _uiState.value = UiState.Error("Ingresa un correo electrónico comercial válido.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        val request = BusinessRequest(
            id = existingRequest?.id.orEmpty(),
            ownerUid = existingRequest?.ownerUid.orEmpty(),
            businessName = name,
            ruc = currentRuc,
            legalAddress = address,
            phone = currentPhone,
            email = currentEmail,
            category = currentCategory,
            status = BusinessStatus.PENDING_REVIEW,
        )

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val user = authRepository.currentUserFlow.first()
            if (user == null || (existingRequest != null && existingRequest?.ownerUid != user.uid)) {
                _uiState.value = UiState.Error("Inicia sesión con la cuenta propietaria de la solicitud.")
                return@launch
            }
            val authenticatedRequest = request.copy(ownerUid = user.uid)
            val result = if (isResubmission) {
                businessRepository.resubmitBusinessRequest(authenticatedRequest)
            } else {
                businessRepository.submitBusinessRequest(authenticatedRequest)
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = UiState.Success(result.data)
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    _uiState.value = UiState.Error(result.message) {
                        _uiState.value = UiState.Success(null)
                    }
                }
            }
        }
    }
}
