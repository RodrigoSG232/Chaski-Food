package com.chaskifood.app.feature.business.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

/** Resultado de una suspensión cuya transacción ya fue confirmada. */
enum class BusinessSuspensionResult {
    VERIFIED,
    COMMITTED_UNVERIFIED,
    COMMITTED_ACTIVE_STORES,
}

interface BusinessRepository {
    fun getBusinessRequest(ownerUid: String): Flow<ApiResult<BusinessRequest?>>
    fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>>
    suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest>
    suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest>
    suspend fun evaluateBusinessRequest(
        requestId: String,
        status: BusinessStatus,
        observations: String?,
        reviewerEmail: String,
    ): ApiResult<Unit>
    suspend fun suspendBusiness(
        requestId: String,
        reason: String,
        adminEmail: String,
        businessName: String,
        ruc: String,
    ): ApiResult<BusinessSuspensionResult>
    suspend fun reactivateBusiness(
        requestId: String,
        adminEmail: String,
        businessName: String,
        ruc: String,
    ): ApiResult<Unit>
    fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>>
}
