package com.chaskifood.app.feature.address.domain

/** Una captura solicitada por el usuario; nunca rastreo en segundo plano. */
interface DeviceLocationProvider {
    suspend fun currentLocation(): DeviceLocationResult
}

sealed interface DeviceLocationResult {
    data class Available(
        val coordinates: AddressCoordinates,
        val accuracyMeters: Float?,
        val approximate: Boolean,
    ) : DeviceLocationResult
    data object PermissionRequired : DeviceLocationResult
    data object Disabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}
