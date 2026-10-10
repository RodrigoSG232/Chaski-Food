package com.chaskifood.app.feature.auth.data

import android.app.Activity
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.datastore.SessionDataStore
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionDataStore: SessionDataStore,
) : AuthRepository {

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var resendPhone: String? = null
    private var resendUserId: String? = null

    override val currentUserFlow: Flow<AuthUser?> = callbackFlow<FirebaseUser?> {
        val listener = FirebaseAuth.IdTokenListener { auth -> trySend(auth.currentUser) }
        firebaseAuth.addIdTokenListener(listener)
        awaitClose { firebaseAuth.removeIdTokenListener(listener) }
    }.mapLatest { user ->
        if (user == null) null else {
            val mapped = user.toAuthUser()
            if (firebaseAuth.currentUser?.uid == user.uid) mapped else null
        }
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure("No se pudo iniciar sesión. Revisa tus credenciales y conexión.", e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            // La cuenta ya existe aunque el envío falle; no repetir el alta.
            result.user?.sendEmailVerification()
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al iniciar sesión con Google.", e)
        }
    }

    override suspend fun sendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
    ): ApiResult<String> = sendPhoneCode(activity, phoneNumber, forceResend = false)

    override suspend fun resendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
    ): ApiResult<String> = sendPhoneCode(activity, phoneNumber, forceResend = true)

    private suspend fun sendPhoneCode(
        activity: Activity,
        phoneNumber: String,
        forceResend: Boolean,
    ): ApiResult<String> = suspendCancellableCoroutine { continuation ->
        val requestedUser = firebaseAuth.currentUser
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // También puede llegar después de onCodeSent. La renovación del token
                    // informa a la pantalla de código de que Firebase ya verificó el teléfono.
                    if (firebaseAuth.currentUser?.uid != requestedUser?.uid) return
                    val task = if (requestedUser == null) firebaseAuth.signInWithCredential(credential)
                        else requestedUser.linkWithCredential(credential)
                    task.addOnCompleteListener { completed ->
                        if (completed.isSuccessful) {
                            if (firebaseAuth.currentUser?.uid != completed.result?.user?.uid) return@addOnCompleteListener
                            completed.result?.user?.getIdToken(true)?.addOnCompleteListener {
                                if (continuation.isActive) continuation.resume(ApiResult.Success(""))
                            }
                        } else if (continuation.isActive) {
                            continuation.resume(ApiResult.Failure("No se pudo verificar automáticamente el teléfono.", completed.exception))
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            ApiResult.Failure(
                                when (e) {
                                    is FirebaseTooManyRequestsException -> "Se alcanzó el límite de solicitudes SMS. Espera unos minutos antes de volver a intentar."
                                    is FirebaseAuthInvalidCredentialsException -> "Revisa el número y el código de país antes de solicitar otro SMS."
                                    else -> "No se pudo enviar el SMS. Revisa tu conexión y vuelve a intentar."
                                },
                                e,
                            ),
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    if (firebaseAuth.currentUser?.uid == requestedUser?.uid) {
                        resendToken = token
                        resendPhone = phoneNumber
                        resendUserId = requestedUser?.uid
                    }
                    if (continuation.isActive) {
                        continuation.resume(ApiResult.Success(verificationId))
                    }
                }
            })
            .apply {
                if (forceResend && resendPhone == phoneNumber && resendUserId == requestedUser?.uid) {
                    resendToken?.let { setForceResendingToken(it) }
                }
            }
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
            firebaseAuth.currentUser?.let { user ->
                val token = user.getIdToken(false).await().token ?: error("No se pudo renovar la sesión.")
                sessionDataStore.setSession(user.uid, token)
            }
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(when {
                e is FirebaseAuthInvalidCredentialsException && e.errorCode == "ERROR_SESSION_EXPIRED" ->
                    "El código SMS venció. Solicita uno nuevo con Reenviar código SMS."
                e is FirebaseAuthInvalidCredentialsException -> "El código SMS no es válido. Revísalo o solicita uno nuevo."
                e is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos y vuelve a intentar."
                else -> "No se pudo verificar el código. Revisa tu conexión y vuelve a intentar."
            }, e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            ApiResult.Success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                user.getIdToken(true).await()
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("No hay una sesión de usuario activa.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar el perfil.", e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        sessionDataStore.clearSession()
    }

    private suspend fun FirebaseUser.toAuthUser(): AuthUser {
        val isAdmin = try { getIdToken(false).await().claims["admin"] == true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        return AuthUser(uid = uid, email = email, displayName = displayName,
            phoneNumber = phoneNumber, isAdmin = isAdmin)
    }
}
