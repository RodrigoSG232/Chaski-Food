package com.chaskifood.app.feature.search.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreOfferDocumentMapperTest {

    @Test
    fun `convierte categoria publica`() {
        val category = offerCategory("category-1", mapOf(
            "businessId" to "business-1",
            "name" to "Platos marinos",
            "displayOrder" to 2L,
            "isActive" to true,
        ))!!

        assertEquals("category-1", category.id)
        assertEquals("Platos marinos", category.name)
        assertEquals(2, category.displayOrder)
    }

    @Test
    fun `convierte producto y conserva referencia https de imagen`() {
        val product = offerProduct("product-1", validProductFields())!!

        assertEquals("Ceviche", product.name)
        assertEquals("https://storage.example/ceviche.jpg", product.imageUrl)
        assertEquals(18.5, product.price, 0.0)
    }

    @Test
    fun `imagen vacia se interpreta como referencia ausente`() {
        val fields = validProductFields().toMutableMap().apply { this["imageUrl"] = "" }

        assertNull(offerProduct("product-1", fields)!!.imageUrl)
    }

    @Test
    fun `documentos esenciales incompletos se descartan`() {
        val product = validProductFields().toMutableMap().apply { remove("categoryId") }
        val category = mutableMapOf<String, Any>(
            "businessId" to "business-1",
            "name" to "Marinos",
            "isActive" to "yes",
        )

        assertNull(offerProduct("product-1", product))
        assertNull(offerCategory("category-1", category))
    }

    private fun validProductFields(): Map<String, Any> = mapOf(
        "businessId" to "business-1",
        "categoryId" to "category-1",
        "categoryName" to "Marinos",
        "name" to "Ceviche",
        "description" to "Pescado del día",
        "price" to 18.5,
        "imageUrl" to "https://storage.example/ceviche.jpg",
        "prepTimeMinutes" to 20L,
        "isActive" to true,
        "createdAt" to 123L,
    )
}
