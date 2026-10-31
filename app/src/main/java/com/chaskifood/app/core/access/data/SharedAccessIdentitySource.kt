package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

/** Puerto de identidad compartida; Firebase es un adaptador, no una dependencia de Domain.
 * sessionKey es identificador opaco de inicio de sesion, nunca token ni credencial.
 */
interface AccessIdentityDriver {
    fun observe(): Flow<IdentityObservation>
    suspend fun refresh() {}
    fun runIfCurrent(state: AccessIdentityState, publish: () -> Unit): Boolean { publish(); return true }
}

sealed interface IdentityObservation {
    data object Loading : IdentityObservation
    data object Failed : IdentityObservation
    data object SignedOut : IdentityObservation
    data class Authenticated(
        val sessionKey: String,
        val uid: String,
        val email: String?,
        val verification: EmailVerification,
        val isAdmin: Boolean = false,
        val credentialRevision: Long = 0,
    ) : IdentityObservation {
        init { require(sessionKey.isNotBlank() && uid.isNotBlank()) }
    }
}

enum class IdentityAvailability { LOADING, READY, FAILED }
data class AccessIdentityState(
    val identity: AccessIdentity,
    val origin: AccessProvenance,
    val availability: IdentityAvailability,
    val sessionKey: String? = null,
    val credentialRevision: Long = 0,
)

/** Una suscripcion al driver por instancia. El propietario cancela scope al terminar.
 * No produce hechos comerciales. Capturar states.value.origin ANTES de iniciar consultas.
 * El driver debe emitir en orden y descartar callbacks de identidad obsoletos.
 */
class SharedAccessIdentitySource(driver: AccessIdentityDriver, scope: CoroutineScope) {
    private data class Accumulator(val state: AccessIdentityState, val sessionKey: String?)
    private val initial = AccessIdentityState(AccessIdentity.SignedOut, AccessProvenance(null, 0, 0), IdentityAvailability.LOADING)
    private val gate = Any()
    private val mutable = MutableStateFlow(initial)
    val states: StateFlow<AccessIdentityState> = mutable.asStateFlow()
    private var accumulator = Accumulator(initial, null)
    private val identityDriver = driver
    init { scope.launch {
        driver.observe().catch { emit(IdentityObservation.Failed) }.collect { observed -> synchronized(gate) {
            val previous = accumulator
            val old = previous.state
            val auth = observed as? IdentityObservation.Authenticated
            val signedOut = observed == IdentityObservation.SignedOut
            val changedSession = if (auth != null) previous.sessionKey != auth.sessionKey ||
                (old.identity as? AccessIdentity.Authenticated)?.session?.uid != auth.uid
                else signedOut && old.identity is AccessIdentity.Authenticated
            val generation = old.origin.generation + if (changedSession) 1 else 0
            val identity = when {
                auth != null -> AccessIdentity.Authenticated(AccessSession(auth.uid, generation), auth.email, auth.verification, auth.isAdmin)
                signedOut -> AccessIdentity.SignedOut
                else -> old.identity
            }
            val availability = when (observed) {
                IdentityObservation.Loading -> IdentityAvailability.LOADING
                IdentityObservation.Failed -> IdentityAvailability.FAILED
                else -> IdentityAvailability.READY
            }
            val key = if (auth != null) auth.sessionKey else if (signedOut) null else previous.sessionKey
            val credentialRevision = auth?.credentialRevision ?: old.credentialRevision
            val revision = old.origin.revision + if (identity != old.identity || availability != old.availability || credentialRevision != old.credentialRevision) 1 else 0
            val state = AccessIdentityState(identity, AccessProvenance((identity as? AccessIdentity.Authenticated)?.session,
                generation, revision), availability, key, credentialRevision)
            accumulator = Accumulator(state, key)
            mutable.value = state
        } }
    } }

    /** Comprobacion y publicacion sin suspension bajo la misma exclusion que actualiza identidad. */
    fun publishIfCurrent(expected: AccessIdentityState, requireCredential: Boolean = true, publish: () -> Unit): Boolean = synchronized(gate) {
        if (mutable.value != expected) false
        else if (requireCredential) identityDriver.runIfCurrent(expected, publish)
        else { publish(); true }
    }
    suspend fun refresh() = identityDriver.refresh()
}
