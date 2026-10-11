package com.chaskifood.app.feature.discover.domain

import com.chaskifood.app.core.common.ApiResult

interface AvailableStoresRepository {
    /** Consulta usando exclusivamente la dirección seleccionada de la cuenta autenticada. */
    suspend fun getAvailableStores(): ApiResult<List<DiscoverableStore>>
}
