package com.chaskifood.app.feature.auth.presentation

import android.app.Activity
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSubmissionTest {
    @Test fun `espera SMS redondea segundos y limita el intervalo`() {
        assertEquals(60, remainingSmsWait(61_000L, 1000L))
        assertEquals(1, remainingSmsWait(1001L, 1000L))
        assertEquals(0, remainingSmsWait(999L, 1000L))
        assertEquals(60, remainingSmsWait(Long.MAX_VALUE, 1000L))
    }

    @Test fun `envio SMS bloquea doble toque y solicitudes durante la espera`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = PhoneAuthViewModel(SubmissionAuth(), SavedStateHandle())
        val gate = CompletableDeferred<ApiResult<String>>()
        var calls = 0
        var continued = 0
        try {
            model.phoneNumber.value = "+51987654321"
            repeat(2) { model.requestCode(false, { continued++ }) { calls++; gate.await() } }
            runCurrent()
            assertEquals(1, calls)
            gate.complete(ApiResult.Success("first-code"))
            runCurrent()
            model.requestCode(true, { continued++ }) { calls++; ApiResult.Success("second-code") }
            runCurrent()
            assertEquals(1, calls)
            assertEquals(1, continued)
            assertEquals("first-code", model.verificationId.value)
            assertTrue(model.uiState.value is UiState.Error)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `reenvio reemplaza ID y limpia codigo anterior sin salir de verificacion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = PhoneAuthViewModel(SubmissionAuth(), SavedStateHandle())
        try {
            model.phoneNumber.value = "+51987654321"
            model.verificationId.value = "old-code"
            model.smsCode.value = "123456"
            model.requestCode(true, { fail("Debe seguir en verificación") }) { phone ->
                assertEquals("+51987654321", phone)
                ApiResult.Success("new-code")
            }
            runCurrent()
            assertEquals("new-code", model.verificationId.value)
            assertEquals("", model.smsCode.value)
            assertNotNull(model.smsNotice.value)
            assertTrue(model.uiState.value is UiState.Success)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `reenvio fallido conserva el codigo utilizable y permite reintentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val state = SavedStateHandle()
        val model = PhoneAuthViewModel(SubmissionAuth(), state)
        try {
            model.phoneNumber.value = "+51987654321"
            model.verificationId.value = "old-code"
            model.smsCode.value = "123456"
            model.requestCode(true, { fail("No debe continuar") }) { ApiResult.Failure("Sin conexión") }
            runCurrent()
            assertEquals("old-code", model.verificationId.value)
            assertEquals("123456", model.smsCode.value)
            assertEquals("Sin conexión", (model.uiState.value as UiState.Error).message)
            state["smsRetryAt"] = 0L // The next attempt is after the cooldown.
            model.requestCode(true, {}) { ApiResult.Success("retry-code") }
            runCurrent()
            assertEquals("retry-code", model.verificationId.value)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `verificacion recupera numero ID y SMS del estado guardado`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = SubmissionAuth()
        val state = SavedStateHandle(mapOf("phone" to "+51987654321", "verificationId" to "restored", "smsCode" to "123456"))
        val model = PhoneAuthViewModel(auth, state)
        try {
            assertEquals("+51987654321", model.phoneNumber.value)
            model.verifyCode {}
            runCurrent()
            assertEquals("restored", auth.lastVerificationId)
            assertEquals("123456", auth.lastSmsCode)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `envio sin callback termina con error en vez de carga infinita`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = PhoneAuthViewModel(SubmissionAuth(), SavedStateHandle())
        try {
            model.phoneNumber.value = "+51987654321"
            model.requestCode(false, { fail("No debe continuar") }) { CompletableDeferred<ApiResult<String>>().await() }
            runCurrent()
            advanceTimeBy(90_000L)
            runCurrent()
            assertTrue(model.uiState.value is UiState.Error)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `doble toque en login envia una sola solicitud`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = SubmissionAuth()
        val model = LoginViewModel(auth)
        var confirmations = 0
        try {
            model.email.value = "user@example.com"
            model.password.value = "secret"
            repeat(2) { model.login { confirmations++ } }
            runCurrent()
            assertEquals(1, auth.loginCalls)
            assertEquals(0, confirmations)
            auth.loginGate.complete(Unit)
            runCurrent()
            assertEquals(1, confirmations)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `codigo SMS invalido nunca llega a Firebase`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = SubmissionAuth()
        val model = PhoneAuthViewModel(auth, SavedStateHandle())
        try {
            model.verificationId.value = "verification"
            for (code in listOf("", "12345", "1234567", "12a456", "１２３４５６")) {
                model.smsCode.value = code
                model.verifyCode { fail("No debe continuar") }
                runCurrent()
                assertTrue(model.uiState.value is UiState.Error)
            }
            assertEquals(0, auth.phoneCalls)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `doble toque en SMS verifica y continua una sola vez`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = SubmissionAuth()
        val model = PhoneAuthViewModel(auth, SavedStateHandle())
        var confirmations = 0
        try {
            model.verificationId.value = "verification"
            model.smsCode.value = "123456"
            repeat(2) { model.verifyCode { confirmations++ } }
            runCurrent()
            assertEquals(1, auth.phoneCalls)
            auth.phoneGate.complete(Unit)
            runCurrent()
            assertEquals(1, confirmations)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}

private class SubmissionAuth : AuthRepository {
    override val currentUserFlow = MutableStateFlow<AuthUser?>(null)
    val loginGate = CompletableDeferred<Unit>()
    val phoneGate = CompletableDeferred<Unit>()
    var loginCalls = 0
    var phoneCalls = 0
    var lastVerificationId: String? = null
    var lastSmsCode: String? = null
    override suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser> {
        loginCalls++
        loginGate.await()
        return ApiResult.Success(AuthUser("user", email, "User", "+51987654321"))
    }
    override suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit> {
        phoneCalls++
        lastVerificationId = verificationId
        lastSmsCode = code
        phoneGate.await()
        return ApiResult.Success(Unit)
    }
    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> = error("unused")
    override suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String> = error("unused")
    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> = error("unused")
    override suspend fun updateProfile(displayName: String): ApiResult<Unit> = error("unused")
    override suspend fun signOut() = Unit
}
