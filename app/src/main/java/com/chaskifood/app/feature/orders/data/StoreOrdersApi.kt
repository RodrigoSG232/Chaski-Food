package com.chaskifood.app.feature.orders.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class StoreOrdersResponse(val contractVersion:Int,val storeId:String,val storeName:String,
    val orders:List<StoreOrderResponse>,val nextCursor:String?)
@Serializable internal data class StoreOrderResponse(val orderId:String,val createdAtEpochMillis:Long,val status:String,
    val items:List<StoreOrderLineResponse>,val subtotalMinor:Long,val deliveryFeeMinor:Long,val totalMinor:Long,
    val currency:String,val paymentMethod:String,val estimatedPreparationMinutes:Int?=null,val estimatedReadyAtEpochMillis:Long?=null,
    val readyAtEpochMillis:Long?=null,val tipMinor:Long=0,val simulationStage:String?=null,
    val estimateRevision:Long=0,val preparationExtendedMinutes:Int=0,val pickup:StorePickupResponse?=null,
    val paymentStatus:String?=null,val amountToCollectMinor:Long?=null)
/** foodApi still sends pickupCode for older APKs (transitional): this app never declares nor reads it. */
@Serializable internal data class StorePickupResponse(val assignment:StoreRiderAssignmentResponse?=null,val verified:Boolean,
    val locked:Boolean,val attemptsRemaining:Int)
@Serializable internal data class StoreRiderAssignmentResponse(val assignmentId:String,val assignmentVersion:Int,
    val riderDisplayName:String?=null,val vehicleType:String?=null,val vehiclePlate:String?=null)
@Serializable internal data class PickupVerificationResponse(val contractVersion:Int,val outcome:String,val attemptsRemaining:Int,
    val replayed:Boolean=false)
@Serializable internal data class PreparationExtensionResponse(val contractVersion:Int,val outcome:String,val status:String,
    val estimatedReadyAtEpochMillis:Long,val estimateRevision:Long,val preparationExtendedMinutes:Int)
@Serializable internal data class StoreOrderLineResponse(val productId:String,val name:String,val quantity:Int,
    val unitPriceMinor:Long,val lineTotalMinor:Long)
@Serializable internal data class StoreOrderDecisionResponse(val contractVersion:Int,val outcome:String,val status:String,
    val estimatedReadyAtEpochMillis:Long?=null,val readyAtEpochMillis:Long?=null)
internal interface StoreOrdersApi {
    @GET("stores/{storeId}/orders")
    suspend fun page(@Path("storeId") storeId:String,@Header("Authorization") authorization:String,
        @Query("cursor") cursor:String?,@Query("status") status:String,@Query("pageSize") pageSize:Int=20):Response<StoreOrdersResponse>
    @POST("stores/{storeId}/orders/{orderId}/decision")
    suspend fun respond(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
    @POST("stores/{storeId}/orders/{orderId}/preparation-extensions")
    suspend fun extendPreparation(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<PreparationExtensionResponse>
    @POST("stores/{storeId}/orders/{orderId}/ready-for-pickup")
    suspend fun markReady(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
    @POST("stores/{storeId}/orders/{orderId}/pickup-verifications")
    suspend fun verifyPickup(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<PickupVerificationResponse>
    @POST("stores/{storeId}/orders/{orderId}/simulate-pickup")
    suspend fun simulatePickup(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
    @POST("stores/{storeId}/orders/{orderId}/simulate-arrival")
    suspend fun simulateArrival(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
}
