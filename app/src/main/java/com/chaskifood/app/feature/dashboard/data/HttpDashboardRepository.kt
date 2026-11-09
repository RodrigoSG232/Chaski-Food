package com.chaskifood.app.feature.dashboard.data

import com.chaskifood.app.core.access.data.SharedAccessContext
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import com.chaskifood.app.feature.dashboard.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class HttpDashboardRepository @Inject constructor(private val api:DashboardApi,
    private val tokens:BuyerTokenSource,private val access:SharedAccessContext):DashboardRepository {
    private fun authorized(c:DashboardContext):Boolean {
        val state=access.currentState()
        val resolved=state.resolution as? AccessResolution.Resolved?:return false
        return state.serverRevision==c.revision && state.origin==c.origin && resolved.identity.session.uid==c.uid &&
            resolved.capabilities.grants.any { grant -> grant.capability==AccessCapability.READ_BUSINESS_METRICS &&
                when(val scope=grant.scope) {
                    is AccessScope.Business -> scope.businessId==c.businessId
                    is AccessScope.Store -> scope.businessId==c.businessId
                    else -> false
                } }
    }
    override suspend fun read(context:DashboardContext,days:Int,storeId:String?):DashboardResult {
        if(!authorized(context) || !tokens.isCurrent(context.uid,context.generation))return DashboardResult.Stale
        return try { withTimeoutOrNull(20_000) {
            val token=tokens.token(context.uid,context.generation)
            val response=api.read("Bearer $token",context.businessId,days,storeId)
            if(!authorized(context) || !tokens.isCurrent(context.uid,context.generation))return@withTimeoutOrNull DashboardResult.Stale
            when(response.code()) {
                200 -> {
                    val body=requireNotNull(response.body())
                    require(body.contractVersion==1 && body.businessId==context.businessId && body.days==days &&
                        body.storeId==storeId && body.timeZone=="America/Lima" && body.currency=="PEN" &&
                        body.series.size==days && body.orders>=0 && body.salesMinor>=0 && body.accepted>=0 && body.rejected>=0 &&
                        body.accepted.toLong()+body.rejected<=body.orders &&
                        (body.acceptanceRate==null || body.acceptanceRate in 0.0..100.0) &&
                        body.stores.map{it.storeId}.distinct().size==body.stores.size &&
                        body.series.all{it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&it.orders>=0&&it.salesMinor>=0} &&
                        body.series.sumOf{it.orders}==body.orders && body.series.sumOf{it.salesMinor}==body.salesMinor)
                    DashboardResult.Loaded(DashboardData(body.businessId,days,storeId,body.generatedAtEpochMillis,
                        body.stores.map{DashboardStore(it.storeId,it.name)},body.orders,body.salesMinor,
                        body.accepted,body.rejected,body.acceptanceRate,
                        body.series.map{DashboardDay(it.date,it.orders,it.salesMinor)}))
                }
                401,403 -> DashboardResult.Denied
                else -> DashboardResult.Unavailable
            }
        }?:DashboardResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:Exception){DashboardResult.Unavailable}
    }
}
