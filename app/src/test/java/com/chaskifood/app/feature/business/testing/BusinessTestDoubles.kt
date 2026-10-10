package com.chaskifood.app.feature.business.testing

import android.app.Activity
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import com.chaskifood.app.feature.business.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class BusinessTestAuth : AuthRepository {
    override val currentUserFlow = MutableStateFlow<AuthUser?>(AuthUser("owner", "owner@example.com", "Owner"))
    override suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> = error("unused")
    override suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String> = error("unused")
    override suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit> = error("unused")
    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> = error("unused")
    override suspend fun updateProfile(displayName: String): ApiResult<Unit> = error("unused")
    override suspend fun signOut() { currentUserFlow.value = null }
}

class BusinessTestRepository : BusinessRepository {
    val requests = MutableStateFlow<ApiResult<List<BusinessRequest>>>(ApiResult.Success(emptyList()))
    var existing: BusinessRequest? = null
    var submitted: BusinessRequest? = null
    var submissionCount = 0
    var resubmissions = 0
    var evaluations = 0
    var reviewer: String? = null
    var gate: CompletableDeferred<Unit>? = null
    var result: ApiResult<Unit> = ApiResult.Success(Unit)
    override fun getBusinessRequest(ownerUid: String) = flowOf(ApiResult.Success(existing))
    override fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>> = requests
    override suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> {
        submissionCount++
        gate?.await()
        return when (val response = result) {
            is ApiResult.Success -> ApiResult.Success(request).also { submitted = request }
            is ApiResult.Failure -> response
        }
    }
    override suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> {
        resubmissions++
        return submitBusinessRequest(request)
    }
    override suspend fun evaluateBusinessRequest(requestId: String, status: BusinessStatus, observations: String?, reviewerEmail: String): ApiResult<Unit> {
        evaluations++
        reviewer = reviewerEmail
        gate?.await()
        return result
    }
    override suspend fun suspendBusiness(requestId: String, reason: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override suspend fun reactivateBusiness(requestId: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>> = error("unused")
}

class OperationTestStores : StoreRepository {
    val assigned = MutableStateFlow<ApiResult<List<BusinessStore>>>(ApiResult.Success(listOf(
        BusinessStore(id = "a", name = "Local A"), BusinessStore(id = "b", name = "Local B"),
    )))
    var calls = 0
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var result: ApiResult<Unit> = ApiResult.Success(Unit)
    var exception: Exception? = null
    var savedReason: String? = null
    var savedHours: List<DayOperatingHours>? = null
    private suspend fun save(storeId: String): ApiResult<Unit> {
        calls++
        gates[storeId]?.await()
        exception?.let { throw it }
        return result
    }
    override fun getStoresForManager(managerEmailOrUid: String): Flow<ApiResult<List<BusinessStore>>> = assigned
    override suspend fun updateOperationalStatus(storeId: String, status: OperationalStatus, pauseReason: String?): ApiResult<Unit> {
        savedReason = pauseReason
        return save(storeId)
    }
    override suspend fun updateOperatingHours(storeId: String, hours: List<DayOperatingHours>): ApiResult<Unit> {
        savedHours = hours
        return save(storeId)
    }
    override fun getStoresByBusiness(businessId: String): Flow<ApiResult<List<BusinessStore>>> = error("unused")
    override suspend fun createStore(store: BusinessStore): ApiResult<BusinessStore> = error("unused")
    override suspend fun updateStore(store: BusinessStore): ApiResult<BusinessStore> = error("unused")
    override fun getManagersByBusiness(businessId: String): Flow<ApiResult<List<StoreManager>>> = error("unused")
    override suspend fun assignManager(manager: StoreManager): ApiResult<StoreManager> = error("unused")
    override suspend fun setAdministrativeStatus(storeId: String, status: StoreStatus, reason: String?): ApiResult<Unit> = error("unused")
}
