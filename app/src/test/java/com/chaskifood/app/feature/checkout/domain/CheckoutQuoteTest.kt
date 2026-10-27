package com.chaskifood.app.feature.checkout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutQuoteTest {
    @Test fun `recalcula subtotal y total con precios vigentes y muestra cambios`() {
        val quote = quote(listOf(
            CheckoutPricedItem("a", "Plato", 2, 1000, 1250),
            CheckoutPricedItem("b", "Bebida", 1, 500, 500),
        ))
        assertEquals(3000L, quote.subtotalMinor)
        assertEquals(380L, quote.deliveryFeeMinor)
        assertEquals(3380L, quote.totalMinor)
        assertTrue(quote.hasPriceChanges)
        assertEquals(2500L, quote.lines.first().lineTotalMinor)
        assertEquals(1000L, quote.lines.first().previousUnitPriceMinor)
    }

    @Test fun `sin cambios de precio conserva la revision limpia`() {
        val quote = quote(listOf(CheckoutPricedItem("a", "Plato", 1, 1250, 1250)))
        assertFalse(quote.hasPriceChanges)
        assertEquals(1630L, quote.totalMinor)
    }

    @Test fun `rechaza cantidad invalida e importes que desbordan`() {
        assertThrows(IllegalArgumentException::class.java) {
            quote(listOf(CheckoutPricedItem("a", "Plato", 0, 1000, 1000)))
        }
        assertThrows(ArithmeticException::class.java) {
            quote(listOf(CheckoutPricedItem("a", "Plato", 2, 1000, Long.MAX_VALUE)))
        }
        assertThrows(ArithmeticException::class.java) {
            buildCheckoutQuote("customer", "store", "Local", "address", "Casa", "Lima",
                listOf(CheckoutPricedItem("a", "Plato", 1, 1250, 1250)),
                DeliveryQuote(1.0, Long.MAX_VALUE,
                    com.chaskifood.app.feature.cart.domain.CartCurrency.PEN, "v1", 1_000_000L, 1_300_000L,
                    com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0),
                    com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0)),
                1_265_000L)
        }
    }

    private fun quote(items: List<CheckoutPricedItem>) = buildCheckoutQuote(
        ownerUid = "customer", storeId = "store", storeName = "Local",
        addressId = "address", addressLabel = "Casa", addressText = "Lima",
        pricedItems = items,
        deliveryQuote = DeliveryQuote(1_918.8, 380, com.chaskifood.app.feature.cart.domain.CartCurrency.PEN,
            "lima-driving-car-v1", 1_000_000L, 1_300_000L,
            com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0),
            com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0)),
        deliveryQuoteDeadlineMillis = 1_265_000L,
    )
}
