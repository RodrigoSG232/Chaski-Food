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
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private val profileStore: FirebaseUserProfileStore,
    private val notifications:com.chaskifood.app.core.notifications.NotificationCoordinator,
    private val signOutMarker: SignOutMarkerStore,
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
            signOutMarker.clearAfterSignIn()
            val firebaseUser = result.user
            val user = firebaseUser?.toAuthUser()
            if (user != null && firebaseUser != null) {
                profileStore.ensure(firebaseUser)
                val token = firebaseUser.getIdToken(false).await().token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo obtener la información del usuario.")
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            ApiResult.Failure("Correo o contraseña incorrectos.")
        } catch (e: FirebaseFirestoreException) {
            ApiResult.Failure("Se inició sesión, pero no se pudo preparar tu perfil. Inténtalo de nuevo.", e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure("No se pudo iniciar sesión. Revisa tus credenciales y conexión.", e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): ApiResult<AuthUser> {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank() || normalizedName.length > 200) {
            return ApiResult.Failure("El nombre debe tener entre 1 y 200 caracteres.")
        }
        var accountCreated = false
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            signOutMarker.clearAfterSignIn()
            accountCreated = true
            result.user?.sendEmailVerification()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                firebaseUser.updateProfile(userProfileChangeRequest { this.displayName = normalizedName }).await()
                profileStore.updateDisplayName(firebaseUser, normalizedName)
                val user = firebaseUser.toAuthUser().copy(displayName = normalizedName)
                val token = firebaseUser.getIdToken(false).await().token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo registrar el usuario.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            ApiResult.Failure("Este correo electrónico ya está registrado. Por favor inicia sesión.")
        } catch (e: FirebaseFirestoreException) {
            ApiResult.Failure("La cuenta se creó, pero no se pudo guardar el perfil. Inicia sesión para reintentarlo.", e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(
                if (accountCreated) "La cuenta se creó, pero no se pudo completar el perfil. Inicia sesión y edita tu nombre si hace falta."
                else e.localizedMessage ?: "Error al registrarse.",
                e,
            )
        }
    }

    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            signOutMarker.clearAfterSignIn()
            val firebaseUser = result.user
            val user = firebaseUser?.toAuthUser()
            if (user != null && firebaseUser != null) {
                profileStore.ensure(firebaseUser)
                val token = firebaseUser.getIdToken(false).await().token ?: ""
                sessionDataStore.setSession(user.uid, token)
                ApiResult.Success(user)
            } else {
                ApiResult.Failure("No se pudo iniciar sesión con Google.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            ApiResult.Failure("Este correo ya está asociado a otra cuenta.")
        } catch (e: FirebaseFirestoreException) {
            ApiResult.Failure("Se inició sesión con Google, pero no se pudo preparar tu perfil. Inténtalo de nuevo.", e)
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
        // D2-H2: the phone only links to the signed-in email account. Without a session no SMS is requested,
        // so automatic verification can never create a phone-only account either.
        val requestedUser = firebaseAuth.currentUser ?: run {
            continuation.resume(ApiResult.Failure(PHONE_SIGN_IN_REQUIRED))
            return@suspendCancellableCoroutine
        }
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (firebaseAuth.currentUser?.uid != requestedUser.uid) return
                    requestedUser.linkWithCredential(credential).addOnCompleteListener { completed ->
                        if (completed.isSuccessful) {
                            if (firebaseAuth.currentUser?.uid != completed.result?.user?.uid) return@addOnCompleteListener
                            val verifiedUser = completed.result?.user ?: return@addOnCompleteListener
                            verifiedUser.getIdToken(true).addOnCompleteListener {
                                profileStore.ensureTask(verifiedUser).addOnCompleteListener { saved ->
                                    if (continuation.isActive) {
                                        continuation.resume(if (saved.isSuccessful) ApiResult.Success("")
                                            else ApiResult.Failure("No se pudo guardar tu perfil. Inténtalo de nuevo.", saved.exception))
                                    }
                                }
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
                    if (firebaseAuth.currentUser?.uid == requestedUser.uid) {
                        resendToken = token
                        resendPhone = phoneNumber
                        resendUserId = requestedUser.uid
                    }
                    if (continuation.isActive) {
                        continuation.resume(ApiResult.Success(verificationId))
                    }
                }
            })
            .apply {
                if (forceResend && resendPhone == phoneNumber && resendUserId == requestedUser.uid) {
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
            // D2-H2: never sign in with the phone credential; without a session there is nothing to link to.
            val currentUser = firebaseAuth.currentUser ?: return ApiResult.Failure(PHONE_SIGN_IN_REQUIRED)
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            currentUser.linkWithCredential(credential).await()
            firebaseAuth.currentUser?.takeIf { it.uid == currentUser.uid }?.let { user ->
                profileStore.ensure(user)
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
        if (displayName.isBlank() || displayName.length > 200) {
            return ApiResult.Failure("El nombre debe tener entre 1 y 200 caracteres.")
        }
        return try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()
                profileStore.updateDisplayName(user, displayName)
                user.getIdToken(true).await()
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("No hay una sesión de usuario activa.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: FirebaseFirestoreException) {
            ApiResult.Failure("El nombre cambió en tu cuenta, pero no se guardó en el perfil. Inténtalo de nuevo.", e)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar el perfil.", e)
        }
    }

    override suspend fun signOut() {
        // Durable before anything else: if the process dies before the SDK persists signOut, startup completes it.
        firebaseAuth.currentUser?.uid?.let(signOutMarker::markSigningOut)
        notifications.beforeSignOut()
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

    internal companion object {
        const val PHONE_SIGN_IN_REQUIRED = "Inicia sesión primero para verificar tu teléfono."
    }
}
