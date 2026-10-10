package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreProductConfigurationDocumentMapperTest {

    @Test
    fun `serializa identidad precio disponibilidad y auditoria`() {
        val fields = StoreProductConfigurationDocumentMapper.toFields(configuration(), "user-1")

        assertEquals(
            setOf("businessId", "storeId", "productId", "price", "availability", "updatedAt", "updatedBy"),
            fields.keys,
        )
        assertEquals(12.50, fields["price"])
        assertEquals("OUT_OF_STOCK", fields["availability"])
        assertEquals(FieldValue.serverTimestamp(), fields["updatedAt"])
        assertEquals("user-1", fields["updatedBy"])
    }

    @Test
    fun `restaura una configuracion valida`() {
        val configuration = StoreProductConfigurationDocumentMapper.fromDocument("store-1_product-1", document())

        assertEquals(configuration(), configuration)
    }

    @Test
    fun `rechaza documento cuyo id no corresponde a local y producto`() {
        assertThrows(IllegalArgumentException::class.java) {
            StoreProductConfigurationDocumentMapper.fromDocument("otro-id", document())
        }
    }

    @Test
    fun `rechaza disponibilidad precio auditoria o campos desconocidos`() {
        val invalidDocuments = listOf(
            document() + ("availability" to "UNKNOWN"),
            document() + ("price" to 10.999),
            document() + ("updatedBy" to ""),
            document() + ("updatedAt" to null),
            document() + ("extra" to true),
        )

        invalidDocuments.forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                StoreProductConfigurationDocumentMapper.fromDocument("store-1_product-1", invalid)
            }
        }
    }

    @Test
    fun `solo acepta local y producto del negocio configurado`() {
        requireConfigurationRelationships(configuration(), "business-1", "business-1")

        val wrongStore = assertThrows(IllegalStateException::class.java) {
            requireConfigurationRelationships(configuration(), "business-2", "business-1")
        }
        val wrongProduct = assertThrows(IllegalStateException::class.java) {
            requireConfigurationRelationships(configuration(), "business-1", "business-2")
        }
        assertTrue(wrongStore.message!!.contains("local", ignoreCase = true))
        assertTrue(wrongProduct.message!!.contains("producto", ignoreCase = true))
    }

    private fun configuration() = StoreProductConfiguration(
        businessId = "business-1",
        storeId = "store-1",
        productId = "product-1",
        price = 12.50,
        availability = StoreProductAvailability.OUT_OF_STOCK,
    )

    private fun document(): Map<String, Any?> = mapOf(
        "businessId" to "business-1",
        "storeId" to "store-1",
        "productId" to "product-1",
        "price" to 12.50,
        "availability" to "OUT_OF_STOCK",
        "updatedAt" to Timestamp(1, 0),
        "updatedBy" to "user-1",
    )
}
