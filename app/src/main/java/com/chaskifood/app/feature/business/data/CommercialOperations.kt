package com.chaskifood.app.feature.business.data

import com.chaskifood.app.feature.business.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Identidad capturada; identityKey solo vive en memoria y nunca es un token. */
data class CommercialSession(val uid: String, val email: String?, val identityKey: Any, val emailVerified: Boolean = false)
interface CommercialSessionSource {
    fun capture(): CommercialSession
    fun isCurrent(session: CommercialSession): Boolean
    suspend fun token(session: CommercialSession): String
}
@Serializable
data class CommercialAttempt(val uid: String, val email: String?, val mutation: CommercialMutation, val command: CommercialCommand)
interface CommercialAttemptStore {
    fun read(uid: String): CommercialAttempt?
    fun save(attempt: CommercialAttempt)
    fun complete(uid: String, operationId: String)
}
class CommercialFailure(val code: String, val uncertain: Boolean = false) : Exception(when (code) {
    "AUTHENTICATION_REQUIRED", "SESSION_CHANGED" -> "La sesión cambió. Vuelve a iniciar sesión y comprobar la operación."
    "AUTHORIZATION_CONFLICT", "MANAGER_CANNOT_REGISTER" -> "La cuenta tiene permisos comerciales incompatibles con esta operación."
    "STALE_REVISION" -> "Los permisos cambiaron. Actualiza los datos y vuelve a intentarlo."
    "PREPARATION_REQUIRED", "RECONCILIATION_REQUIRED", "INDEX_INCONSISTENT", "BINDING_OWNED_BY_ANOTHER_UID" -> "La cuenta o asignación requiere preparación o reconciliación autorizada."
    "LOCAL_ONLY" -> "El coordinador comercial está disponible únicamente en el laboratorio LOCAL. No se realizaron escrituras directas."
    "PENDING_OPERATION" -> "Hay una operación anterior sin confirmar. Reintenta sus mismos datos antes de iniciar otra."
    "EMAIL_VERIFICATION_REQUIRED" -> "Verifica tu correo para acceder a los locales asignados."
    "FORBIDDEN" -> "No tienes permisos para realizar esta operación."
    else -> if (uncertain) "No se pudo confirmar el resultado. Conservamos el intento; reintenta los mismos datos." else "No se pudo completar la operación comercial ($code)."
})

/** Un intento pendiente por UID, sin colas ni reintentos automáticos. */
@Singleton
class CommercialOperations @Inject constructor(
    private val api: CommercialApi,
    private val sessions: CommercialSessionSource,
    private val attempts: CommercialAttemptStore,
    private val json: Json,
) {
    private val mutex = Mutex()
    suspend fun execute(mutation: CommercialMutation, expectedUid: String, expectedIdentityKey: Any? = null): CommercialResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = sessions.capture()
            if (session.uid != expectedUid || (expectedIdentityKey != null && session.identityKey !== expectedIdentityKey)) throw CommercialFailure("SESSION_CHANGED")
            val pending = attempts.read(session.uid)
            if (pending != null && (pending.email != session.email || pending.mutation != mutation)) throw CommercialFailure("PENDING_OPERATION", true)
            val attempt = pending ?: run {
                val token = sessions.token(session)
                val revision = when (mutation.action) {
                    "REGISTER", "RESUBMIT", "BIND" -> value(api.ownRevision("Bearer $token")).revision
                    else -> value(api.revision("Bearer $token", CommercialRevisionRequest(checkNotNull(mutation.businessId), mutation.email))).revision
                }
                val previous = mutation.previousEmail?.let {
                    value(api.revision("Bearer $token", CommercialRevisionRequest(checkNotNull(mutation.businessId), it))).revision
                }
                if (!sessions.isCurrent(session)) throw CommercialFailure("SESSION_CHANGED")
                if (revision < 0 || (previous != null && previous < 0)) throw CommercialFailure("INVALID_RESPONSE")
                CommercialAttempt(session.uid, session.email, mutation, CommercialCommand(mutation.action,
                    UUID.randomUUID().toString(), revision, mutation.businessId, mutation.email, mutation.storeIds,
                    mutation.fullName, mutation.phone, mutation.business, mutation.previousEmail, previous)).also(attempts::save)
            }
            try {
                val token = sessions.token(session)
                val response = withTimeoutOrNull(12_000) { api.execute("Bearer $token", attempt.command) }
                    ?: throw CommercialFailure("RESULT_UNCERTAIN", true)
                if (!sessions.isCurrent(session)) throw CommercialFailure("SESSION_CHANGED", true)
                val result = value(response)
                if (result.action != mutation.action || result.revision < 0) throw CommercialFailure("INVALID_RESPONSE", true)
                attempts.complete(session.uid, attempt.command.operationId)
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: CommercialFailure) {
                if (!failure.uncertain && failure.code !in setOf("AUTHENTICATION_REQUIRED", "OPERATION_ID_REUSED", "SESSION_CHANGED")) {
                    attempts.complete(session.uid, attempt.command.operationId)
                }
                throw failure
            } catch (_: Exception) { throw CommercialFailure("RESULT_UNCERTAIN", true) }
        }
    }
    suspend fun ensureBinding(expectedUid: String) {
        val session = sessions.capture()
        if (session.uid != expectedUid) throw CommercialFailure("SESSION_CHANGED")
        val response = api.facts("Bearer ${sessions.token(session)}")
        if (!sessions.isCurrent(session)) throw CommercialFailure("SESSION_CHANGED")
        if (response.isSuccessful) {
            if (response.body()?.uid != expectedUid) throw CommercialFailure("INVALID_RESPONSE")
            return
        }
        val code = try { json.decodeFromString<CommercialError>(response.errorBody()?.string().orEmpty()).error } catch (_: Exception) { "SERVICE_UNAVAILABLE" }
        if (code != "RECONCILIATION_REQUIRED") throw CommercialFailure(code)
        execute(CommercialMutation("BIND"), expectedUid)
    }

    private fun <T> value(response: Response<T>): T {
        if (response.isSuccessful) return response.body() ?: throw CommercialFailure("INVALID_RESPONSE", true)
        val code = try { json.decodeFromString<CommercialError>(response.errorBody()?.string().orEmpty()).error } catch (_: Exception) { null }
        throw CommercialFailure(code ?: when (response.code()) { 401 -> "AUTHENTICATION_REQUIRED"; 403 -> "FORBIDDEN"; else -> "SERVICE_UNAVAILABLE" }, response.code() >= 500 || response.code() !in setOf(400, 401, 403, 409))
    }
}
