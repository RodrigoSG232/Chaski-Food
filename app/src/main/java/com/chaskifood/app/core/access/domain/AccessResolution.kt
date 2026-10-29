package com.chaskifood.app.core.access.domain

import java.util.Collections

enum class CommercialExperience { CLIENT, OWNER, MANAGER }
enum class AccessCapability {
    START_PURCHASE, REQUEST_BUSINESS, READ_PERSONAL_PURCHASES,
    READ_OWN_BUSINESS_STATUS, MANAGE_BUSINESS, LIST_OWN_STORES, MANAGE_OWN_STORES,
    MANAGE_MANAGERS, OPERATE_HOURS_AND_OFFERS, READ_STORE_ORDERS,
    // Representadas pero no concedidas: politica de acciones HU19-HU21 pendiente.
    ACCEPT_STORE_ORDER, REJECT_STORE_ORDER, PREPARE_STORE_ORDER,
}

sealed interface AccessScope {
    data class Account(val uid: String) : AccessScope
    data class Business(val businessId: String) : AccessScope
    data class Store(val businessId: String, val storeId: String) : AccessScope
}

data class AccessGrant(val capability: AccessCapability, val scope: AccessScope)

/** Copia defensiva: ni las entradas ni el consumidor pueden mutar los permisos resueltos. */
class AccessCapabilities(grants: Collection<AccessGrant>) {
    val grants: Set<AccessGrant> = Collections.unmodifiableSet(grants.toSet())
    override fun equals(other: Any?): Boolean = other is AccessCapabilities && grants == other.grants
    override fun hashCode(): Int = grants.hashCode()
    fun allows(capability: AccessCapability, scope: AccessScope): Boolean =
        AccessGrant(capability, scope) in grants
}

enum class AccessProblem { FACTS_UNAVAILABLE, FACTS_UNVERIFIABLE, STALE_SESSION, INVALID_FACTS }

sealed interface AccessResolution {
    data object SignedOut : AccessResolution
    data class Loading(val session: AccessSession) : AccessResolution
    data class Error(val session: AccessSession, val problem: AccessProblem) : AccessResolution
    data class Conflict(val session: AccessSession, val capabilities: AccessCapabilities) : AccessResolution
    data class Resolved(
        val identity: AccessIdentity.Authenticated,
        val experience: CommercialExperience,
        val ownership: AccessOwnership?,
        val capabilities: AccessCapabilities,
    ) : AccessResolution
}
