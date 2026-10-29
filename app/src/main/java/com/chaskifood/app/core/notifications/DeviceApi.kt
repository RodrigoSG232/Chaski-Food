package com.chaskifood.app.core.notifications

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable internal data class DeviceRegistration(val token:String,val bindingVersion:Long,val platform:String="ANDROID")
internal interface DeviceApi {
    @PUT("devices/{installationId}") suspend fun put(@Path("installationId") id:String,@Header("Authorization") bearer:String,@Body body:DeviceRegistration):Response<Unit>
    @DELETE("devices/{installationId}") suspend fun remove(@Path("installationId") id:String,@Header("Authorization") bearer:String,@Query("bindingVersion") bindingVersion:Long):Response<Unit>
}
