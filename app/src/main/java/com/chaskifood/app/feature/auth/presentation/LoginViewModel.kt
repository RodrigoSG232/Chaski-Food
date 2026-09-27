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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")

    private val _uiState = MutableStateFlow<UiState<AuthUser?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<AuthUser?>> = _uiState.asStateFlow()

    fun login(onSuccess: (hasPhoneNumber: Boolean) -> Unit) {
        val currentEmail = email.value.trim()
        val currentPassword = password.value

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _uiState.value = UiState.Error("Por favor ingresa tu correo y contraseña.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.loginWithEmail(currentEmail, currentPassword)) {
                is ApiResult.Success -> {
                    _uiState.value = UiState.Success(result.data)
                    val hasPhone = !result.data.phoneNumber.isNullOrBlank()
                    onSuccess(hasPhone)
                }
                is ApiResult.Failure -> {
                    _uiState.value = UiState.Error(result.message) {
                        _uiState.value = UiState.Success(null)
                    }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: (hasPhoneNumber: Boolean) -> Unit) {
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
                            val hasPhone = !authResult.data.phoneNumber.isNullOrBlank()
                            onSuccess(hasPhone)
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