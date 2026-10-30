package com.chaskifood.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AccessNavigationViewModel @Inject constructor(
    private val context: SharedAccessContext,
    private val identity: SharedAccessIdentitySource,
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
