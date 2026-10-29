package com.chaskifood.app.feature.business.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class CommercialBusiness(val businessName: String, val ruc: String, val legalAddress: String,
    val phone: String, val email: String, val category: String)
@Serializable
data class CommercialMutation(val action: String, val businessId: String? = null, val email: String? = null,
    val storeIds: List<String>? = null, val fullName: String? = null, val phone: String? = null,
    val business: CommercialBusiness? = null, val previousEmail: String? = null)
@Serializable
data class CommercialCommand(val action: String, val operationId: String, val expectedRevision: Long,
    val businessId: String? = null, val email: String? = null, val storeIds: List<String>? = null,
    val fullName: String? = null, val phone: String? = null, val business: CommercialBusiness? = null,
    val previousEmail: String? = null, val previousRevision: Long? = null)
@Serializable data class CommercialRevisionRequest(val businessId: String, val email: String? = null)
@Serializable data class CommercialRevision(val revision: Long)
@Serializable data class CommercialResult(val action: String, val revision: Long)
@Serializable data class CommercialFactsSummary(val uid: String)
@Serializable data class CommercialError(val error: String)

interface CommercialApi {
    @GET("access/facts") suspend fun facts(@Header("Authorization") authorization: String): Response<CommercialFactsSummary>
    @POST("access/operations") suspend fun execute(@Header("Authorization") authorization: String, @Body command: CommercialCommand): Response<CommercialResult>
    @GET("access/revision") suspend fun ownRevision(@Header("Authorization") authorization: String): Response<CommercialRevision>
    @POST("access/assignment-revision") suspend fun revision(@Header("Authorization") authorization: String, @Body target: CommercialRevisionRequest): Response<CommercialRevision>
}
