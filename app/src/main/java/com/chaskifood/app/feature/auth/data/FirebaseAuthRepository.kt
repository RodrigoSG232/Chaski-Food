package com.chaskifood.app.feature.auth.data

import android.app.Activity
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.datastore.SessionDataStore
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionDataStore: SessionDataStore,
) : AuthRepository {

    override val currentUserFlow: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                val token = result.user?.getIdToken(false)?.await()?.token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo obtener la información del usuario.")
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            ApiResult.Failure("Correo o contraseña incorrectos.")
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al iniciar sesión.", e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                val token = result.user?.getIdToken(false)?.await()?.token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo registrar el usuario.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            ApiResult.Failure("Este correo electrónico ya está registrado. Por favor inicia sesión.")
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al registrarse.", e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                val token = result.user?.getIdToken(false)?.await()?.token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo iniciar sesión con Google.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            ApiResult.Failure("Este correo ya está asociado a otra cuenta.")
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al iniciar sesión con Google.", e)
        }
    }

    override suspend fun sendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
    ): ApiResult<String> = suspendCancellableCoroutine { continuation ->
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification handled if applicable
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            ApiResult.Failure(
                                e.localizedMessage ?: "Error al enviar código SMS.",
                                e,
                            ),
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(ApiResult.Success(verificationId))
                    }
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override suspend fun verifyPhoneCode(
        verificationId: String,
        code: String,
    ): ApiResult<Unit> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                currentUser.linkWithCredential(credential).await()
            } else {
                firebaseAuth.signInWithCredential(credential).await()
            }
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Código SMS incorrecto.", e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            ApiResult.Success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            ApiResult.Failure("No existe ninguna cuenta registrada con este correo electrónico.")
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al enviar el correo de recuperación.", e)
        }
    }

    override suspend fun updateProfile(displayName: String): ApiResult<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("No hay una sesión de usuario activa.")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar el perfil.", e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        sessionDataStore.clearSession()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        phoneNumber = phoneNumber,
    )
}