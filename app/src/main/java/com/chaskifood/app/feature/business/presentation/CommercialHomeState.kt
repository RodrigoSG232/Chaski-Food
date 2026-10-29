package com.chaskifood.app.feature.business.presentation

import com.chaskifood.app.core.access.domain.AccessProvenance
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStore

sealed interface CommercialHomeState {
    data object Loading : CommercialHomeState
    data class Error(val origin: AccessProvenance, val message: String) : CommercialHomeState
    data class Owner(
        val origin: AccessProvenance,
        val business: BusinessRequest,
        val suspended: Boolean,
        val suspensionReason: String?,
        val stores: List<BusinessStore>?,
        val storeError: String? = null,
        val canManageStores: Boolean,
        val canManageCatalog: Boolean,
        val canOperate: Boolean,
    ) : CommercialHomeState
    data class Manager(
        val origin: AccessProvenance,
        val locals: List<CommercialLocal>,
        val selectedId: String?,
        val permissionsChanged: Boolean = false,
    ) : CommercialHomeState
}

data class CommercialLocal(val store: BusinessStore, val canOperate: Boolean,
    val withinSchedule: Boolean, val todayHours: String, val canReadOrders: Boolean = false)

internal val CommercialHomeState.origin: AccessProvenance?
    get() = when (this) {
        CommercialHomeState.Loading -> null
        is CommercialHomeState.Owner -> origin
        is CommercialHomeState.Manager -> origin
        is CommercialHomeState.Error -> origin
    }
