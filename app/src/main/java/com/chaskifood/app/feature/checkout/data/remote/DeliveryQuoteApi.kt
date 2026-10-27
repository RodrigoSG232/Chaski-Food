package com.chaskifood.app.feature.checkout.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
internal data class DeliveryQuoteRequest(val storeId: String, val addressId: String)

@Serializable
internal data class DeliveryQuoteResponse(
    val distanceMeters: Double,
    val deliveryFeeMinor: Long,
    val currency: String,
    val tariffVersion: String,
    val quotedAt: String,
    val expiresAt: String,
    val storeLocation: QuoteLocationResponse,
    val deliveryLocation: QuoteLocationResponse,
)

@Serializable
internal data class QuoteLocationResponse(val latitude: Double, val longitude: Double)

@Serializable
internal data class DeliveryQuoteErrorResponse(val error: String)

internal interface DeliveryQuoteApi {
    @POST("delivery-quotes")
    suspend fun quote(@Body request: DeliveryQuoteRequest,
                      @Header("Authorization") authorization: String): Response<DeliveryQuoteResponse>
}
