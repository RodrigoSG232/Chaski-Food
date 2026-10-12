package com.chaskifood.app.feature.cart.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class CartPriceFormatterTest {
    @Test fun `muestra centimos sin redondeo ni perdida en importes grandes`() {
        assertEquals("S/ 0.00", formatCartPrice(0))
        assertEquals("S/ 0.01", formatCartPrice(1))
        assertEquals("S/ 10.50", formatCartPrice(1050))
        assertEquals("S/ 92233720368547758.07", formatCartPrice(Long.MAX_VALUE))
    }
}
