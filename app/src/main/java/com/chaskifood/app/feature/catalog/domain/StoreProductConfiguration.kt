package com.chaskifood.app.feature.catalog.domain

/** Disponibilidad comercial de un producto dentro de un local concreto. */
enum class StoreProductAvailability {
    AVAILABLE,
    OUT_OF_STOCK,
}

/**
 * Sobrescribe el precio y la disponibilidad del producto para un único local.
 *
 * La pertenencia del negocio, local y producto se comprobará en la implementación
 * del repositorio antes de persistir cambios.
 */
data class StoreProductConfiguration(
    val businessId: String,
    val storeId: String,
    val productId: String,
    val price: Double,
    val availability: StoreProductAvailability = StoreProductAvailability.AVAILABLE,
) {
    val id: String
        get() = storeProductConfigurationId(storeId, productId)
}

enum class StoreProductConfigurationValidationError {
    BUSINESS_ID_REQUIRED,
    STORE_ID_REQUIRED,
    PRODUCT_ID_REQUIRED,
    INVALID_PRICE,
}

fun storeProductConfigurationId(storeId: String, productId: String): String =
    "${storeId}_${productId}"

fun StoreProductConfiguration.validate(): Set<StoreProductConfigurationValidationError> = buildSet {
    if (businessId.isBlank()) add(StoreProductConfigurationValidationError.BUSINESS_ID_REQUIRED)
    if (storeId.isBlank()) add(StoreProductConfigurationValidationError.STORE_ID_REQUIRED)
    if (productId.isBlank()) add(StoreProductConfigurationValidationError.PRODUCT_ID_REQUIRED)
    if (!validProductPrice(price)) add(StoreProductConfigurationValidationError.INVALID_PRICE)
}

fun StoreProductConfiguration.isValid(): Boolean = validate().isEmpty()
