package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryQuoteValidityTest {
    private val quote = DeliveryQuote(1_918.8, 380, CartCurrency.PEN, "v1",
        1_000_000L, 1_300_000L, AddressCoordinates(-12.0, -77.0), AddressCoordinates(-12.0, -77.0))
    private fun clock(wall: Long, monotonic: Long = 10_000L) = object : CheckoutClock {
        override fun wallTimeMillis() = wall
        override fun monotonicTimeMillis() = monotonic
    }

    @Test fun `cotizacion nominal reserva margen de reloj y red`() {
        assertEquals(275_000L, deliveryQuoteDeadlineOrNull(quote, clock(1_000_000L)))
        assertEquals(270_000L, deliveryQuoteDeadlineOrNull(quote, clock(1_005_000L)))
    }

    @Test fun `reloj atrasado no prolonga la vigencia y desfase excesivo bloquea`() {
        assertEquals(275_000L, deliveryQuoteDeadlineOrNull(quote, clock(970_000L)))
        assertNull(deliveryQuoteDeadlineOrNull(quote, clock(969_999L)))
    }

    @Test fun `cotizacion ya vencida o duracion anomala no es valida`() {
        assertNull(deliveryQuoteDeadlineOrNull(quote, clock(1_300_000L)))
        assertNull(deliveryQuoteDeadlineOrNull(quote.copy(expiresAtEpochMillis = 1_020_000L),
            clock(1_000_000L)))
        assertNull(deliveryQuoteDeadlineOrNull(quote.copy(expiresAtEpochMillis = 2_000_000L),
            clock(1_000_000L)))
    }
}
