package com.chaskifood.app.feature.orders.presentation

import com.chaskifood.app.feature.orders.domain.*
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BuyerOrderFormattingTest {
    private val id = "7f3a9c21" + "0".repeat(56)
    private fun summary(preview: List<BuyerOrderItemPreview>, lines: Int, count: Int = 9) = BuyerOrderSummary(id, 1_800_000_000_000,
        "PREPARING", true, BuyerOrderPhase.ACTIVE, "Local", "Negocio", count, 1000, preview, lines)

    @Test fun shortIdIsOnlyForDisplay() {
        assertEquals("7F3A9C21", shortOrderId(id)); assertEquals(64, id.length)
    }

    @Test fun previewShowsUpToTwoLinesAndCountsTheRest() {
        val two = listOf(BuyerOrderItemPreview("Plato", 2), BuyerOrderItemPreview("Bebida", 1))
        assertEquals("2× Plato · 1× Bebida · +3 más", itemPreviewText(summary(two, 5)))
        assertEquals("2× Plato · 1× Bebida", itemPreviewText(summary(two, 2)))
        assertEquals("2× Plato", itemPreviewText(summary(two.take(1), 1)))
    }

    @Test fun previewMustMatchTheLineCount() {
        val one = listOf(BuyerOrderItemPreview("Plato", 2))
        assertThrows(IllegalArgumentException::class.java) { summary(one, 3) }
        assertThrows(IllegalArgumentException::class.java) { summary(emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { summary(one, 3, count = 2) }
    }

    @Test fun statusesAreSpanishAndUnknownNeverBreaks() {
        val expected = mapOf("PENDING_CONFIRMATION" to "Pendiente de confirmación", "PREPARING" to "En preparación",
            "READY_FOR_PICKUP" to "Listo para recoger", "IN_DELIVERY" to "En camino", "COMPLETED" to "Entregado",
            "CANCELLED" to "Cancelado", "REJECTED" to "Rechazado")
        expected.forEach { (status, label) -> assertEquals(label, buyerOrderStatusUi(status, true).label) }
        assertEquals("Estado en actualización", buyerOrderStatusUi("AWAITING_RIDER", false).label)
        // Un código conocido marcado como desconocido por el servidor tampoco se interpreta en la app.
        assertEquals("Estado en actualización", buyerOrderStatusUi("PREPARING", false).label)
    }

    @Test fun datesUseTheGivenZone() {
        assertEquals("15 ene 2027, 03:00", formatOrderDateTime(1_800_000_000_000, ZoneId.of("America/Lima")))
    }
}
