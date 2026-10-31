package com.chaskifood.app.core.notifications

import com.chaskifood.app.core.access.data.AccessContextPhase
import com.chaskifood.app.core.access.data.AccessContextState
import com.chaskifood.app.core.access.domain.*
import org.junit.Assert.*
import org.junit.Test

class NoticePolicyTest {
    private val session = AccessSession("manager", 1)
    private val identity = AccessIdentity.Authenticated(session, "manager@example.test", EmailVerification.VERIFIED)
    private val origin = AccessProvenance(session, 1, 1)
    private val notice = NewOrderNotice("new_order_${"a".repeat(64)}", "a".repeat(64), "business", "store", "manager")
    private fun state(phase: AccessContextPhase, vararg grants: AccessGrant, who: AccessIdentity.Authenticated = identity): AccessContextState {
        val resolution = when (phase) {
            AccessContextPhase.RESOLVED -> AccessResolution.Resolved(who, if (grants.isEmpty()) CommercialExperience.CLIENT else CommercialExperience.MANAGER,
                null, AccessCapabilities(grants.toList()))
            AccessContextPhase.LOADING -> AccessResolution.Loading(session)
            AccessContextPhase.CONFLICT -> AccessResolution.Conflict(session, AccessCapabilities(grants.toList()))
            AccessContextPhase.SIGNED_OUT -> AccessResolution.SignedOut
            else -> AccessResolution.Error(session, AccessProblem.FACTS_UNAVAILABLE)
        }
        return AccessContextState(origin, resolution, phase = phase)
    }
    private val reads = AccessGrant(AccessCapability.READ_STORE_ORDERS, AccessScope.Store("business", "store"))
    private val readsOther = AccessGrant(AccessCapability.READ_STORE_ORDERS, AccessScope.Store("business", "other"))

    @Test fun onlyResolvedStoreManagersRegisterTheirOwnUid() {
        assertTrue(NoticePolicy.canRegister("manager", state(AccessContextPhase.RESOLVED, reads)))
        assertTrue(NoticePolicy.canRegister("manager", state(AccessContextPhase.RESOLVED, readsOther)))
        assertFalse(NoticePolicy.canRegister("manager", state(AccessContextPhase.RESOLVED)))
        assertFalse(NoticePolicy.canRegister("manager", state(AccessContextPhase.RESOLVED,
            AccessGrant(AccessCapability.MANAGE_BUSINESS, AccessScope.Business("business")))))
        assertFalse(NoticePolicy.canRegister("other", state(AccessContextPhase.RESOLVED, reads)))
        assertFalse(NoticePolicy.canRegister(null, state(AccessContextPhase.RESOLVED, reads)))
        for (phase in listOf(AccessContextPhase.LOADING, AccessContextPhase.ERROR, AccessContextPhase.CONFLICT,
            AccessContextPhase.SIGNED_OUT, AccessContextPhase.UNAVAILABLE)) assertFalse(NoticePolicy.canRegister("manager", state(phase, reads)))
    }

    @Test fun visibleHintRequiresMatchingLocalUidAndNoCurrentDenial() {
        assertTrue(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.RESOLVED, reads)))
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.RESOLVED, readsOther)))
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.RESOLVED)))
        assertFalse(NoticePolicy.canShow("other", notice, state(AccessContextPhase.RESOLVED, reads)))
        assertFalse(NoticePolicy.canShow(null, notice, state(AccessContextPhase.LOADING)))
        val foreign = AccessIdentity.Authenticated(AccessSession("foreign", 1), "f@example.test", EmailVerification.VERIFIED)
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.RESOLVED, reads, who = foreign)))
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.CONFLICT, reads)))
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.SIGNED_OUT)))
        assertFalse(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.UNAVAILABLE)))
    }

    @Test fun coldProcessWithoutFreshFactsShowsOnlyTheGenericHintForTheSameUid() {
        // A recreated process has the persisted Auth UID but no access lease yet; opening the hint reauthorizes.
        assertTrue(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.LOADING)))
        assertTrue(NoticePolicy.canShow("manager", notice, state(AccessContextPhase.ERROR)))
        assertFalse(NoticePolicy.canShow("other", notice, state(AccessContextPhase.LOADING)))
    }
}
