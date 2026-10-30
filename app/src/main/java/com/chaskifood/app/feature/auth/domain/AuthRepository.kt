package com.chaskifood.app.feature.auth.domain

import android.app.Activity
import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<AuthUser?>
    suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): ApiResult<AuthUser>
    suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser>
    suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String>
    suspend fun resendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String> =
        sendPhoneVerificationCode(activity, phoneNumber)
    suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit>
    suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit>
    suspend fun updateProfile(displayName: String): ApiResult<Unit>
    /** Always closes the session. Returns false when the interrupted-sign-out protection could not be persisted. */
    suspend fun signOut(): Boolean
}
