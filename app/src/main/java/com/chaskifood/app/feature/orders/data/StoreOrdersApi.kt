package com.chaskifood.app.feature.orders.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class StoreOrdersResponse(val contractVersion:Int,val storeId:String,val storeName:String,
    val orders:List<StoreOrderResponse>,val nextCursor:String?)
@Serializable internal data class StoreOrderResponse(val orderId:String,val createdAtEpochMillis:Long,val status:String,
    val items:List<StoreOrderLineResponse>,val subtotalMinor:Long,val deliveryFeeMinor:Long,val totalMinor:Long,
    val currency:String,val paymentMethod:String)
@Serializable internal data class StoreOrderLineResponse(val productId:String,val name:String,val quantity:Int,
    val unitPriceMinor:Long,val lineTotalMinor:Long)
internal interface StoreOrdersApi {
    @GET("stores/{storeId}/orders")
    suspend fun page(@Path("storeId") storeId:String,@Header("Authorization") authorization:String,
        @Query("cursor") cursor:String?,@Query("pageSize") pageSize:Int=20):Response<StoreOrdersResponse>
}
