package com.chaskifood.app.core.notifications

import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal enum class RegistrationOutcome { REGISTERED, NOT_ELIGIBLE, SESSION_CHANGED, FAILED }

/** Binds this installation's push token to a UID only while that exact session is current.
 * Every suspension is followed by a session check; register and unregister never overlap, and once a session
 * starts signing out no late registration can bind it again. Tokens are never logged. */
internal class DeviceRegistrar(private val api: DeviceApi, private val sessions: CartSessionSource, private val tokens: BuyerTokenSource,
    private val installationId: String, private val nextBindingVersion: suspend () -> Long, private val pushToken: suspend () -> String) {
    private val gate = Mutex()
    @Volatile private var signingOut: CartSession? = null
    private fun current(session: CartSession) = session != signingOut && sessions.isCurrent(session)

    suspend fun register(session: CartSession): RegistrationOutcome = gate.withLock {
        val uid = session.uid ?: return@withLock RegistrationOutcome.SESSION_CHANGED
        try {
            withTimeoutOrNull(10_000) {
                if (!current(session)) return@withTimeoutOrNull RegistrationOutcome.SESSION_CHANGED
                val token = pushToken()
                if (!current(session)) return@withTimeoutOrNull RegistrationOutcome.SESSION_CHANGED
                val version = nextBindingVersion()
                val bearer = tokens.token(uid, session.generation)
                if (!current(session)) return@withTimeoutOrNull RegistrationOutcome.SESSION_CHANGED
                val response = api.put(installationId, "Bearer $bearer", DeviceRegistration(token, version))
                when {
                    response.isSuccessful -> RegistrationOutcome.REGISTERED
                    response.code() == 403 -> RegistrationOutcome.NOT_ELIGIBLE
                    else -> RegistrationOutcome.FAILED
                }
            } ?: RegistrationOutcome.FAILED
        } catch (e: CancellationException) { throw e } catch (_: Exception) { RegistrationOutcome.FAILED }
    }

    /** Best effort: an offline failure never blocks logout; a later binding of this installation supersedes it. */
    suspend fun unregister(session: CartSession, signOut: Boolean) {
        if (signOut) signingOut = session
        gate.withLock {
            val uid = session.uid ?: return@withLock
            try {
                withTimeoutOrNull(5_000) { api.remove(installationId, "Bearer ${tokens.token(uid, session.generation)}", nextBindingVersion()) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }
}
