package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AccessContextPhase { LOADING, SIGNED_OUT, RESOLVED, ERROR, CONFLICT, UNAVAILABLE }

data class AccessContextState(val origin: AccessProvenance, val resolution: AccessResolution,
    val serverRevision: Long? = null, val problem: AccessCheckProblem? = null,
    val phase: AccessContextPhase = AccessContextPhase.LOADING)

/** Refresh explicito al retomar/reconectar/cambiar permisos. Una lease vigente se renueva una vez
 * a los 5/6 de su validez (50 s de 60) sin interrumpir la UI; la renovacion nunca extiende la lease anterior:
 * si falla por red, la lease caduca a su hora y no se reintenta hasta un refresh explicito.
 */
class SharedAccessContext(private val identity: SharedAccessIdentitySource, private val facts: AccessFactsReader,
    private val scope: CoroutineScope, private val enabled: Boolean, private val validityMillis: Long = 60_000L, private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L }) {
    init { require(validityMillis > 0) }
    private val renewAfterMillis = validityMillis * 5 / 6
    private companion object { const val READ_TIMEOUT_MILLIS = 12_000L }
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
        work = check(state, request, silent = false)
    }
    /** Con [silent] la primera consulta es una renovacion inmediata: no retira la lease vigente mientras espera. */
    private fun check(state: AccessIdentityState, request: Long, silent: Boolean): Job = scope.launch {
        if (!silent && !publish(state, request, read(state, READ_TIMEOUT_MILLIS))) return@launch
        var renewNow = silent
        // Un solo trabajo por ticket: logout, cambio de cuenta, segundo plano o refresh cancelan la renovacion.
        // Sin lease vigente (error, conflicto o revocacion publicados) no hay nada que renovar ni que caducar.
        while (true) {
            if (synchronized(gate) { ticket != request || validUntil == null }) return@launch
            if (!renewNow) delay(renewAfterMillis)
            renewNow = false
            val leaseLeft = synchronized(gate) { validUntil?.minus(monotonicMillis()) } ?: return@launch
            if (leaseLeft <= 0) break
            val renewed = read(state, minOf(leaseLeft, READ_TIMEOUT_MILLIS))
            if (renewed.facts is AccessFacts.Failed) {
                // Fallo de red: se conserva la lease vigente hasta su caducidad, sin reintentos automaticos.
                delay((synchronized(gate) { validUntil?.minus(monotonicMillis()) } ?: 0L).coerceAtLeast(0L))
                break
            }
            if (!publish(state, request, renewed)) return@launch
        }
        synchronized(gate) { if (ticket == request) restricted(state, AccessCheckProblem.EXPIRED) }
    }
    private suspend fun read(state: AccessIdentityState, timeoutMillis: Long): AccessFactsResult =
        try { withTimeoutOrNull(timeoutMillis) { facts.read(state) } ?: AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
    /** Publica solo si el ticket y la credencial siguen siendo los de la consulta; devuelve false si se descarto. */
    private fun publish(state: AccessIdentityState, request: Long, result: AccessFactsResult): Boolean =
            synchronized(gate) {
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
    /** Invalida antes del refresh suspendido; otra respuesta no puede restaurar permisos viejos. */
    fun invalidate(problem: AccessCheckProblem? = null) = synchronized(gate) {
        work?.cancel(); ++ticket; restricted(identity.states.value, if (enabled) problem else AccessCheckProblem.ENVIRONMENT_UNAVAILABLE)
    }
    suspend fun refresh() {
        synchronized(gate) { work?.cancel(); ++ticket; restricted(identity.states.value,
            if (enabled) null else AccessCheckProblem.ENVIRONMENT_UNAVAILABLE) }
        if (enabled) identity.refresh()
    }
    /** Para la navegacion: con una lease vigente de la sesion actual consulta sin retirarla (la consulta en curso
     * se reemplaza, nunca hay dos) y devuelve true. Sin lease vigente devuelve false: el llamador debe usar [refresh].
     */
    fun revalidate(): Boolean = synchronized(gate) {
        val state = identity.states.value
        val leased = enabled && mutable.value.phase == AccessContextPhase.RESOLVED && mutable.value.origin == state.origin &&
            state.availability == IdentityAvailability.READY && state.identity is AccessIdentity.Authenticated &&
            validUntil?.let { monotonicMillis() < it } == true
        if (leased) { work?.cancel(); work = check(state, ++ticket, silent = true) }
        leased
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
