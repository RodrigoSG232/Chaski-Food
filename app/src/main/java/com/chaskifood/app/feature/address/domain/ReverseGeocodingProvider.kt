package com.chaskifood.app.feature.address.domain

/** Traduce un punto confirmado a una sugerencia de texto, si el dispositivo dispone del servicio. */
interface ReverseGeocodingProvider {
    suspend fun addressFor(point: AddressCoordinates): ReverseGeocodingResult
}

sealed interface ReverseGeocodingResult {
    data class Found(val address: String) : ReverseGeocodingResult
    data object Unavailable : ReverseGeocodingResult
}
