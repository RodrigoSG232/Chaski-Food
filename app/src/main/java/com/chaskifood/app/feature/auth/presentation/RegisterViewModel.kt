package com.chaskifood.app.feature.auth.presentation

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.R
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    private val _uiState = MutableStateFlow<UiState<AuthUser?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<AuthUser?>> = _uiState.asStateFlow()

    fun register(onSuccess: () -> Unit) {
        val currentEmail = email.value.trim()
        val currentPassword = password.value
        val currentConfirmPassword = confirmPassword.value

        if (currentEmail.isBlank() || currentPassword.isBlank() || currentConfirmPassword.isBlank()) {
            _uiState.value = UiState.Error("Por favor completa todos los campos.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (currentPassword != currentConfirmPassword) {
            _uiState.value = UiState.Error("Las contraseñas no coinciden.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (currentPassword.length < 6) {
            _uiState.value = UiState.Error("La contraseña debe tener al menos 6 caracteres.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.signUpWithEmail(currentEmail, currentPassword)) {
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

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val webClientId = context.getString(R.string.default_web_client_id)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    when (val authResult = authRepository.signInWithGoogle(googleIdTokenCredential.idToken)) {
                        is ApiResult.Success -> {
                            _uiState.value = UiState.Success(authResult.data)
                            onSuccess()
                        }
                        is ApiResult.Failure -> {
                            _uiState.value = UiState.Error(authResult.message) {
                                _uiState.value = UiState.Success(null)
                            }
                        }
                    }
                } else {
                    _uiState.value = UiState.Error("No se pudo obtener la credencial de Google.") {
                        _uiState.value = UiState.Success(null)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Error al conectar con Google.") {
                    _uiState.value = UiState.Success(null)
                }
            }
        }
    }
}