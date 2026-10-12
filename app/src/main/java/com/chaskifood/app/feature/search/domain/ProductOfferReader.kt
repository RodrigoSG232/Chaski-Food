package com.chaskifood.app.feature.search.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration

/** Lectura puntual confirmada por el servidor para una incorporación al carrito. */
interface ProductOfferReader {
    suspend fun read(
        storeId: String,
        productId: String,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReadResult
}

sealed interface ProductOfferReadResult {
    data class Found(
        val product: Product,
        val configuration: StoreProductConfiguration?,
    ) : ProductOfferReadResult
    data object ProductNotFound : ProductOfferReadResult
    data object Unavailable : ProductOfferReadResult
    data object PermissionDenied : ProductOfferReadResult
    data object NetworkError : ProductOfferReadResult
    data object Unverified : ProductOfferReadResult
    data object InvalidData : ProductOfferReadResult
}
