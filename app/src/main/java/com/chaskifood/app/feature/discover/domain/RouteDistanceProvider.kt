package com.chaskifood.app.feature.discover.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates

data class RouteDestination(val storeId: String, val location: AddressCoordinates)

data class RoadEstimate(val distanceKm: Double, val durationMinutes: Int)

/** Estimaciones informativas; la cobertura comercial sigue usando el radio del local. */
interface RouteDistanceProvider {
    suspend fun estimate(
        origin: AddressCoordinates,
        destinations: List<RouteDestination>,
    ): Map<String, RoadEstimate>
}
