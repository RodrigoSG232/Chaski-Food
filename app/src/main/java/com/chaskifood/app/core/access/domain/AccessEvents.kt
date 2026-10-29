package com.chaskifood.app.core.access.domain

/** Capturar al comenzar trabajo, nunca al recibir su respuesta. Incluye logout. */
data class AccessProvenance(val session: AccessSession?, val generation: Long, val revision: Long) {
    init { require(generation >= 0 && revision >= 0); require(session == null || session.generation == generation) }
}

sealed interface AccessEvent {
    val origin: AccessProvenance
    data class Identity(override val origin: AccessProvenance, val identity: AccessIdentity) : AccessEvent
    data class Facts(override val origin: AccessProvenance, val facts: AccessFacts) : AccessEvent
}

/** null significa descartar; no reemplazar el estado vigente por un error antiguo. */
class EvaluateAccessEvent(private val resolve: ResolveAccess = ResolveAccess()) {
    operator fun invoke(current: AccessProvenance, identity: AccessIdentity, event: AccessEvent): AccessResolution? {
        if (event.origin != current) return null
        val session = (identity as? AccessIdentity.Authenticated)?.session
        if (session != current.session) return null
        return when (event) {
            is AccessEvent.Facts -> resolve(identity, event.facts)
            is AccessEvent.Identity -> if (event.identity != identity) null else when (identity) {
                AccessIdentity.SignedOut -> AccessResolution.SignedOut
                is AccessIdentity.Authenticated -> AccessResolution.Loading(identity.session)
            }
        }
    }
}
