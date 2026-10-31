package com.chaskifood.app.core.access.domain

import java.util.Locale

/** Evaluacion determinista, sin I/O. No sustituye autorizacion servidor ni cancela trabajos. */
class ResolveAccess {
    operator fun invoke(identity: AccessIdentity, facts: AccessFacts): AccessResolution {
        if (identity !is AccessIdentity.Authenticated) return AccessResolution.SignedOut
        val session = identity.session
        fun error(problem: AccessProblem) = AccessResolution.Error(session, problem)
        when (facts) {
            AccessFacts.Loading -> return AccessResolution.Loading(session)
            AccessFacts.Failed -> return error(AccessProblem.FACTS_UNAVAILABLE)
            AccessFacts.Unverifiable -> return error(AccessProblem.FACTS_UNVERIFIABLE)
            is AccessFacts.ConfirmedConflict -> return if (facts.session != session) error(AccessProblem.STALE_SESSION)
                else AccessResolution.Conflict(session, AccessCapabilities(listOf(
                    AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account(session.uid)))))
            is AccessFacts.Checked -> Unit
        }
        if (facts.session != session) return error(AccessProblem.STALE_SESSION)
        val owner = facts.ownership
        if (owner != null && (owner.ownerUid != session.uid || owner.businessId.isBlank())) {
            return error(AccessProblem.INVALID_FACTS)
        }
        if (facts.assignments.any { it.businessId.isBlank() || it.storeId.isBlank() || it.authorizedEmail.isBlank() } ||
            facts.stores.any { it.businessId.isBlank() || it.storeId.isBlank() } ||
            facts.stores.map { it.storeId }.distinct().size != facts.stores.size) {
            return error(AccessProblem.INVALID_FACTS)
        }
        val grants = mutableSetOf(AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account(session.uid)))
        val isOwner = owner?.status in setOf(AccessBusinessStatus.APPROVED, AccessBusinessStatus.SUSPENDED)
        // Vinculacion VERIFIED afirma responsabilidad del UID; el correo puede requerir
        // reconciliacion para operar, pero no borra un conflicto autoritativo ya probado.
        val applicable = facts.assignments.filter { it.binding == AccessBinding.VERIFIED && it.boundUid == session.uid }
        if (isOwner && applicable.isNotEmpty()) {
            return AccessResolution.Conflict(session, AccessCapabilities(grants))
        }
        if (facts.exclusivity != AccessExclusivity.VERIFIED) return error(AccessProblem.FACTS_UNVERIFIABLE)
        val uncertain = facts.assignments.any {
            it.binding == AccessBinding.RECONCILIATION_REQUIRED || it.binding == AccessBinding.UNVERIFIABLE ||
                (it.binding == AccessBinding.VERIFIED && it.boundUid.isNullOrBlank())
        }
        if (uncertain || (!isOwner && facts.assignments.any { it.binding == AccessBinding.PENDING })) {
            return error(AccessProblem.FACTS_UNVERIFIABLE)
        }
        if (applicable.isNotEmpty() && (identity.verification != EmailVerification.VERIFIED ||
                identity.email.isNullOrBlank() || applicable.any {
                    normalize(it.authorizedEmail) != normalize(identity.email)
                })) return error(AccessProblem.FACTS_UNVERIFIABLE)
        val experience = when {
            isOwner -> CommercialExperience.OWNER
            applicable.isNotEmpty() -> CommercialExperience.MANAGER
            else -> CommercialExperience.CLIENT
        }
        when (experience) {
            CommercialExperience.CLIENT -> {
                grants += AccessGrant(AccessCapability.START_PURCHASE, AccessScope.Account(session.uid))
                // Pendiente/observada/rechazada ya tienen solicitud: no crear otra.
                if (owner == null) grants += AccessGrant(AccessCapability.REQUEST_BUSINESS, AccessScope.Account(session.uid))
                else {
                    val scope = AccessScope.Business(owner.businessId)
                    grants += AccessGrant(AccessCapability.READ_OWN_BUSINESS_STATUS, scope)
                    if (owner.status in setOf(AccessBusinessStatus.OBSERVED, AccessBusinessStatus.REJECTED))
                        grants += AccessGrant(AccessCapability.RESUBMIT_BUSINESS, scope)
                }
            }
            CommercialExperience.OWNER -> {
                checkNotNull(owner)
                val scope = AccessScope.Business(owner.businessId)
                grants += AccessGrant(AccessCapability.READ_OWN_BUSINESS_STATUS, scope)
                if (owner.status == AccessBusinessStatus.APPROVED) {
                    listOf(AccessCapability.MANAGE_BUSINESS, AccessCapability.LIST_OWN_STORES,
                        AccessCapability.MANAGE_OWN_STORES, AccessCapability.MANAGE_MANAGERS).forEach {
                        grants += AccessGrant(it, scope)
                    }
                    facts.stores.filter { it.businessId == owner.businessId && it.businessStatus == owner.status &&
                        it.status == AccessStoreStatus.ACTIVE }.forEach {
                        grants += AccessGrant(AccessCapability.OPERATE_HOURS_AND_OFFERS, AccessScope.Store(it.businessId, it.storeId))
                    }
                }
            }
            CommercialExperience.MANAGER -> applicable.forEach { assignment ->
                val scope = AccessScope.Store(assignment.businessId, assignment.storeId)
                grants += AccessGrant(AccessCapability.READ_STORE_ORDERS, scope)
                val store = facts.stores.singleOrNull { it.storeId == assignment.storeId && it.businessId == assignment.businessId }
                if (store?.businessStatus == AccessBusinessStatus.APPROVED && store.status == AccessStoreStatus.ACTIVE) {
                    grants += AccessGrant(AccessCapability.OPERATE_HOURS_AND_OFFERS, scope)
                }
            }
        }
        return AccessResolution.Resolved(identity, experience, owner, AccessCapabilities(grants))
    }

    private fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)
}
