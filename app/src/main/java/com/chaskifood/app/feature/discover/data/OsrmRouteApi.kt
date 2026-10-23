package com.chaskifood.app.feature.discover.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class OsrmTableResponse(
    val code: String,
    val distances: List<List<Double?>>? = null,
    val durations: List<List<Double?>>? = null,
)

interface OsrmRouteApi {
    @GET("table/v1/driving/{coordinates}")
    suspend fun table(
        @Path(value = "coordinates", encoded = true) coordinates: String,
        @Query("sources") sources: String = "0",
        @Query("annotations") annotations: String = "distance,duration",
    ): OsrmTableResponse
}
