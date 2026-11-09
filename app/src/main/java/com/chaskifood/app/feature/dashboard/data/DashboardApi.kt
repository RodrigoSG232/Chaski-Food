package com.chaskifood.app.feature.dashboard.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@Serializable internal data class DashboardResponse(val contractVersion:Int,val businessId:String,val days:Int,
    val storeId:String?,val generatedAtEpochMillis:Long,val timeZone:String,val currency:String,
    val stores:List<DashboardStoreResponse>,val orders:Int,val salesMinor:Long,val accepted:Int,val rejected:Int,
    val acceptanceRate:Double?,val series:List<DashboardDayResponse>)
@Serializable internal data class DashboardStoreResponse(val storeId:String,val name:String)
@Serializable internal data class DashboardDayResponse(val date:String,val orders:Int,val salesMinor:Long)
internal interface DashboardApi {
    @GET("commercial/dashboard")
    suspend fun read(@Header("Authorization") authorization:String,@Query("businessId") businessId:String,
        @Query("days") days:Int,@Query("storeId") storeId:String?):Response<DashboardResponse>
}
