package com.chaskifood.app.feature.address.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressValidatorTest {
    @Test
    fun `borrador vacio informa direccion y ubicacion obligatorias`() {
        val result = AddressValidator.validate(AddressDraft())

        assertFalse(result.isValid)
        assertEquals(
            mapOf(
                AddressField.ADDRESS_TEXT to AddressValidationError.REQUIRED,
                AddressField.LOCATION to AddressValidationError.REQUIRED,
            ),
            result.errors,
        )
    }

    @Test
    fun `campos opcionales vacios y coordenadas cero son validos`() {
        assertTrue(AddressValidator.validate(validDraft()).isValid)
    }

    @Test
    fun `direccion de solo espacios o saltos de linea es invalida`() {
        listOf("", "   ", "\t\r\n", "\u00a0").forEach { text ->
            val result = AddressValidator.validate(validDraft().copy(addressText = text))
            assertEquals(AddressValidationError.REQUIRED, result.errors[AddressField.ADDRESS_TEXT])
        }
    }

    @Test
    fun `normaliza extremos de direccion sin modificar borrador original`() {
        val draft = validDraft().copy(addressText = " \tCalle  ficticia 1\n ")
        val result = AddressValidator.validate(draft)

        assertTrue(result.isValid)
        assertEquals("Calle  ficticia 1", result.normalizedDraft.addressText)
        assertEquals(" \tCalle  ficticia 1\n ", draft.addressText)
        assertEquals(result, AddressValidator.validate(result.normalizedDraft))
    }

    @Test
    fun `conserva etiqueta referencia e instrucciones sin truncar ni borrar espacios`() {
        val draft = validDraft().copy(
            label = " Casa ",
            reference = " Frente al punto ficticio ",
            instructions = " Primera indicación\nSegunda indicación ",
        )

        assertEquals(draft, AddressValidator.validate(draft).normalizedDraft)
    }

    @Test
    fun `acepta exactamente los cuatro limites acordados`() {
        val draft = validDraft().copy(
            label = "a".repeat(40),
            addressText = "a".repeat(250),
            reference = "a".repeat(250),
            instructions = "a".repeat(500),
        )

        assertTrue(AddressValidator.validate(draft).isValid)
    }

    @Test
    fun `rechaza cada campo que supera su limite sin truncarlo`() {
        val cases = listOf(
            AddressField.LABEL to validDraft().copy(label = "a".repeat(41)),
            AddressField.ADDRESS_TEXT to validDraft().copy(addressText = "a".repeat(251)),
            AddressField.REFERENCE to validDraft().copy(reference = "a".repeat(251)),
            AddressField.INSTRUCTIONS to validDraft().copy(instructions = "a".repeat(501)),
        )
        cases.forEach { (field, draft) ->
            val result = AddressValidator.validate(draft)
            assertFalse(result.isValid)
            assertEquals(mapOf(field to AddressValidationError.TOO_LONG), result.errors)
            assertEquals(draft, result.normalizedDraft)
        }
    }

    @Test
    fun `devuelve todos los errores del formulario en una validacion`() {
        val result = AddressValidator.validate(
            AddressDraft(
                label = "a".repeat(41),
                reference = "a".repeat(251),
                instructions = "a".repeat(501),
            ),
        )

        assertEquals(AddressField.entries.toSet(), result.errors.keys)
    }

    @Test
    fun `limite de direccion se aplica al texto que se guardara normalizado`() {
        val result = AddressValidator.validate(validDraft().copy(addressText = " ${"a".repeat(250)} "))

        assertTrue(result.isValid)
        assertEquals(250, result.normalizedDraft.addressText.length)
    }

    @Test
    fun `latitud y longitud aceptan extremos inclusivos`() {
        listOf(-90.0, 0.0, 90.0).forEach { latitude ->
            listOf(-180.0, 0.0, 180.0).forEach { longitude ->
                val draft = validDraft().copy(location = AddressCoordinates(latitude, longitude))
                assertTrue(AddressValidator.validate(draft).isValid)
            }
        }
    }

    @Test
    fun `rechaza latitud fuera de rango o no finita`() {
        listOf(-90.0001, 90.0001, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            .forEach { latitude ->
                assertInvalidCoordinates(AddressCoordinates(latitude, 0.0))
            }
    }

    @Test
    fun `rechaza longitud fuera de rango o no finita`() {
        listOf(-180.0001, 180.0001, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            .forEach { longitude ->
                assertInvalidCoordinates(AddressCoordinates(0.0, longitude))
            }
    }

    @Test
    fun `ubicacion ausente no se reemplaza por coordenadas cero`() {
        val result = AddressValidator.validate(validDraft().copy(location = null))

        assertEquals(AddressValidationError.REQUIRED, result.errors[AddressField.LOCATION])
        assertEquals(null, result.normalizedDraft.location)
    }

    @Test
    fun `ubicacion valida requiere confirmacion explicita`() {
        val result = AddressValidator.validate(validDraft().copy(isLocationConfirmed = false))

        assertEquals(AddressValidationError.LOCATION_NOT_CONFIRMED, result.errors[AddressField.LOCATION])
        assertFalse(result.isValid)
    }

    @Test
    fun `etiqueta con emojis usa unidades utf16 como el emulador de reglas`() {
        val emoji = "\uD83C\uDFE0"

        assertTrue(AddressValidator.validate(validDraft().copy(label = emoji.repeat(20))).isValid)
        assertEquals(
            AddressValidationError.TOO_LONG,
            AddressValidator.validate(validDraft().copy(label = emoji.repeat(21))).errors[AddressField.LABEL],
        )
    }

    @Test
    fun `tildes y caracteres combinados mantienen conteo sin normalizacion destructiva`() {
        assertTrue(AddressValidator.validate(validDraft().copy(label = "á".repeat(40))).isValid)
        assertTrue(AddressValidator.validate(validDraft().copy(label = "a\u0301".repeat(20))).isValid)
        assertFalse(AddressValidator.validate(validDraft().copy(label = "a\u0301".repeat(21))).isValid)
    }

    private fun assertInvalidCoordinates(location: AddressCoordinates) {
        val result = AddressValidator.validate(validDraft().copy(location = location))
        assertEquals(AddressValidationError.INVALID_COORDINATES, result.errors[AddressField.LOCATION])
        assertFalse(result.isValid)
    }

    private fun validDraft() = AddressDraft(
        addressText = "Dirección ficticia de prueba",
        location = AddressCoordinates(0.0, 0.0),
        isLocationConfirmed = true,
    )
}
