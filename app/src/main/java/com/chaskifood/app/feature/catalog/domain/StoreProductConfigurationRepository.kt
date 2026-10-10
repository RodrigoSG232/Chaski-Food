package com.chaskifood.app.feature.catalog.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface StoreProductConfigurationRepository {
    fun getConfigurations(storeId: String): Flow<ApiResult<List<StoreProductConfiguration>>>

    suspend fun getConfiguration(
        storeId: String,
        productId: String,
    ): ApiResult<StoreProductConfiguration?>

    suspend fun saveConfiguration(
        configuration: StoreProductConfiguration,
    ): ApiResult<StoreProductConfiguration>
}
