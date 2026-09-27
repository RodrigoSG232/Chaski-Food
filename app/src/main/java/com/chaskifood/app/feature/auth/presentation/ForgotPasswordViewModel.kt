package com.chaskifood.app.feature.auth.presentation

import android.util.Patterns
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
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val email = MutableStateFlow("")

    private val _uiState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<Unit?>> = _uiState.asStateFlow()

    fun sendPasswordResetEmail(onSuccess: (email: String) -> Unit) {
        val currentEmail = email.value.trim()

        if (currentEmail.isBlank()) {
            _uiState.value = UiState.Error("Por favor ingresa tu correo electrónico.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            _uiState.value = UiState.Error("Ingresa un correo electrónico con formato válido (ejemplo@correo.com).") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetEmail(currentEmail)) {
                is ApiResult.Success -> {
                    _uiState.value = UiState.Success(Unit)
                    onSuccess(currentEmail)
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
