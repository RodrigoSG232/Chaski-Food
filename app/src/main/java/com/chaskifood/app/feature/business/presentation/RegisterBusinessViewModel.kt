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
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    private val currentUser = authRepository.currentUserFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    fun populateFromExisting(request: BusinessRequest) {
        businessName.value = request.businessName
        ruc.value = request.ruc
        legalAddress.value = request.legalAddress
        phone.value = request.phone
        email.value = request.email
        category.value = request.category
    }

    fun submitRequest(isResubmission: Boolean = false, onSuccess: () -> Unit) {
        val ownerUid = FirebaseAuth.getInstance().currentUser?.uid ?: currentUser.value?.uid ?: ""
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
            ownerUid = ownerUid,
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
            val result = if (isResubmission) {
                businessRepository.resubmitBusinessRequest(request)
            } else {
                businessRepository.submitBusinessRequest(request)
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