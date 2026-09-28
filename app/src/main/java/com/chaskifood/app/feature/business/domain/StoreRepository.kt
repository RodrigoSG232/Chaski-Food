package com.chaskifood.app.feature.business.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getStoresByBusiness(businessId: String): Flow<ApiResult<List<BusinessStore>>>
    suspend fun createStore(store: BusinessStore): ApiResult<BusinessStore>
    suspend fun updateStore(store: BusinessStore): ApiResult<BusinessStore>
    fun getManagersByBusiness(businessId: String): Flow<ApiResult<List<StoreManager>>>
    suspend fun assignManager(manager: StoreManager): ApiResult<StoreManager>
    fun getStoresForManager(managerEmailOrUid: String): Flow<ApiResult<List<BusinessStore>>>
    suspend fun updateOperationalStatus(storeId: String, status: OperationalStatus, pauseReason: String? = null): ApiResult<Unit>
    suspend fun updateOperatingHours(storeId: String, hours: List<DayOperatingHours>): ApiResult<Unit>
}
