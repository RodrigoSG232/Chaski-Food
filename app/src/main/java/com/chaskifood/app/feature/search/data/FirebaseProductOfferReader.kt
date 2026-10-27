package com.chaskifood.app.feature.search.data

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.getCurrentDayOfWeek
import com.chaskifood.app.feature.business.domain.getCurrentFormattedTime
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.isValid
import com.chaskifood.app.feature.catalog.domain.storeProductConfigurationId
import com.chaskifood.app.feature.discover.data.toDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.StoreEligibilityResult
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.evaluateStoreEligibility
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import com.chaskifood.app.feature.search.domain.ProductOfferReviewSession
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** No utiliza los listeners de Search: sus valores Ready pueden proceder de caché. */
class FirebaseProductOfferReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ProductOfferReader {
    /** Observación opcional de llamadas al SDK para pruebas con Emulator; null en producción. */
    internal var onServerGet: ((String) -> Unit)? = null

    override suspend fun read(
        storeId: String,
        productId: String,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReadResult = readSafely(storeId, productId, deliveryLocation) {
        readVerified(storeId, productId, deliveryLocation)
    }

    override fun openCheckoutReview(
        verifiedStore: StoreDiscoveryCandidate,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReviewSession {
        // La instancia y su memoria terminan con esta revisión; no comparten categorías entre compras.
        val categories = mutableMapOf<String, ProductCategory>()
        return ProductOfferReviewSession { productId ->
            readSafely(verifiedStore.store.id, productId, deliveryLocation) {
                readVerifiedProduct(verifiedStore, productId, deliveryLocation, categories)
            }
        }
    }

    private suspend fun readSafely(
        storeId: String,
        productId: String,
        deliveryLocation: AddressCoordinates,
        block: suspend () -> ProductOfferReadResult,
    ): ProductOfferReadResult = try {
        if (storeId.isBlank() || productId.isBlank() || !deliveryLocation.isValid) {
            ProductOfferReadResult.InvalidData
        } else {
            block()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: UnverifiedOfferException) {
        ProductOfferReadResult.Unverified
    } catch (error: FirebaseFirestoreException) {
        when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ProductOfferReadResult.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.UNKNOWN,
            FirebaseFirestoreException.Code.INTERNAL -> ProductOfferReadResult.NetworkError
            else -> ProductOfferReadResult.InvalidData
        }
    } catch (_: Exception) {
        ProductOfferReadResult.InvalidData
    }

    private suspend fun readVerified(
        storeId: String,
        productId: String,
        deliveryLocation: AddressCoordinates,
    ): ProductOfferReadResult {
        val storeDocument = serverDocument(firestore.collection("stores").document(storeId))
        if (!storeDocument.exists()) return ProductOfferReadResult.Unavailable
        // Discover admite un radio por defecto para presentar locales legados;
        // una incorporación validada exige que el documento declare su cobertura.
        if (storeDocument.data?.get("deliveryRadiusKm") !is Number) {
            return ProductOfferReadResult.InvalidData
        }
        val store = storeDocument.toDiscoveryCandidate() ?: return ProductOfferReadResult.InvalidData

        return readVerifiedProduct(store, productId, deliveryLocation, null)
    }

    private suspend fun readVerifiedProduct(
        store: StoreDiscoveryCandidate,
        productId: String,
        deliveryLocation: AddressCoordinates,
        categories: MutableMap<String, ProductCategory>?,
    ): ProductOfferReadResult {
        val storeId = store.store.id
        val productDocument = serverDocument(firestore.collection("products").document(productId))
        if (!productDocument.exists()) return ProductOfferReadResult.ProductNotFound
        val product = productDocument.toOfferProduct() ?: return ProductOfferReadResult.InvalidData

        val category = categories?.get(product.categoryId) ?: run {
            val categoryDocument = serverDocument(firestore.collection("categories").document(product.categoryId))
            if (!categoryDocument.exists()) return ProductOfferReadResult.Unavailable
            val verified = categoryDocument.toOfferCategory() ?: return ProductOfferReadResult.InvalidData
            categories?.put(product.categoryId, verified)
            verified
        }

        val configurationDocument = serverDocument(firestore.collection("store_product_configs")
            .document(storeProductConfigurationId(storeId, productId)))
        val configuration = if (configurationDocument.exists()) {
            configurationDocument.toOfferConfiguration() ?: return ProductOfferReadResult.InvalidData
        } else null
        return evaluateProductOffer(store, product, category, configuration, deliveryLocation,
            getCurrentDayOfWeek(), getCurrentFormattedTime())
    }

    private suspend fun serverDocument(reference: DocumentReference): DocumentSnapshot {
        onServerGet?.invoke(reference.path)
        val document = reference.get(Source.SERVER).await()
        if (document.metadata.isFromCache || document.metadata.hasPendingWrites()) {
            throw UnverifiedOfferException()
        }
        return document
    }
}

private class UnverifiedOfferException : Exception()

/** Reutiliza la elegibilidad de Discover sin consultar la ficha privada del negocio. */
internal fun evaluateProductOffer(
    store: StoreDiscoveryCandidate,
    product: Product,
    category: ProductCategory,
    configuration: StoreProductConfiguration?,
    deliveryLocation: AddressCoordinates,
    currentDay: DayOfWeekEnum,
    currentTime: String,
): ProductOfferReadResult {
    if (evaluateStoreEligibility(store, deliveryLocation, currentDay, currentTime)
        !is StoreEligibilityResult.Eligible) return ProductOfferReadResult.Unavailable
    if (product.businessId != store.store.businessId || product.categoryId != category.id ||
        category.businessId != product.businessId) return ProductOfferReadResult.InvalidData
    if (!category.isActive) return ProductOfferReadResult.Unavailable
    if (configuration != null && (!configuration.isValid() ||
            configuration.businessId != store.store.businessId ||
            configuration.storeId != store.store.id || configuration.productId != product.id)) {
        return ProductOfferReadResult.InvalidData
    }
    return ProductOfferReadResult.Found(product, configuration)
}
