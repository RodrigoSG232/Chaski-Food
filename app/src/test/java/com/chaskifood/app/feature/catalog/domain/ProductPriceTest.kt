package com.chaskifood.app.feature.catalog.domain

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ProductPriceTest {
    @Test fun `punto y coma representan el mismo precio`() {
        for (input in listOf("12.5", "12,5", "12.50", "12,50", " 12,50 ", "0012.50")) {
            assertEquals(input, 12.5, parseProductPrice(input).value!!, 0.0)
            assertNull(parseProductPrice(input).error)
        }
    }
    @Test fun `enteros y centavos se normalizan sin perder valor`() {
        for ((input, expected) in listOf("12" to "12.00", ".5" to "0.50", ",05" to "0.05", "0,01" to "0.01")) {
            assertEquals(expected, formatProductPrice(parseProductPrice(input).value!!))
        }
    }
    @Test fun `rechaza formato ambiguo y no lo convierte en cero`() {
        for (input in listOf("1,234.50", "1.234,50", "1,234", "12..5", "12,", "1e2", "NaN", "Infinity", "S/12", "12 50", "-12", "+12")) {
            assertNull(input, parseProductPrice(input).value)
            assertNotNull(input, parseProductPrice(input).error)
        }
    }
    @Test fun `vacio y cero tienen un mensaje propio`() {
        assertTrue(parseProductPrice(" ").error!!.contains("Ingresa"))
        for (input in listOf("0", "0.00", "0,0")) assertTrue(parseProductPrice(input).error!!.contains("mayor a 0"))
    }
    @Test fun `mas de dos decimales se rechazan sin redondear`() {
        assertNull(parseProductPrice("12.505").value)
        assertFalse(validProductPrice(12.505))
        assertTrue(validProductPrice(12.5))
    }
    @Test fun `valores enormes no pierden centavos ni se vuelven infinito`() {
        assertNull(parseProductPrice("9".repeat(400)).value)
        assertNull(parseProductPrice("9007199254740993.01").value)
        assertFalse(validProductPrice(Double.NaN))
        assertFalse(validProductPrice(Double.POSITIVE_INFINITY))
    }
    @Test fun `formato visible mantiene punto y dos decimales aunque cambie el idioma`() {
        val original = Locale.getDefault()
        try { Locale.setDefault(Locale.FRANCE); assertEquals("12.50", formatProductPrice(12.5)) }
        finally { Locale.setDefault(original) }
    }
}
