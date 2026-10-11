@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.search.data

import com.chaskifood.app.feature.catalog.data.StoreProductConfigurationDocumentMapper
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.StoreOfferState
import com.chaskifood.app.feature.search.domain.buildStoreOffer
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStoreOfferRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val availableStoresRepository: AvailableStoresRepository,
) : StoreOfferRepository {

    override fun observeStoreOffer(storeId: String): Flow<StoreOfferState> {
        if (storeId.isBlank()) return flowOf(StoreOfferState.StoreUnavailable("Selecciona un local válido."))
        return availableStoresRepository.observeAvailableStores().flatMapLatest { availableState ->
            when (availableState) {
                AvailableStoresState.Loading -> flowOf(StoreOfferState.Loading)
                AvailableStoresState.SignedOut -> flowOf(StoreOfferState.StoreUnavailable(
                    "Inicia sesión para consultar la oferta.",
                ))
                AvailableStoresState.AddressRequired -> flowOf(StoreOfferState.StoreUnavailable(
                    "Selecciona una dirección de entrega para consultar la oferta.",
                ))
                is AvailableStoresState.Error -> flowOf(StoreOfferState.Error(availableState.message))
                is AvailableStoresState.Ready -> {
                    val store = availableState.stores.singleOrNull { it.id == storeId }
                    if (store == null) {
                        flowOf(StoreOfferState.StoreUnavailable(
                            "Este local ya no está disponible para tu dirección.",
                        ))
                    } else {
                        observeOffer(store)
                    }
                }
            }
        }
    }

    private fun observeOffer(store: DiscoverableStore): Flow<StoreOfferState> = combine(
        observeDocuments(
            firestore.collection("categories").whereEqualTo("businessId", store.businessId),
            "No se pudieron cargar las categorías.",
            DocumentSnapshot::toOfferCategory,
        ),
        observeDocuments(
            firestore.collection("products").whereEqualTo("businessId", store.businessId),
            "No se pudieron cargar los productos.",
            DocumentSnapshot::toOfferProduct,
        ),
        observeDocuments(
            firestore.collection("store_product_configs").whereEqualTo("storeId", store.id),
            "No se pudo cargar la disponibilidad de los productos.",
            DocumentSnapshot::toOfferConfiguration,
        ),
    ) { categories, products, configurations ->
        val error = listOf(categories, products, configurations)
            .filterIsInstance<QueryResult.Error>().firstOrNull()
        if (error != null) {
            StoreOfferState.Error(error.message)
        } else {
            @Suppress("UNCHECKED_CAST")
            StoreOfferState.Ready(
                buildStoreOffer(
                    store = store,
                    categories = (categories as QueryResult.Success<ProductCategory>).data,
                    products = (products as QueryResult.Success<Product>).data,
                    configurations = (configurations as QueryResult.Success<StoreProductConfiguration>).data,
                ),
            )
        }
    }
}

private sealed interface QueryResult<out T> {
    data class Success<T>(val data: List<T>) : QueryResult<T>
    data class Error(val message: String) : QueryResult<Nothing>
}

private fun <T> observeDocuments(
    query: Query,
    errorMessage: String,
    mapper: (DocumentSnapshot) -> T?,
): Flow<QueryResult<T>> = callbackFlow {
    val registration = query.addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(QueryResult.Error(errorMessage))
            return@addSnapshotListener
        }
        try {
            trySend(QueryResult.Success(snapshot?.documents.orEmpty().mapNotNull(mapper)))
        } catch (_: Exception) {
            trySend(QueryResult.Error(errorMessage))
        }
    }
    awaitClose { registration.remove() }
}

internal fun DocumentSnapshot.toOfferCategory(): ProductCategory? {
    if (!exists()) return null
    return offerCategory(id, data ?: return null)
}

internal fun offerCategory(id: String, data: Map<String, Any>): ProductCategory? {
    val businessId = data["businessId"] as? String ?: return null
    val name = data["name"] as? String ?: return null
    return ProductCategory(
        id = id,
        businessId = businessId,
        name = name,
        displayOrder = (data["displayOrder"] as? Number)?.toInt() ?: 0,
        isActive = data["isActive"] as? Boolean ?: return null,
    )
}

internal fun DocumentSnapshot.toOfferProduct(): Product? {
    if (!exists()) return null
    return offerProduct(id, data ?: return null)
}

internal fun offerProduct(id: String, data: Map<String, Any>): Product? {
    return Product(
        id = id,
        businessId = data["businessId"] as? String ?: return null,
        categoryId = data["categoryId"] as? String ?: return null,
        categoryName = data["categoryName"] as? String ?: "",
        name = data["name"] as? String ?: return null,
        description = data["description"] as? String ?: "",
        price = (data["price"] as? Number)?.toDouble() ?: return null,
        imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
        prepTimeMinutes = (data["prepTimeMinutes"] as? Number)?.toInt() ?: return null,
        isActive = data["isActive"] as? Boolean ?: return null,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
    )
}

internal fun DocumentSnapshot.toOfferConfiguration(): StoreProductConfiguration? {
    if (!exists()) return null
    return StoreProductConfigurationDocumentMapper.fromDocument(
        id,
        getData(ServerTimestampBehavior.ESTIMATE) ?: return null,
    )
}
