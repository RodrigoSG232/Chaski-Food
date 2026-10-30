package com.chaskifood.app.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    private val _uiState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<Unit?>> = _uiState.asStateFlow()

    fun updateProfile(displayName: String, onSuccess: () -> Unit) {
        if (_uiState.value is UiState.Loading) return
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            _uiState.value = UiState.Error("El nombre completo no puede estar vacío.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }
        if (trimmedName.length > 200) {
            _uiState.value = UiState.Error("El nombre no puede superar 200 caracteres.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.updateProfile(trimmedName)) {
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

    /** [onSignedOut] receives false when the sign-out could not be protected against an immediate process death. */
    fun signOut(onSignedOut: (protected: Boolean) -> Unit) {
        viewModelScope.launch {
            onSignedOut(authRepository.signOut())
        }
    }

    companion object {
        /** Application-context toast so the warning survives the navigation that follows the sign-out. */
        fun warnIfUnprotected(context: android.content.Context, protected: Boolean) {
            if (!protected) android.widget.Toast.makeText(context.applicationContext, SIGN_OUT_UNPROTECTED, android.widget.Toast.LENGTH_LONG).show()
        }
        const val SIGN_OUT_UNPROTECTED = "Cerraste sesión, pero no se pudo confirmar en este dispositivo. Si al volver a abrir la app sigues dentro, cierra sesión otra vez."
    }
}
