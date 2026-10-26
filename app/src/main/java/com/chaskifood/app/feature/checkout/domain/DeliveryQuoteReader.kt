package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates

/** Cotización temporal del servidor; HU16 deberá volver a validar la entrega antes del pedido. */
data class DeliveryQuote(
    val distanceMeters: Double,
    val deliveryFeeMinor: Long,
    val currency: CartCurrency,
    val tariffVersion: String,
    val quotedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val storeLocation: AddressCoordinates,
    val deliveryLocation: AddressCoordinates,
) {
    init {
        require(distanceMeters.isFinite() && distanceMeters >= 0.0)
        require(deliveryFeeMinor >= 0)
        require(tariffVersion.isNotBlank())
        require(quotedAtEpochMillis > 0 && expiresAtEpochMillis > quotedAtEpochMillis)
        require(storeLocation.isValid && deliveryLocation.isValid)
    }
}

enum class DeliveryQuoteFailure {
    SESSION_CHANGED, AUTHENTICATION_REQUIRED, ADDRESS_NOT_FOUND,
    STORE_UNAVAILABLE, BUSINESS_SUSPENDED, BUSINESS_NOT_APPROVED,
    OUTSIDE_COVERAGE, INVALID_LOCATION, NO_ROUTE, ROUTING_UNAVAILABLE,
    ROUTING_QUOTA_EXCEEDED, ROUTING_CONFIGURATION, ROUTING_INVALID_RESPONSE,
    INVALID_REQUEST, DATA_INCONSISTENT, SERVER_UNAVAILABLE,
    MALFORMED_RESPONSE, CONNECTIVITY, TIMEOUT, HTTP_ERROR, CONFIGURATION,
}

sealed interface DeliveryQuoteResult {
    data class Quoted(val quote: DeliveryQuote) : DeliveryQuoteResult
    data class Failed(val reason: DeliveryQuoteFailure) : DeliveryQuoteResult
}

/** Consulta puntual vinculada a la generación de sesión; nunca autoriza un pedido futuro. */
interface DeliveryQuoteReader {
    suspend fun quote(storeId: String, addressId: String, ownerUid: String,
                      sessionGeneration: Long): DeliveryQuoteResult
}
