package com.chaskifood.app.feature.checkout.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*

internal interface OrderApi {
    @POST("orders")
    suspend fun confirm(@Header("Authorization") authorization: String, @Body body: JsonObject): Response<JsonObject>
    @GET("order-attempts/{intentId}")
    suspend fun recover(@Header("Authorization") authorization: String, @Path("intentId") intentId: String): Response<JsonObject>
}
