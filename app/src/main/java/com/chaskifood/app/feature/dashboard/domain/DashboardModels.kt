package com.chaskifood.app.feature.dashboard.domain

import com.chaskifood.app.core.access.domain.AccessProvenance

data class DashboardContext(val uid:String,val generation:Long,val businessId:String,val revision:Long,val origin:AccessProvenance)
data class DashboardStore(val storeId:String,val name:String)
data class DashboardDay(val date:String,val orders:Int,val salesMinor:Long)
data class DashboardData(val businessId:String,val days:Int,val storeId:String?,val generatedAt:Long,
    val stores:List<DashboardStore>,val orders:Int,val salesMinor:Long,val accepted:Int,val rejected:Int,
    val acceptanceRate:Double?,val series:List<DashboardDay>)
sealed interface DashboardResult {
    data class Loaded(val data:DashboardData):DashboardResult
    data object Denied:DashboardResult
    data object Unavailable:DashboardResult
    data object Stale:DashboardResult
}
interface DashboardRepository {
    suspend fun read(context:DashboardContext,days:Int,storeId:String?):DashboardResult
}
