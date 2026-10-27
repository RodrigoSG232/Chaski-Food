package com.chaskifood.app.feature.search.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate

/** Lectura puntual confirmada por el servidor para una incorporación al carrito. */
interface ProductOfferReader {
    suspend fun read(
        storeId: String,
        productId: String,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReadResult

    /** Solo Checkout entrega un local previamente verificado; la implementación puntual sigue intacta. */
    fun openCheckoutReview(
        verifiedStore: StoreDiscoveryCandidate,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReviewSession = ProductOfferReviewSession { productId ->
        read(verifiedStore.store.id, productId, deliveryLocation)
    }
}

fun interface ProductOfferReviewSession {
    suspend fun read(productId: String): ProductOfferReadResult
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
