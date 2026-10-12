package com.chaskifood.app.feature.cart.domain

import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.isValid

enum class AddToCartRejectionReason {
    INVALID_QUANTITY,
    PRODUCT_INACTIVE,
    CONFIGURATION_MISSING,
    CONFIGURATION_INVALID,
    DIFFERENT_BUSINESS,
    DIFFERENT_STORE,
    DIFFERENT_PRODUCT,
    OUT_OF_STOCK,
}

sealed interface AddToCartValidationResult {
    data class Allowed(val item: CartProductSnapshot) : AddToCartValidationResult

    data class Rejected(val reason: AddToCartRejectionReason) : AddToCartValidationResult
}

/**
 * Comprueba el estado vigente del producto para el local antes de incorporarlo al carrito.
 * El precio aceptado siempre proviene de la configuración del local, nunca del catálogo base.
 */
class ValidateProductForCart {
    operator fun invoke(
        selectedStoreId: String,
        product: Product,
        configuration: StoreProductConfiguration?,
        quantity: Int = 1,
    ): AddToCartValidationResult {
        if (quantity <= 0) return rejected(AddToCartRejectionReason.INVALID_QUANTITY)
        if (!product.isActive) return rejected(AddToCartRejectionReason.PRODUCT_INACTIVE)
        if (configuration == null) return rejected(AddToCartRejectionReason.CONFIGURATION_MISSING)
        if (!configuration.isValid()) return rejected(AddToCartRejectionReason.CONFIGURATION_INVALID)
        if (configuration.businessId != product.businessId) {
            return rejected(AddToCartRejectionReason.DIFFERENT_BUSINESS)
        }
        if (configuration.storeId != selectedStoreId) {
            return rejected(AddToCartRejectionReason.DIFFERENT_STORE)
        }
        if (configuration.productId != product.id) {
            return rejected(AddToCartRejectionReason.DIFFERENT_PRODUCT)
        }
        if (configuration.availability == StoreProductAvailability.OUT_OF_STOCK) {
            return rejected(AddToCartRejectionReason.OUT_OF_STOCK)
        }
        val unitPriceMinor = penMinorUnitsOrNull(configuration.price)
            ?: return rejected(AddToCartRejectionReason.CONFIGURATION_INVALID)

        return AddToCartValidationResult.Allowed(
            CartProductSnapshot(
                businessId = product.businessId,
                storeId = configuration.storeId,
                productId = product.id,
                productName = product.name,
                unitPriceMinor = unitPriceMinor,
                quantity = quantity,
            ),
        )
    }

    private fun rejected(reason: AddToCartRejectionReason) =
        AddToCartValidationResult.Rejected(reason)
}
