package com.chaskifood.app.feature.auth.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val phoneNumber = MutableStateFlow("")
    val verificationId = MutableStateFlow<String?>(null)
    val smsCode = MutableStateFlow("")

    private val _uiState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<Unit?>> = _uiState.asStateFlow()

    fun sendCode(activity: Activity, onSuccess: () -> Unit) {
        val phone = phoneNumber.value.trim()
        if (phone.isBlank()) {
            _uiState.value = UiState.Error("Por favor ingresa tu número de teléfono.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.sendPhoneVerificationCode(activity, phone)) {
                is ApiResult.Success -> {
                    verificationId.value = result.data
                    _uiState.value = UiState.Success(null)
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

    fun verifyCode(onSuccess: () -> Unit) {
        val currentVerificationId = verificationId.value
        val code = smsCode.value.trim()

        if (currentVerificationId.isNullOrBlank()) {
            _uiState.value = UiState.Error("No se encontró la solicitud de verificación.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (code.isBlank() || code.length < 6) {
            _uiState.value = UiState.Error("Ingresa el código de 6 dígitos enviado por SMS.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.verifyPhoneCode(currentVerificationId, code)) {
                is ApiResult.Success -> {
                    _uiState.value = UiState.Success(Unit)
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