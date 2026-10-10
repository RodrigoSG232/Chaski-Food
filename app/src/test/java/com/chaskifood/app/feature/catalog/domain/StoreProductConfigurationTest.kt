package com.chaskifood.app.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreProductConfigurationTest {

    @Test
    fun `genera un identificador estable por local y producto`() {
        val configuration = validConfiguration(storeId = "store-1", productId = "product-8")

        assertEquals("store-1_product-8", configuration.id)
        assertEquals(configuration.id, storeProductConfigurationId("store-1", "product-8"))
    }

    @Test
    fun `un producto conserva configuraciones independientes por local`() {
        val firstStore = validConfiguration(storeId = "store-1", price = 12.50)
        val secondStore = validConfiguration(
            storeId = "store-2",
            price = 15.00,
            availability = StoreProductAvailability.OUT_OF_STOCK,
        )

        assertFalse(firstStore.id == secondStore.id)
        assertEquals(12.50, firstStore.price, 0.0)
        assertEquals(StoreProductAvailability.AVAILABLE, firstStore.availability)
        assertEquals(15.00, secondStore.price, 0.0)
        assertEquals(StoreProductAvailability.OUT_OF_STOCK, secondStore.availability)
    }

    @Test
    fun `acepta identificadores completos y precio comercial valido`() {
        val configuration = validConfiguration(price = 0.01)

        assertTrue(configuration.validate().isEmpty())
        assertTrue(configuration.isValid())
    }

    @Test
    fun `informa todos los identificadores obligatorios ausentes`() {
        val errors = validConfiguration(
            businessId = " ",
            storeId = "",
            productId = "\t",
        ).validate()

        assertEquals(
            setOf(
                StoreProductConfigurationValidationError.BUSINESS_ID_REQUIRED,
                StoreProductConfigurationValidationError.STORE_ID_REQUIRED,
                StoreProductConfigurationValidationError.PRODUCT_ID_REQUIRED,
            ),
            errors,
        )
    }

    @Test
    fun `rechaza precios no positivos no finitos o con mas de dos decimales`() {
        for (price in listOf(0.0, -1.0, 10.999, Double.NaN, Double.POSITIVE_INFINITY)) {
            val configuration = validConfiguration(price = price)

            assertTrue(
                "Debía rechazar $price",
                StoreProductConfigurationValidationError.INVALID_PRICE in configuration.validate(),
            )
            assertFalse(configuration.isValid())
        }
    }

    private fun validConfiguration(
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
