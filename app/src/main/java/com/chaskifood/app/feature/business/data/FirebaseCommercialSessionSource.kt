package com.chaskifood.app.feature.business.data

import android.content.Context
import com.chaskifood.app.core.firebase.FoodApiEnvironment
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Reutiliza FirebaseAuth compartido sin listeners ni arquitectura de sesión nueva. */
@Singleton
class FirebaseCommercialSessionSource @Inject constructor(private val auth: FirebaseAuth) : CommercialSessionSource {
    override fun capture(): CommercialSession {
        if (!FoodApiEnvironment.isConfigured(auth.app.options.projectId)) throw CommercialFailure("ENVIRONMENT_UNAVAILABLE")
        val user = auth.currentUser ?: throw CommercialFailure("AUTHENTICATION_REQUIRED")
        return CommercialSession(user.uid, user.email, user, user.isEmailVerified)
    }
    override fun isCurrent(session: CommercialSession): Boolean = auth.currentUser?.let {
        it === session.identityKey && it.uid == session.uid && it.email == session.email && it.isEmailVerified == session.emailVerified
    } == true
    override suspend fun token(session: CommercialSession): String {
        if (!isCurrent(session)) throw CommercialFailure("SESSION_CHANGED")
        val token = auth.currentUser!!.getIdToken(false).await().token ?: throw CommercialFailure("AUTHENTICATION_REQUIRED")
        if (!isCurrent(session)) throw CommercialFailure("SESSION_CHANGED")
        return token
    }
}

/** Almacenamiento privado mínimo del intento administrativo; no contiene tokens. */
@Singleton
class PreferenceCommercialAttemptStore @Inject constructor(
    @ApplicationContext context: Context, private val json: Json,
) : CommercialAttemptStore {
    private val preferences = context.getSharedPreferences("commercial_pending_attempts", Context.MODE_PRIVATE)
    override fun read(uid: String): CommercialAttempt? = preferences.getString(uid, null)?.let {
        try { json.decodeFromString<CommercialAttempt>(it).also { attempt -> check(attempt.uid == uid) } }
        catch (_: Exception) { throw CommercialFailure("PENDING_OPERATION", true) }
    }
    override fun save(attempt: CommercialAttempt) {
        check(preferences.edit().putString(attempt.uid, json.encodeToString(attempt)).commit()) { "No se pudo conservar el intento." }
    }
    override fun complete(uid: String, operationId: String) {
        if (read(uid)?.command?.operationId == operationId) check(preferences.edit().remove(uid).commit()) { "No se pudo cerrar el intento." }
    }
}
