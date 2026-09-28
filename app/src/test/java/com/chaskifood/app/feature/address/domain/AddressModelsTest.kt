package com.chaskifood.app.feature.address.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressModelsTest {
    @Test
    fun `nuevo punto queda pendiente de confirmacion`() {
        val draft = AddressDraft().withLocation(AddressCoordinates(0.0, 0.0))

        assertFalse(draft.isLocationConfirmed)
        assertTrue(draft.confirmLocation().isLocationConfirmed)
    }

    @Test
    fun `mover un punto confirmado invalida su confirmacion`() {
        val original = AddressDraft().withLocation(AddressCoordinates(0.0, 0.0)).confirmLocation()
        val moved = original.withLocation(AddressCoordinates(1.0, 1.0))

        assertFalse(moved.isLocationConfirmed)
        assertEquals(AddressCoordinates(1.0, 1.0), moved.location)
        assertTrue(original.isLocationConfirmed)
    }

    @Test
    fun `quitar ubicacion invalida su confirmacion`() {
        val draft = AddressDraft().withLocation(AddressCoordinates(0.0, 0.0)).confirmLocation()
        val removed = draft.withLocation(null)

        assertEquals(null, removed.location)
        assertFalse(removed.isLocationConfirmed)
    }

    @Test
    fun `no confirma un punto ausente ni invalido`() {
        listOf(null, AddressCoordinates(91.0, 0.0), AddressCoordinates(0.0, Double.NaN))
            .forEach { location ->
                assertFalse(AddressDraft(location = location).confirmLocation().isLocationConfirmed)
            }
    }

    @Test
    fun `borrador de edicion conserva campos y no muta direccion guardada`() {
        val original = Address(
            id = "direccion-prueba",
            label = "Casa ficticia",
            addressText = "Dirección ficticia",
            location = AddressCoordinates(0.0, 0.0),
            reference = "Referencia de prueba",
            instructions = "Instrucciones de prueba",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )
        val draft = original.toDraft()
        val edited = draft.copy(addressText = "Otra dirección ficticia")

        assertEquals(original.label, draft.label)
        assertEquals(original.addressText, draft.addressText)
        assertEquals(original.location, draft.location)
        assertEquals(original.reference, draft.reference)
        assertEquals(original.instructions, draft.instructions)
        assertTrue(draft.isLocationConfirmed)
        assertTrue(AddressValidator.validate(draft).isValid)
        assertEquals("Otra dirección ficticia", edited.addressText)
        assertEquals("Dirección ficticia", original.addressText)
        assertEquals(1_000L, original.createdAtEpochMillis)
        assertEquals(2_000L, original.updatedAtEpochMillis)
    }
}
