package com.chaskifood.app.feature.checkout.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

@Serializable
internal data class StoreEligibilityResponse(
    val storeId: String,
    val businessId: String?,
    val decision: String,
    val checkedAt: String,
)

internal interface StoreEligibilityApi {
    @GET("stores/{storeId}/purchase-eligibility")
    suspend fun check(@Path("storeId") storeId: String,
                      @Header("Authorization") authorization: String): Response<StoreEligibilityResponse>
}
