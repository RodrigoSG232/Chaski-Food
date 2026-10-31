package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.core.access.data.SharedAccessContext
import com.chaskifood.app.core.access.domain.*
import javax.inject.Inject

internal fun interface CheckoutPurchaseGuard { fun allows(uid: String, purchase: Boolean): Boolean }

internal class SharedCheckoutPurchaseGuard @Inject constructor(private val access: SharedAccessContext) : CheckoutPurchaseGuard {
    override fun allows(uid: String, purchase: Boolean): Boolean {
        val snapshot = access.currentState()
        val resolution = snapshot.resolution
        val session = when (resolution) {
            is AccessResolution.Resolved -> resolution.identity.session
            is AccessResolution.Conflict -> resolution.session
            else -> return false
        }
        if (session.uid != uid || snapshot.origin.session != session) return false
        val grants = when (resolution) {
            is AccessResolution.Resolved -> resolution.capabilities
            is AccessResolution.Conflict -> resolution.capabilities
            else -> return false
        }
        return grants.allows(if (purchase) AccessCapability.START_PURCHASE else AccessCapability.READ_PERSONAL_PURCHASES,
            AccessScope.Account(uid))
    }
}
