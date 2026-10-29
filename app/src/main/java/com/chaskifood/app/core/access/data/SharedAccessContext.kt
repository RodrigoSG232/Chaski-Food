package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AccessContextPhase { LOADING, SIGNED_OUT, RESOLVED, ERROR, CONFLICT, UNAVAILABLE }

data class AccessContextState(val origin: AccessProvenance, val resolution: AccessResolution,
    val serverRevision: Long? = null, val problem: AccessCheckProblem? = null,
    val phase: AccessContextPhase = AccessContextPhase.LOADING)

/** Sin consumidores visuales en 3A.4. Refresh explicito al retomar/reconectar/cambiar permisos.
 * La lease solo invalida; no hace polling ni convierte una cache en autorizacion permanente.
 */
class SharedAccessContext(private val identity: SharedAccessIdentitySource, private val facts: AccessFactsReader,
    private val scope: CoroutineScope, private val enabled: Boolean, private val validityMillis: Long = 60_000L, private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L }) {
    init { require(validityMillis > 0) }
    private val gate = Any()
    private val evaluate = EvaluateAccessEvent()
    private val mutable = MutableStateFlow(AccessContextState(identity.states.value.origin, AccessResolution.SignedOut,
        problem = if (enabled) null else AccessCheckProblem.ENVIRONMENT_UNAVAILABLE,
        phase = if (enabled) AccessContextPhase.LOADING else AccessContextPhase.UNAVAILABLE))
    val states: StateFlow<AccessContextState> = mutable.asStateFlow()
    private var ticket = 0L
    private var work: Job? = null
    private var revisionSession: AccessSession? = null
    private var latestRevision: Long? = null
    private var validUntil: Long? = null
    init { scope.launch { identity.states.collect { start(it) } } }

    private fun restricted(state: AccessIdentityState, problem: AccessCheckProblem? = null) {
        validUntil = null
        val event = if (state.availability == IdentityAvailability.READY && state.identity == AccessIdentity.SignedOut)
            AccessEvent.Identity(state.origin, state.identity)
        else AccessEvent.Facts(state.origin, if (problem == null) AccessFacts.Loading else AccessFacts.Unverifiable)
        identity.publishIfCurrent(state, requireCredential = false) {
            evaluate(state.origin, state.identity, event)?.let {
                val phase = when {
                    problem == AccessCheckProblem.ENVIRONMENT_UNAVAILABLE -> AccessContextPhase.UNAVAILABLE
                    problem != null || state.availability == IdentityAvailability.FAILED -> AccessContextPhase.ERROR
                    state.availability == IdentityAvailability.LOADING -> AccessContextPhase.LOADING
                    state.identity == AccessIdentity.SignedOut -> AccessContextPhase.SIGNED_OUT
                    else -> AccessContextPhase.LOADING
                }
                mutable.value = AccessContextState(state.origin, it, problem = problem, phase = phase)
            }
        }
    }
    private fun start(state: AccessIdentityState) = synchronized(gate) {
        work?.cancel(); val request = ++ticket
        if (!enabled) { restricted(state, AccessCheckProblem.ENVIRONMENT_UNAVAILABLE); return@synchronized }
        restricted(state, if (state.availability == IdentityAvailability.FAILED) AccessCheckProblem.UNAVAILABLE else null)
        if (state.availability != IdentityAvailability.READY || state.identity !is AccessIdentity.Authenticated) return@synchronized
        work = scope.launch {
            val result = try { withTimeoutOrNull(12_000) { facts.read(state) } ?: AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
            val accepted = synchronized(gate) {
                if (ticket != request) false else identity.publishIfCurrent(state) {
                    val session = (state.identity as AccessIdentity.Authenticated).session
                    val staleRevision = result.revision != null && revisionSession == session && latestRevision != null && result.revision < latestRevision!!
                    val checked = if (staleRevision) AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.INVALID_RESPONSE) else result
                    val resolution = evaluate(state.origin, state.identity, AccessEvent.Facts(state.origin, checked.facts))
                    if (resolution != null) {
                        if (checked.revision != null) { revisionSession = session; latestRevision = checked.revision }
                        validUntil = if (resolution is AccessResolution.Resolved) monotonicMillis() + validityMillis else null
                        mutable.value = AccessContextState(state.origin, resolution, checked.revision, checked.problem, when (resolution) {
                            is AccessResolution.Resolved -> AccessContextPhase.RESOLVED
                            is AccessResolution.Conflict -> AccessContextPhase.CONFLICT
                            is AccessResolution.Error -> AccessContextPhase.ERROR
                            is AccessResolution.Loading -> AccessContextPhase.LOADING
                            AccessResolution.SignedOut -> AccessContextPhase.SIGNED_OUT
                        })
                    }
                }
            }
            if (accepted && result.facts is AccessFacts.Checked) {
                // delay usa reloj monotono de corrutinas, nunca fecha de pared ni datos persistidos.
                delay(validityMillis)
                synchronized(gate) { if (ticket == request) restricted(state, AccessCheckProblem.EXPIRED) }
            }
        }
    }
    /** Invalida antes del refresh suspendido; otra respuesta no puede restaurar permisos viejos. */
    fun invalidate(problem: AccessCheckProblem? = null) = synchronized(gate) {
        work?.cancel(); ++ticket; restricted(identity.states.value, if (enabled) problem else AccessCheckProblem.ENVIRONMENT_UNAVAILABLE)
    }
    suspend fun refresh() {
        synchronized(gate) { work?.cancel(); ++ticket; restricted(identity.states.value,
            if (enabled) null else AccessCheckProblem.ENVIRONMENT_UNAVAILABLE) }
        if (enabled) identity.refresh()
    }
    /** También se comprueba al ejecutar acciones: un dispositivo suspendido no prolonga la lease. */
    fun currentState(): AccessContextState = synchronized(gate) {
        val state = identity.states.value
        if (mutable.value.origin != state.origin) restricted(state)
        if (mutable.value.phase == AccessContextPhase.RESOLVED && !identity.publishIfCurrent(state) {}) restricted(state, AccessCheckProblem.UNAVAILABLE)
        if (validUntil?.let { monotonicMillis() >= it } == true) restricted(state, AccessCheckProblem.EXPIRED)
        mutable.value
    }
    /** Sin suspensión entre validar la identidad/lease y despachar la acción de presentación. */
    fun runIfCurrent(action: (AccessContextState) -> Unit): Boolean = synchronized(gate) {
        val expected = identity.states.value
        val snapshot = currentState()
        if (snapshot.origin != expected.origin) false
        else identity.publishIfCurrent(expected) { action(snapshot) }
    }
}
