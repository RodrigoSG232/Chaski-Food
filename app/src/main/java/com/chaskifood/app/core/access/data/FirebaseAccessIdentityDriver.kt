package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Token solo en Data; nunca se conserva en Identity, Domain o el estado publico. */
interface AccessCredentialSource { suspend fun token(expected: AccessIdentityState): String }

/** Una observacion por SharedAccessIdentitySource; sin listeners por pantalla. */
class FirebaseAccessIdentityDriver(private val auth: FirebaseAuth) : AccessIdentityDriver, AccessCredentialSource {
    private val gate = Any()
    private var lastUser: FirebaseUser? = null
    private var key: String? = null
    private var epoch = 0L
    private val refreshes = MutableStateFlow(0L)
    private data class Snapshot(val user: FirebaseUser?, val key: String?, val epoch: Long, val reload: Boolean)
    private fun current(snapshot: Snapshot) = epoch == snapshot.epoch && auth.currentUser === snapshot.user
    private fun matches(state: AccessIdentityState): Boolean {
        val identity = state.identity as? AccessIdentity.Authenticated ?: return auth.currentUser == null
        val user = auth.currentUser ?: return false
        return state.credentialRevision == epoch && user === lastUser && state.sessionKey == key && user.uid == identity.session.uid &&
            user.email == identity.email && (if (user.isEmailVerified) EmailVerification.VERIFIED else EmailVerification.UNVERIFIED) == identity.verification
    }
    override fun runIfCurrent(state: AccessIdentityState, publish: () -> Unit): Boolean = synchronized(gate) {
        if (!matches(state)) false else { publish(); true }
    }
    override suspend fun token(expected: AccessIdentityState): String {
        val user = synchronized(gate) { check(matches(expected)) { "SESSION_CHANGED" }; checkNotNull(auth.currentUser) }
        val token = user.getIdToken(false).await().token ?: error("AUTHENTICATION_REQUIRED")
        synchronized(gate) { check(matches(expected)) { "SESSION_CHANGED" } }
        return token
    }
    override suspend fun refresh() { refreshes.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<IdentityObservation> = channelFlow {
        callbackFlow {
            fun offer(reload: Boolean) = synchronized(gate) {
                val user = auth.currentUser
                if (user !== lastUser) { lastUser = user; key = user?.let { UUID.randomUUID().toString() } }
                epoch++
                trySend(Snapshot(user, key, epoch, reload))
            }
            val listener = FirebaseAuth.IdTokenListener { offer(false) }
            auth.addIdTokenListener(listener)
            val refreshJob = launch { refreshes.collect { if (it > 0) offer(true) } }
            awaitClose { refreshJob.cancel(); auth.removeIdTokenListener(listener) }
        }.buffer(Channel.CONFLATED).collectLatest { snapshot ->
            val user = snapshot.user
            fun publish(observation: IdentityObservation) = synchronized(gate) {
                if (current(snapshot)) trySend(observation)
            }
            if (user == null) publish(IdentityObservation.SignedOut)
            else {
                publish(IdentityObservation.Loading)
                try {
                    val observation = kotlinx.coroutines.withTimeoutOrNull(12_000) {
                        if (snapshot.reload) user.reload().await()
                        val claims = user.getIdToken(snapshot.reload).await().claims
                        IdentityObservation.Authenticated(checkNotNull(snapshot.key), user.uid, user.email,
                            if (user.isEmailVerified) EmailVerification.VERIFIED else EmailVerification.UNVERIFIED,
                            claims["admin"] == true, snapshot.epoch)
                    }
                    publish(observation ?: IdentityObservation.Failed)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { publish(IdentityObservation.Failed) }
            }
        }
    }.buffer(Channel.CONFLATED)
}
