package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Puerto aislado: no hay adaptador Firebase conectado en este incremento.
 * sessionKey es identificador opaco de inicio de sesion, nunca token ni credencial.
 */
interface AccessIdentityDriver {
    fun observe(): Flow<IdentityObservation>
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
    ) : IdentityObservation {
        init { require(sessionKey.isNotBlank() && uid.isNotBlank()) }
    }
}

enum class IdentityAvailability { LOADING, READY, FAILED }
data class AccessIdentityState(
    val identity: AccessIdentity,
    val origin: AccessProvenance,
    val availability: IdentityAvailability,
)

/** Una suscripcion al driver por instancia. El propietario cancela scope al terminar.
 * No produce hechos comerciales. Capturar states.value.origin ANTES de iniciar consultas.
 * El driver futuro debe emitir en orden y descartar callbacks de identidad obsoletos.
 */
class SharedAccessIdentitySource(driver: AccessIdentityDriver, scope: CoroutineScope) {
    private data class Accumulator(val state: AccessIdentityState, val sessionKey: String?)
    private val initial = AccessIdentityState(AccessIdentity.SignedOut, AccessProvenance(null, 0, 0), IdentityAvailability.LOADING)
    val states: StateFlow<AccessIdentityState> = driver.observe()
        .catch { emit(IdentityObservation.Failed) }
        .scan(Accumulator(initial, null)) { previous, observed ->
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
            val changed = identity != old.identity || availability != old.availability
            val revision = old.origin.revision + if (changed) 1 else 0
            Accumulator(AccessIdentityState(identity,
                AccessProvenance((identity as? AccessIdentity.Authenticated)?.session, generation, revision), availability),
                if (auth != null) auth.sessionKey else if (signedOut) null else previous.sessionKey)
        }.map { it.state }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initial)
}
