package com.chaskifood.app.feature.address.data

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class DeliveryMetadataTest {
    @Test
    fun `primera alta fija predeterminada y posteriores la conservan`() {
        val first = DeliveryMetadata.fromDocument(null).afterCreate("A")
        assertEquals(DeliveryMetadata(1, "A"), first)
        assertEquals(DeliveryMetadata(2, "A"), first.afterCreate("B"))
    }

    @Test
    fun `eliminar ultima limpia predeterminada`() {
        assertEquals(DeliveryMetadata(0, null), DeliveryMetadata(1, "A").afterDelete("A", null))
    }

    @Test
    fun `eliminar predeterminada exige reemplazo distinto`() {
        val state = DeliveryMetadata(2, "A")
        assertThrows(IllegalArgumentException::class.java) { state.afterDelete("A", null) }
        assertThrows(IllegalArgumentException::class.java) { state.afterDelete("A", "A") }
        assertEquals(DeliveryMetadata(1, "B"), state.afterDelete("A", "B"))
    }

    @Test
    fun `eliminar no predeterminada conserva preferencia y rechaza reemplazo innecesario`() {
        val state = DeliveryMetadata(2, "A")
        assertEquals(DeliveryMetadata(1, "A"), state.afterDelete("B", null))
        assertThrows(IllegalArgumentException::class.java) { state.afterDelete("B", "A") }
    }

    @Test
    fun `rechaza operaciones incompatibles con contador`() {
        assertThrows(IllegalStateException::class.java) { DeliveryMetadata(0, null).afterDelete("A", null) }
        assertThrows(IllegalStateException::class.java) { DeliveryMetadata(1, "A").afterDelete("B", null) }
        assertThrows(IllegalArgumentException::class.java) { DeliveryMetadata(1, "A").afterDelete("A", "B") }
        assertThrows(IllegalStateException::class.java) { DeliveryMetadata(Long.MAX_VALUE, "A").afterCreate("B") }
    }

    @Test
    fun `esquema legado no se migra implicitamente`() {
        assertThrows(IllegalArgumentException::class.java) { DeliveryMetadata.fromDocument(mapOf("defaultAddressId" to "A")) }
    }

    @Test
    fun `contador requiere entero no negativo y referencia consistente`() {
        val base = mapOf("addressCount" to 1L, "defaultAddressId" to "A", "lastOperationId" to "op", "updatedAt" to Timestamp(1, 0))
        assertEquals(DeliveryMetadata(1, "A"), DeliveryMetadata.fromDocument(base))
        listOf(-1L, 1.0, "1", null).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { DeliveryMetadata.fromDocument(base + ("addressCount" to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { DeliveryMetadata.fromDocument(base + ("defaultAddressId" to null)) }
        assertThrows(IllegalArgumentException::class.java) { DeliveryMetadata.fromDocument(base + ("addressCount" to 0L)) }
    }
}
