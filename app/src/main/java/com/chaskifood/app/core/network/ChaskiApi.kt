package com.chaskifood.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class HealthResponse(
    val status: String = "",
)

interface ChaskiApi {
    @GET("health")
    suspend fun health(): HealthResponse
}