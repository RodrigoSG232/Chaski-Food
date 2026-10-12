package com.chaskifood.app.feature.cart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartMoneyTest {
    @Test
    fun `precios validos se convierten exactamente a centimos`() {
        assertEquals(1L, penMinorUnitsOrNull(0.01))
        assertEquals(29L, penMinorUnitsOrNull(0.29))
        assertEquals(100L, penMinorUnitsOrNull(1.00))
        assertEquals(1250L, penMinorUnitsOrNull(12.50))
    }

    @Test
    fun `conversion rechaza precios negativos fracciones de centimo e importes fuera de rango`() {
        for (price in listOf(0.0, -1.0, 0.001, Double.NaN, Double.POSITIVE_INFINITY, 1.0E17)) {
            assertNull("Precio inválido: $price", penMinorUnitsOrNull(price))
        }
    }
}
