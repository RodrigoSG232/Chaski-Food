package com.chaskifood.app.feature.orders.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class StoreOrdersResponse(val contractVersion:Int,val storeId:String,val storeName:String,
    val orders:List<StoreOrderResponse>,val nextCursor:String?)
@Serializable internal data class StoreOrderResponse(val orderId:String,val createdAtEpochMillis:Long,val status:String,
    val items:List<StoreOrderLineResponse>,val subtotalMinor:Long,val deliveryFeeMinor:Long,val totalMinor:Long,
    val currency:String,val paymentMethod:String,val estimatedPreparationMinutes:Int?=null,val estimatedReadyAtEpochMillis:Long?=null)
@Serializable internal data class StoreOrderLineResponse(val productId:String,val name:String,val quantity:Int,
    val unitPriceMinor:Long,val lineTotalMinor:Long)
@Serializable internal data class StoreOrderDecisionResponse(val contractVersion:Int,val outcome:String,val status:String,
    val estimatedReadyAtEpochMillis:Long?=null)
internal interface StoreOrdersApi {
    @GET("stores/{storeId}/orders")
    suspend fun page(@Path("storeId") storeId:String,@Header("Authorization") authorization:String,
        @Query("cursor") cursor:String?,@Query("status") status:String,@Query("pageSize") pageSize:Int=20):Response<StoreOrdersResponse>
    @POST("stores/{storeId}/orders/{orderId}/decision")
    suspend fun respond(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
    @PUT("stores/{storeId}/orders/{orderId}/preparation-estimate")
    suspend fun updateEstimate(@Path("storeId") storeId:String,@Path("orderId") orderId:String,
        @Header("Authorization") authorization:String,@Body body:JsonObject):Response<StoreOrderDecisionResponse>
}
