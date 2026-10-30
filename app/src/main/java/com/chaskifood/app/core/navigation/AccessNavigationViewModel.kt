package com.chaskifood.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AccessNavigationViewModel @Inject constructor(
    private val context: SharedAccessContext,
    private val identity: SharedAccessIdentitySource,
    private val binder: PendingAssignmentBinder = PendingAssignmentBinder { error("BINDING_UNAVAILABLE") },
    private val sessionSignOut: SessionSignOut = SessionSignOut { false },
) : ViewModel() {
    val instanceId = java.util.UUID.randomUUID().toString()
    fun invalidate() {
        refreshJob?.cancel()
        if (enabled) context.invalidate()
    }
    val enabled = true // Authorization is enforced in every environment, including unavailable configuration.
    val states = combine(context.states, identity.states) { access, current ->
        if (access.origin == current.origin && (current.availability == IdentityAvailability.READY ||
            access.phase in setOf(AccessContextPhase.ERROR, AccessContextPhase.UNAVAILABLE))) access
        else access.copy(origin = current.origin, resolution = AccessResolution.SignedOut,
            problem = if (current.availability == IdentityAvailability.FAILED) AccessCheckProblem.UNAVAILABLE else access.problem,
            phase = if (current.availability == IdentityAvailability.FAILED) AccessContextPhase.ERROR else AccessContextPhase.LOADING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, context.currentState())
    private var refreshJob: Job? = null
    private var bindingJob: Job? = null
    private var bindingFor: AccessIdentity.Authenticated? = null
    /** Identidad (UID, sesion, correo y verificacion) que ya tuvo su intento automatico; un refresco no la cambia. */
    private var autoBindingFor: AccessIdentity.Authenticated? = null

    init {
        // Una invitacion PENDING hace que los hechos respondan RECONCILIATION_REQUIRED hasta vincularla. Se intenta
        // una sola vez por identidad con correo verificado; verificar el correo cambia la identidad y habilita otro.
        viewModelScope.launch {
            context.states.collect { state ->
                val current = identity.states.value.identity as? AccessIdentity.Authenticated
                if (bindingJob?.isActive == true && bindingFor != current) bindingJob?.cancel()
                if (current != null && needsBinding(state) && current.verification == EmailVerification.VERIFIED &&
                    autoBindingFor != current) {
                    autoBindingFor = current
                    bind(current)
                }
            }
        }
    }
    private fun needsBinding(state: AccessContextState) =
        state.phase == AccessContextPhase.ERROR && state.problem == AccessCheckProblem.RECONCILIATION_REQUIRED
    /** El backend decide si la vinculacion procede; despues se consultan hechos nuevos solo para la misma identidad. */
    private fun bind(expected: AccessIdentity.Authenticated) {
        if (bindingJob?.isActive == true) return
        bindingFor = expected
        bindingJob = viewModelScope.launch {
            try { binder.bind(expected.session.uid) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Conflicto, correo sin verificar o red: el panel restrictivo se mantiene. */ }
            if (identity.states.value.identity == expected) refresh()
        }
    }
    /** "Volver a comprobar": ante reconciliacion reintenta la vinculacion una vez por pulsacion; si no, refresca. */
    fun retry() {
        val current = identity.states.value.identity as? AccessIdentity.Authenticated
        if (current != null && needsBinding(context.currentState())) bind(current) else refresh()
    }
    /** Cierre de sesion centralizado; descarta antes cualquier vinculacion o refresco en curso de esta cuenta. */
    fun signOut(onSignedOut: (protected: Boolean) -> Unit) {
        bindingJob?.cancel(); refreshJob?.cancel()
        viewModelScope.launch { onSignedOut(sessionSignOut.signOut()) }
    }
    fun refresh(force: Boolean = true) {
        if (!enabled) return
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            val completed = kotlinx.coroutines.withTimeoutOrNull(25_000) {
                context.refresh()
                context.states.first { it.phase != AccessContextPhase.LOADING }
            }
            if (completed == null) context.invalidate(AccessCheckProblem.UNAVAILABLE)
        }
    }
    /** Revalidacion al navegar: silenciosa con lease vigente; si no la hay, refresco bloqueante normal. */
    fun revalidate() {
        if (!enabled || refreshJob?.isActive == true) return
        if (!context.revalidate()) refresh(force = false)
    }
    fun current() = context.currentState()
    fun sessionStamp(origin: AccessProvenance) = instanceId + ":" + origin.generation
    fun allows(route: String?, businessId: String? = null, storeId: String? = null, resubmit: Boolean = false): Boolean =
        AccessNavigationPolicy.allows(current().resolution, route, businessId, storeId, resubmit)
fun runIfAllowedFrom(expected: AccessProvenance, route: String?, businessId: String? = null,
        storeId: String? = null, resubmit: Boolean = false, action: () -> Unit) {
        context.runIfCurrent { state ->
            if ((expected == state.origin) &&
                AccessNavigationPolicy.allows(state.resolution, route, businessId, storeId, resubmit)) action()
        }
    }
}
