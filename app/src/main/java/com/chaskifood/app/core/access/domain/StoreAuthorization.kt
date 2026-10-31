package com.chaskifood.app.core.access.domain

import kotlinx.coroutines.flow.Flow

/** Consulta de capacidades ya resueltas; no resuelve identidad ni obtiene hechos nuevos. */
interface StoreAuthorization {
    val enforced: Boolean
    val changes: Flow<Unit>
    fun hasAuthorizedStores(): Boolean
    fun allows(businessId: String, storeId: String): Boolean
}
