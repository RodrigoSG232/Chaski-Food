package com.chaskifood.app.core.access.data

import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.flow.map

/** Adaptador del contexto único para las dos pantallas de operación por local. */
class CurrentStoreAuthorization(private val context: SharedAccessContext) : StoreAuthorization {
    override val enforced = BuildConfig.FIREBASE_MODE == "LOCAL"
    override val changes = context.states.map { Unit }
    private fun grants() = (context.currentState().resolution as? AccessResolution.Resolved)?.capabilities
    override fun hasAuthorizedStores() = !enforced || grants()?.grants.orEmpty().any {
        it.capability == AccessCapability.OPERATE_HOURS_AND_OFFERS && it.scope is AccessScope.Store
    }
    override fun allows(businessId: String, storeId: String) = !enforced || grants()?.allows(
        AccessCapability.OPERATE_HOURS_AND_OFFERS, AccessScope.Store(businessId, storeId)) == true
}
