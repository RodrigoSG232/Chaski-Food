package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.orders.domain.*
import javax.inject.Inject
import kotlinx.coroutines.flow.*

internal interface StoreOrdersAccessSource {
    fun observe(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization>
    fun current(target:StoreOrdersTarget):StoreOrdersAuthorization
}
/** Reuses HU34 and the existing cart session generation; no additional Auth listener. */
internal class SharedStoreOrdersAccessSource @Inject constructor(
    private val access:SharedAccessContext,private val sessions:CartSessionSource,
):StoreOrdersAccessSource {
    override fun observe(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization> =
        combine(access.states,sessions.sessions){_,_->current(target)}.distinctUntilChanged()
    override fun current(target:StoreOrdersTarget):StoreOrdersAuthorization {
        if(BuildConfig.FIREBASE_MODE!="LOCAL")return StoreOrdersAuthorization.Unavailable
        val session=sessions.current();val uid=session.uid?:return StoreOrdersAuthorization.SignedOut
        val state=access.currentState()
        if(state.phase==AccessContextPhase.LOADING)return StoreOrdersAuthorization.Verifying
        val resolved=state.resolution as? AccessResolution.Resolved?:return StoreOrdersAuthorization.Denied
        val revision=state.serverRevision?:return StoreOrdersAuthorization.Unavailable
        if(resolved.identity.session.uid!=uid || state.origin.session!=resolved.identity.session ||
            !resolved.capabilities.allows(AccessCapability.READ_STORE_ORDERS,AccessScope.Store(target.businessId,target.storeId)))
            return StoreOrdersAuthorization.Denied
        return StoreOrdersAuthorization.Allowed(StoreOrdersContext(uid,session.generation,state.origin,revision,target))
    }
}
