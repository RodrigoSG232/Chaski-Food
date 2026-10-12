package com.chaskifood.app.feature.cart.domain

import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateProductForCartTest {
    private val validate = ValidateProductForCart()

    @Test
    fun `acepta producto disponible usando el precio configurado para el local`() {
        val result = validate(
            selectedStoreId = "store-1",
            product = product(price = 9.00),
            configuration = configuration(price = 12.50),
            quantity = 2,
        )

        assertEquals(
            CartProductSnapshot(
                businessId = "business-1",
                storeId = "store-1",
                productId = "product-1",
                productName = "Ceviche",
                unitPriceMinor = 1250,
                quantity = 2,
            ),
            (result as AddToCartValidationResult.Allowed).item,
        )
    }

    @Test
    fun `rechaza producto agotado en el local seleccionado`() {
        val result = validate(
            selectedStoreId = "store-1",
            product = product(),
            configuration = configuration(availability = StoreProductAvailability.OUT_OF_STOCK),
        )

        assertRejected(AddToCartRejectionReason.OUT_OF_STOCK, result)
    }

    @Test
    fun `rechaza producto sin configuracion para el local`() {
        val result = validate("store-1", product(), configuration = null)

        assertRejected(AddToCartRejectionReason.CONFIGURATION_MISSING, result)
    }

    @Test
    fun `rechaza configuracion de otro local`() {
        val result = validate("store-1", product(), configuration(storeId = "store-2"))

        assertRejected(AddToCartRejectionReason.DIFFERENT_STORE, result)
    }

    @Test
    fun `rechaza configuracion de otro producto o negocio`() {
        val otherProduct = validate(
            "store-1",
            product(),
            configuration(productId = "product-2"),
        )
        val otherBusiness = validate(
            "store-1",
            product(),
            configuration(businessId = "business-2"),
        )

        assertRejected(AddToCartRejectionReason.DIFFERENT_PRODUCT, otherProduct)
        assertRejected(AddToCartRejectionReason.DIFFERENT_BUSINESS, otherBusiness)
    }

    @Test
    fun `rechaza configuracion con precio invalido`() {
        val result = validate("store-1", product(), configuration(price = 0.0))

        assertRejected(AddToCartRejectionReason.CONFIGURATION_INVALID, result)
        assertRejected(
            AddToCartRejectionReason.CONFIGURATION_INVALID,
            validate("store-1", product(), configuration(price = 1.0E17)),
        )
    }

    @Test
    fun `vuelve a validar si la disponibilidad cambia antes de agregar`() {
        val available = configuration()
        assertTrue(validate("store-1", product(), available) is AddToCartValidationResult.Allowed)

        val updated = available.copy(availability = StoreProductAvailability.OUT_OF_STOCK)
        assertRejected(
            AddToCartRejectionReason.OUT_OF_STOCK,
            validate("store-1", product(), updated),
        )
    }

    @Test
    fun `rechaza producto inactivo y cantidad no positiva`() {
        assertRejected(
            AddToCartRejectionReason.PRODUCT_INACTIVE,
            validate("store-1", product(isActive = false), configuration()),
        )
        assertRejected(
            AddToCartRejectionReason.INVALID_QUANTITY,
            validate("store-1", product(), configuration(), quantity = 0),
        )
        assertRejected(
            AddToCartRejectionReason.INVALID_QUANTITY,
            validate("store-1", product(), configuration(), quantity = -1),
        )
    }

    private fun assertRejected(
        expected: AddToCartRejectionReason,
        result: AddToCartValidationResult,
    ) {
        assertEquals(expected, (result as AddToCartValidationResult.Rejected).reason)
    }

    private fun product(
        price: Double = 10.00,
        isActive: Boolean = true,
    ) = Product(
        id = "product-1",
        businessId = "business-1",
        name = "Ceviche",
        price = price,
        isActive = isActive,
    )

    private fun configuration(
        businessId: String = "business-1",
        storeId: String = "store-1",
        productId: String = "product-1",
        price: Double = 12.50,
        availability: StoreProductAvailability = StoreProductAvailability.AVAILABLE,
    ) = StoreProductConfiguration(
        businessId = businessId,
        storeId = storeId,
        productId = productId,
        price = price,
        availability = availability,
    )
}
