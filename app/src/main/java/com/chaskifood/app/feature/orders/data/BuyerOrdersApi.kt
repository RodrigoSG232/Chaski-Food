package com.chaskifood.app.feature.orders.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class BuyerOrdersResponse(val contractVersion: Int, val phase: String,
    val orders: List<BuyerOrderSummaryResponse>, val nextCursor: String?)
@Serializable internal data class BuyerOrderSummaryResponse(val orderId: String, val createdAtEpochMillis: Long, val status: String,
    val phase: String, val statusKnown: Boolean, val storeName: String, val businessName: String, val itemCount: Int,
    val totalMinor: Long, val currency: String, val itemPreview: List<BuyerOrderItemPreviewResponse>, val itemLineCount: Int)
@Serializable internal data class BuyerOrderItemPreviewResponse(val name: String, val quantity: Int)
@Serializable internal data class BuyerOrderDetailEnvelope(val contractVersion: Int, val order: BuyerOrderDetailResponse)
@Serializable internal data class BuyerOrderDetailResponse(val orderId: String, val createdAtEpochMillis: Long, val status: String,
    val phase: String, val statusKnown: Boolean, val storeName: String, val businessName: String, val itemCount: Int,
    val totalMinor: Long, val currency: String, val storePhone: String?, val pickupAddressText: String,
    val delivery: BuyerOrderDeliveryResponse, val items: List<StoreOrderLineResponse>, val subtotalMinor: Long,
    val deliveryFeeMinor: Long, val paymentMethod: String, val statusHistory: List<BuyerOrderStatusResponse>,
    val itemPreview: List<BuyerOrderItemPreviewResponse>, val itemLineCount: Int)
@Serializable internal data class BuyerOrderDeliveryResponse(val label: String?, val addressText: String, val reference: String?,
    val instructions: String?)
@Serializable internal data class BuyerOrderStatusResponse(val status: String, val statusKnown: Boolean, val occurredAtEpochMillis: Long)

internal interface BuyerOrdersApi {
    @GET("me/orders")
    suspend fun page(@Header("Authorization") authorization: String, @Query("phase") phase: String,
        @Query("cursor") cursor: String?, @Query("pageSize") pageSize: Int = 20): Response<BuyerOrdersResponse>
    @GET("me/orders/{orderId}")
    suspend fun detail(@Header("Authorization") authorization: String, @Path("orderId") orderId: String): Response<BuyerOrderDetailEnvelope>
}
