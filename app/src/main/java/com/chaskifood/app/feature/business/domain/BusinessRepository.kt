package com.chaskifood.app.feature.business.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface BusinessRepository {
    fun getBusinessRequest(ownerUid: String): Flow<ApiResult<BusinessRequest?>>
    suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest>
    suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest>
}