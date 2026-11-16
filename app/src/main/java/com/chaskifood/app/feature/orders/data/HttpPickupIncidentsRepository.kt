package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.orders.domain.*
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class PickupIncidentsResponse(val contractVersion:Int,val incidents:List<PickupIncidentResponse>,
    val truncated:Boolean=false)
@Serializable internal data class PickupIncidentResponse(val orderId:String,val shortId:String,val storeId:String,
    val storeName:String?=null,val status:String,val lockedAt:String,val cycle:Int,val failures:Int,val hasAssignment:Boolean)
@Serializable internal data class PickupUnlockResponse(val contractVersion:Int,val outcome:String,val cycle:Int,val replayed:Boolean=false)

internal interface PickupIncidentsApi {
    @GET("admin/pickup-incidents")
    suspend fun incidents(@Header("Authorization") authorization:String):Response<PickupIncidentsResponse>
    @POST("admin/pickup-incidents/{orderId}/unlock")
    suspend fun unlock(@Path("orderId") orderId:String,@Header("Authorization") authorization:String,
        @Body body:JsonObject):Response<PickupUnlockResponse>
}

/** Admin token from the existing access control (claim `admin`); foodApi re-checks it on every call. */
internal class HttpPickupIncidentsRepository @Inject constructor(private val api:PickupIncidentsApi,
    private val access:FirebaseAccessControl):PickupIncidentsRepository {
    private suspend fun bearer(forceRefresh:Boolean):String?{
        val user=runCatching{access.requireAdmin(forceRefresh)}.getOrNull()?:return null
        return user.getIdToken(false).await().token?.let{"Bearer $it"}
    }
    override suspend fun incidents():PickupIncidentsResult=try{ withTimeoutOrNull(15_000){
        val token=bearer(false)?:return@withTimeoutOrNull PickupIncidentsResult.AccessDenied
        val response=api.incidents(token)
        when(response.code()){
            200->{val body=requireNotNull(response.body());require(body.contractVersion==1)
                PickupIncidentsResult.Loaded(body.incidents.map{PickupIncident(it.orderId,it.storeName,it.status,
                    Instant.parse(it.lockedAt).toEpochMilli(),it.cycle,it.failures,it.hasAssignment)},body.truncated)}
            401->PickupIncidentsResult.AuthenticationRequired
            403->PickupIncidentsResult.AccessDenied
            else->PickupIncidentsResult.Unavailable
        }
    }?:PickupIncidentsResult.Unavailable}catch(e:CancellationException){throw e}catch(_:Exception){PickupIncidentsResult.Unavailable}

    override suspend fun unlock(orderId:String,request:PickupUnlockRequest):PickupUnlockResult{
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return PickupUnlockResult.NotFound
        return try{ withTimeoutOrNull(15_000){
            // Mutations confirm the claim with a fresh token, as the other admin operations do.
            val token=bearer(true)?:return@withTimeoutOrNull PickupUnlockResult.AccessDenied
            val response=api.unlock(orderId,token,buildJsonObject{put("operationId",request.operationId);put("reason",request.reason)})
            when(response.code()){
                200->{val body=requireNotNull(response.body())
                    require(body.contractVersion==1&&body.outcome=="UNLOCKED"&&body.cycle>=2);PickupUnlockResult.Unlocked(body.cycle)}
                400->PickupUnlockResult.InvalidReason
                401->PickupUnlockResult.AuthenticationRequired
                403->PickupUnlockResult.AccessDenied
                404->PickupUnlockResult.NotFound
                409->when(errorCode(response.errorBody()?.string())){
                    "PICKUP_NOT_LOCKED"->PickupUnlockResult.NotLocked
                    "IDEMPOTENCY_CONFLICT"->PickupUnlockResult.OperationConflict
                    else->PickupUnlockResult.StateChanged}
                else->PickupUnlockResult.Unavailable
            }
        }?:PickupUnlockResult.Unavailable}catch(e:CancellationException){throw e}catch(_:Exception){PickupUnlockResult.Unavailable}
    }
    private fun errorCode(body:String?):String?=runCatching{
        Json.parseToJsonElement(requireNotNull(body)).jsonObject["error"]?.jsonPrimitive?.content}.getOrNull()
}
