package com.chaskifood.app.feature.auth.domain

import android.app.Activity
import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<AuthUser?>
    suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser>
    suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser>
    suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String>
    suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit>
    suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit>
    suspend fun updateProfile(displayName: String): ApiResult<Unit>
    suspend fun signOut()
}