package com.chaskifood.app.feature.search.domain

import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.isValid
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import java.text.Normalizer
import java.util.Locale

data class OfferCategory(
    val id: String,
    val name: String,
    val displayOrder: Int,
)

/** Producto comprable y contextualizado en un local concreto. */
data class StoreOfferProduct(
    val id: String,
    val businessId: String,
    val storeId: String,
    val storeName: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String?,
    val prepTimeMinutes: Int,
)

data class StoreOffer(
    val store: DiscoverableStore,
    val categories: List<OfferCategory>,
    val products: List<StoreOfferProduct>,
)

data class OfferSearchResults(
    val stores: List<DiscoverableStore>,
    val products: List<StoreOfferProduct>,
)

/**
 * Construye la oferta pública usando únicamente productos activos con una configuración
 * AVAILABLE válida para el local. El precio base del catálogo nunca se expone como precio local.
 */
fun buildStoreOffer(
    store: DiscoverableStore,
    categories: List<ProductCategory>,
    products: List<Product>,
    configurations: List<StoreProductConfiguration>,
): StoreOffer {
    val activeCategories = categories
        .filter { it.businessId == store.businessId && it.isActive }
        .associateBy { it.id }
    val configurationsByProduct = configurations
        .filter { configuration ->
            configuration.businessId == store.businessId &&
                configuration.storeId == store.id &&
                configuration.isValid() &&
                configuration.availability == StoreProductAvailability.AVAILABLE
        }
        .associateBy { it.productId }

    val availableProducts = products.mapNotNull { product ->
        if (!product.isActive || product.businessId != store.businessId) return@mapNotNull null
        val category = activeCategories[product.categoryId] ?: return@mapNotNull null
        val configuration = configurationsByProduct[product.id] ?: return@mapNotNull null
        StoreOfferProduct(
            id = product.id,
            businessId = product.businessId,
            storeId = store.id,
            storeName = store.name,
            categoryId = category.id,
            categoryName = category.name,
            name = product.name,
            description = product.description,
            price = configuration.price,
            imageUrl = product.imageUrl,
            prepTimeMinutes = product.prepTimeMinutes,
        )
    }.sortedWith(compareBy(StoreOfferProduct::categoryName, StoreOfferProduct::name))

    val availableCategoryIds = availableProducts.mapTo(mutableSetOf()) { it.categoryId }
    val offerCategories = activeCategories.values
        .filter { it.id in availableCategoryIds }
        .sortedWith(compareBy(ProductCategory::displayOrder, ProductCategory::name))
        .map { OfferCategory(it.id, it.name, it.displayOrder) }

    return StoreOffer(store, offerCategories, availableProducts)
}

fun searchStoreOffers(
    offers: List<StoreOffer>,
    query: String,
    categoryId: String? = null,
): OfferSearchResults {
    val normalizedQuery = query.searchText()
    val filteredProducts = offers.flatMap { it.products }.filter { product ->
        (categoryId == null || product.categoryId == categoryId) &&
            (normalizedQuery.isEmpty() || listOf(
                product.name,
                product.description,
                product.categoryName,
                product.storeName,
            ).any { normalizedQuery in it.searchText() })
    }
    val matchingStoreIdsFromProducts = filteredProducts.mapTo(mutableSetOf()) { it.storeId }
    val filteredStores = offers.filter { offer ->
        val supportsCategory = categoryId == null || offer.products.any { it.categoryId == categoryId }
        val directMatch = normalizedQuery.isEmpty() || listOf(
            offer.store.name,
            offer.store.businessName,
            offer.store.businessCategory,
            offer.store.address,
        ).any { normalizedQuery in it.searchText() }
        supportsCategory && (directMatch || offer.store.id in matchingStoreIdsFromProducts)
    }.map { it.store }

    return OfferSearchResults(
        stores = filteredStores.distinctBy { it.id }.sortedBy { it.distanceKm },
        products = filteredProducts.sortedWith(
            compareBy(StoreOfferProduct::storeName, StoreOfferProduct::categoryName, StoreOfferProduct::name),
        ),
    )
}

private fun String.searchText(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
