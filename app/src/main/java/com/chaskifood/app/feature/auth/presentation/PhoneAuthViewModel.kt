package com.chaskifood.app.feature.auth.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val phoneNumber = savedState.getMutableStateFlow("phone", "")
    val verificationId = savedState.getMutableStateFlow<String?>("verificationId", null)
    val smsCode = savedState.getMutableStateFlow("smsCode", "")
    val phoneVerified = authRepository.currentUserFlow.map { !it?.phoneNumber.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
    val uiState: StateFlow<UiState<Unit?>> = _uiState.asStateFlow()

    private val _resendSeconds = MutableStateFlow(remainingSmsWait(savedState["smsRetryAt"] ?: 0L))
    val resendSeconds = _resendSeconds.asStateFlow()
    private val _smsNotice = MutableStateFlow<String?>(null)
    val smsNotice = _smsNotice.asStateFlow()
    private var cooldownJob: Job? = null

    init { observeCooldown() }

    private fun observeCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            do {
                _resendSeconds.value = remainingSmsWait(savedState["smsRetryAt"] ?: 0L)
                if (_resendSeconds.value == 0) break
                delay(1000)
            } while (true)
        }
    }

    fun clearFeedback() {
        if (_uiState.value !is UiState.Loading) {
            _uiState.value = UiState.Success(null)
            _smsNotice.value = null
        }
    }

    fun resendCode(activity: Activity, onVerified: () -> Unit) =
        requestCode(resend = true, onSuccess = onVerified) { phone ->
            authRepository.resendPhoneVerificationCode(activity, phone)
        }

    fun sendCode(activity: Activity, onSuccess: () -> Unit) =
        requestCode(resend = false, onSuccess = onSuccess) { phone ->
            authRepository.sendPhoneVerificationCode(activity, phone)
        }

    internal fun requestCode(resend: Boolean, onSuccess: () -> Unit, send: suspend (String) -> ApiResult<String>) {
        if (_uiState.value is UiState.Loading) return
        val phone = phoneNumber.value.trim()
        if (!phone.matches(Regex("[+][1-9][0-9]{7,14}"))) {
            _uiState.value = UiState.Error("Ingresa un número válido con su código de país.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        val wait = remainingSmsWait(savedState["smsRetryAt"] ?: 0L)
        if (savedState.get<String>("smsRequestedPhone") == phone && wait > 0) {
            _uiState.value = UiState.Error("Espera $wait segundos antes de solicitar otro SMS.")
            return
        }

        _uiState.value = UiState.Loading
        _smsNotice.value = null
        savedState["smsRequestedPhone"] = phone
        savedState["smsRetryAt"] = System.currentTimeMillis() + 60_000L
        _resendSeconds.value = 60
        observeCooldown()
        viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(90_000L) { send(phone) }
                    ?: ApiResult.Failure("El envío tardó demasiado. Comprueba tu conexión e intenta nuevamente.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ApiResult.Failure("No se pudo enviar el SMS. Revisa tu conexión y vuelve a intentar.")
            }
            when (result) {
                is ApiResult.Success -> {
                    verificationId.value = result.data
                    smsCode.value = ""
                    _uiState.value = UiState.Success(null)
                    if (!resend || result.data.isBlank()) onSuccess()
                    else _smsNotice.value = "Enviamos un nuevo código SMS a $phone."
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
        if (_uiState.value is UiState.Loading) return
        val currentVerificationId = verificationId.value
        val code = smsCode.value.trim()

        if (currentVerificationId.isNullOrBlank()) {
            _uiState.value = UiState.Error("No se encontró la solicitud de verificación.") {
                _uiState.value = UiState.Success(null)
            }
            return
        }

        if (!code.matches(Regex("[0-9]{6}"))) {
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

internal fun remainingSmsWait(retryAt: Long, now: Long = System.currentTimeMillis()): Int =
    ((retryAt - now).coerceAtLeast(0L).coerceAtMost(60_000L) + 999L).div(1000L).toInt()
