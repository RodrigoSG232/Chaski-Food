package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.*
import org.junit.Test

class AddressDocumentMapperTest {
    @Test
    fun `lee mapa lat lng enteros o decimales y timestamps reales`() {
        val data = document().toMutableMap()
        data["location"] = mapOf("lat" to 0L, "lng" to 1.5)
        val address = AddressDocumentMapper.fromDocument("A", data)
        assertEquals(AddressCoordinates(0.0, 1.5), address.location)
        assertEquals(1_000L, address.createdAtEpochMillis)
        assertEquals(2_000L, address.updatedAtEpochMillis)
    }

    @Test
    fun `alta normaliza direccion y envia exactamente siete campos con fechas del servidor`() {
        val fields = AddressDocumentMapper.createFields(draft().copy(addressText = " Calle ficticia "))
        assertEquals(setOf("label", "addressText", "location", "reference", "instructions", "createdAt", "updatedAt"), fields.keys)
        assertEquals("Calle ficticia", fields["addressText"])
        assertEquals(mapOf("lat" to 0.0, "lng" to 0.0), fields["location"])
        assertEquals(FieldValue.serverTimestamp(), fields["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), fields["updatedAt"])
        assertEquals("", fields["reference"])
    }

    @Test
    fun `edicion nunca escribe createdAt ni campos de identidad`() {
        val fields = AddressDocumentMapper.updateFields(draft())
        assertFalse(fields.containsKey("createdAt"))
        assertFalse(fields.containsKey("id"))
        assertFalse(fields.containsKey("ownerUid"))
        assertEquals(6, fields.size)
    }

    @Test
    fun `rechaza cada campo ausente y campos extra`() {
        document().keys.forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { AddressDocumentMapper.fromDocument("A", document() - key) }
        }
        assertThrows(IllegalArgumentException::class.java) { AddressDocumentMapper.fromDocument("A", document() + ("ownerUid" to "B")) }
    }

    @Test
    fun `rechaza json de timestamp null y fechas invertidas`() {
        listOf(null, "fecha", mapOf("seconds" to 1L)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                AddressDocumentMapper.fromDocument("A", document() + ("createdAt" to value))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AddressDocumentMapper.fromDocument("A", document() + ("updatedAt" to Timestamp(0, 0)))
        }
    }

    @Test
    fun `rechaza coordenadas mal formadas no finitas y nombres anteriores`() {
        listOf(
            null, mapOf("latitude" to 0.0, "longitude" to 0.0),
            mapOf("lat" to "0", "lng" to 0), mapOf("lat" to Double.NaN, "lng" to 0),
            mapOf("lat" to 91, "lng" to 0), mapOf("lat" to 0, "lng" to 181),
            mapOf("lat" to 0, "lng" to 0, "extra" to 1),
        ).forEach { location ->
            assertThrows(IllegalArgumentException::class.java) {
                AddressDocumentMapper.fromDocument("A", document() + ("location" to location))
            }
        }
    }

    @Test
    fun `rechaza textos invalidos sin descartarlos silenciosamente`() {
        listOf("", " a ", "a".repeat(251), 123).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                AddressDocumentMapper.fromDocument("A", document() + ("addressText" to value))
            }
        }
    }

    @Test
    fun `borrador sin punto confirmado no se serializa`() {
        assertThrows(IllegalArgumentException::class.java) { AddressDocumentMapper.createFields(draft().copy(isLocationConfirmed = false)) }
    }

    @Test
    fun `identificadores no pueden escapar de su segmento`() {
        listOf("", " ", ".", "..", "A/B", "__reserved__", "a".repeat(1501)).forEach {
            assertThrows(IllegalArgumentException::class.java) { requireDocumentId(it) }
        }
        requireDocumentId("direccion-A_1")
    }

    @Test
    fun `huella es estable normaliza texto y distingue campos y coordenadas`() {
        assertEquals(addressPayloadHash(draft()), addressPayloadHash(draft().copy(addressText = " Calle ficticia ")))
        assertEquals(64, addressPayloadHash(draft()).length)
        assertNotEquals(addressPayloadHash(draft()), addressPayloadHash(draft().copy(instructions = "Otra instrucción")))
        assertNotEquals(addressPayloadHash(draft()), addressPayloadHash(draft().copy(location = AddressCoordinates(1.0, 0.0))))
        assertNotEquals(addressPayloadHash(draft().copy(label = "ab", reference = "c")), addressPayloadHash(draft().copy(label = "a", reference = "bc")))
    }

    private fun draft() = AddressDraft(addressText = "Calle ficticia", location = AddressCoordinates(0.0, 0.0), isLocationConfirmed = true)

    private fun document(): Map<String, Any?> = mapOf(
        "label" to "", "addressText" to "Calle ficticia", "location" to mapOf("lat" to 0.0, "lng" to 0.0),
        "reference" to "", "instructions" to "", "createdAt" to Timestamp(1, 0), "updatedAt" to Timestamp(2, 0),
    )
}
