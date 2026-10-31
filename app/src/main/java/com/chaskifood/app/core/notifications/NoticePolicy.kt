package com.chaskifood.app.core.notifications

import com.chaskifood.app.core.access.data.AccessContextPhase
import com.chaskifood.app.core.access.data.AccessContextState
import com.chaskifood.app.core.access.domain.AccessCapability
import com.chaskifood.app.core.access.domain.AccessResolution
import com.chaskifood.app.core.access.domain.AccessScope

/** Pure local decisions. Neither grants commercial access: the server reauthorizes every send and the HTTP inbox every read. */
internal object NoticePolicy {
    /** Only a resolved store manager keeps a push token bound to its UID. */
    fun canRegister(uid: String?, state: AccessContextState): Boolean {
        if (uid == null || state.phase != AccessContextPhase.RESOLVED) return false
        val resolved = state.resolution as? AccessResolution.Resolved ?: return false
        return resolved.identity.session.uid == uid && resolved.capabilities.grants.any { it.capability == AccessCapability.READ_STORE_ORDERS }
    }

    /** onMessageReceived cannot wait for the network. The persisted local UID must match the recipient; a current
     * denial, conflict, sign-out or unavailable environment drops the hint; unknown facts allow only the generic hint. */
    fun canShow(sessionUid: String?, notice: NewOrderNotice, state: AccessContextState): Boolean {
        if (sessionUid == null || sessionUid != notice.recipientUid) return false
        return when (state.phase) {
            AccessContextPhase.RESOLVED -> {
                val resolved = state.resolution as? AccessResolution.Resolved ?: return false
                resolved.identity.session.uid == sessionUid &&
                    resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS, AccessScope.Store(notice.businessId, notice.storeId))
            }
            AccessContextPhase.LOADING, AccessContextPhase.ERROR -> true
            AccessContextPhase.SIGNED_OUT, AccessContextPhase.CONFLICT, AccessContextPhase.UNAVAILABLE -> false
        }
    }
}
