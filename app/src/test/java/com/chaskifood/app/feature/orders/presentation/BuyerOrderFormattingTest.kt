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

    @Test fun paymentTextNeverClaimsPendingCashForUndeliveredOrdersAndKeepsRegisteredPayments() {
        for (status in listOf("CANCELLED", "REJECTED"))
            assertEquals("Sin cobro: el pedido no se entregó", buyerPaymentText(status, OrderPaymentStatus.PENDING))
        for (status in listOf("PENDING_CONFIRMATION", "PREPARING", "READY_FOR_PICKUP", "IN_DELIVERY", "COMPLETED", "UNKNOWN_STATE"))
            assertEquals("Efectivo pendiente hasta la entrega", buyerPaymentText(status, OrderPaymentStatus.PENDING))
        for (status in listOf("COMPLETED", "CANCELLED", "REJECTED"))
            assertEquals("Efectivo registrado · simulación", buyerPaymentText(status, OrderPaymentStatus.PAID))
    }

    @Test fun rejectionReasonsUseApprovedBuyerTextsAndNeverInventAMissingReason() {
        assertEquals("El local tiene alta demanda en este momento.", buyerRejectionReasonText(StoreOrderRejectionReason.STORE_BUSY))
        assertEquals("Uno o más productos no están disponibles.", buyerRejectionReasonText(StoreOrderRejectionReason.PRODUCT_UNAVAILABLE))
        assertEquals("El local está próximo a cerrar.", buyerRejectionReasonText(StoreOrderRejectionReason.CLOSING_SOON))
        assertEquals("Otro motivo", buyerRejectionReasonText(StoreOrderRejectionReason.OTHER))
        assertEquals("Motivo del rechazo no disponible.", buyerRejectionReasonText(null))
    }

    @Test fun rejectionBelongsOnlyToRejectedOrdersAndExplanationOnlyToOther() {
        assertEquals(StoreOrderRejectionReason.OTHER, BuyerOrderPreviewData.rejectedDetail.rejection!!.reason)
        assertThrows(IllegalArgumentException::class.java) { BuyerOrderRejection(StoreOrderRejectionReason.STORE_BUSY, "texto libre") }
        assertThrows(IllegalArgumentException::class.java) { BuyerOrderRejection(StoreOrderRejectionReason.OTHER, "ab") }
        val d = BuyerOrderPreviewData.detail
        assertThrows(IllegalArgumentException::class.java) {
            BuyerOrderDetail(d.summary, d.storePhone, d.pickupAddressText, d.delivery, d.items, d.amounts, d.paymentMethod, d.statusHistory,
                rejection = BuyerOrderRejection(null, null))
        }
    }

    @Test fun preparationEstimateShowsAClockTimeTheExtensionAndNeverClaimsReadyWhenOverdue() {
        val lima = ZoneId.of("America/Lima"); val readyAt = 1_800_000_000_000 // 03:00 en Lima
        assertEquals("03:00", formatOrderClockTime(readyAt, lima))
        val onTime = buyerPreparationEstimateText(readyAt, 15, readyAt - 60_000, lima)
        assertEquals("Hora estimada de preparación: 03:00 (aprox.)", onTime.headline)
        assertEquals("El local amplió el tiempo en 15 min.", onTime.detail)
        assertNull(buyerPreparationEstimateText(readyAt, 0, readyAt, lima).detail)
        val overdue = buyerPreparationEstimateText(readyAt, 0, readyAt + 60_000, lima).headline
        assertTrue(overdue.contains("ya pasó") && overdue.contains("sigue preparando")); assertFalse(overdue.contains("listo"))
    }

    @Test fun storeCardMarksAnOverdueEstimateAsStillPreparing() {
        val lima = ZoneId.of("America/Lima"); val readyAt = 1_800_000_000_000
        assertEquals("Listo aprox. a las 03:00", storePreparationEstimateText(readyAt, readyAt, lima))
        val overdue = storePreparationEstimateText(readyAt, readyAt + 60_000, lima)
        assertEquals("Hora estimada vencida (03:00) · sigue en preparación", overdue); assertFalse(overdue.contains("Listo"))
    }

    @Test fun extensionPreviewAddsToTheCurrentEstimateOrToNowWhenItHasPassed() {
        val readyAt = 1_800_002_400_000
        assertEquals(readyAt + 600_000, extendedPreviewEpochMillis(readyAt, readyAt - 1_200_000, 10))
        assertEquals(readyAt + 900_000 + 600_000, extendedPreviewEpochMillis(readyAt, readyAt + 900_000, 10))
    }

    @Test fun previewMustMatchTheLineCount() {
        val one = listOf(BuyerOrderItemPreview("Plato", 2))
        assertThrows(IllegalArgumentException::class.java) { summary(one, 3) }
        assertThrows(IllegalArgumentException::class.java) { summary(emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { summary(one, 3, count = 2) }
    }

    @Test fun statusesAreSpanishAndUnknownNeverBreaks() {
        val expected = mapOf("PENDING_CONFIRMATION" to "Pendiente de confirmación", "PREPARING" to "En preparación",
            "READY_FOR_PICKUP" to "Listo para recoger", "IN_DELIVERY" to "En camino", "COMPLETED" to "Finalizado",
            "CANCELLED" to "Cancelado", "REJECTED" to "Rechazado")
        expected.forEach { (status, label) -> assertEquals(label, buyerOrderStatusUi(status, true).label) }
        assertEquals("Entregado", buyerOrderStatusUi("COMPLETED", true, "SUCCESS").label)
        assertEquals("Entrega no exitosa", buyerOrderStatusUi("COMPLETED", true, "UNSUCCESSFUL").label)
        assertEquals("Estado en actualización", buyerOrderStatusUi("AWAITING_RIDER", false).label)
        // Un código conocido marcado como desconocido por el servidor tampoco se interpreta en la app.
        assertEquals("Estado en actualización", buyerOrderStatusUi("PREPARING", false).label)
    }

    @Test fun datesUseTheGivenZone() {
        assertEquals("15 ene 2027, 03:00", formatOrderDateTime(1_800_000_000_000, ZoneId.of("America/Lima")))
    }

    @Test fun previewDataIsValidForTheDomainRules() {
        assertEquals(3, BuyerOrderPreviewData.active.size); assertEquals(4, BuyerOrderPreviewData.detail.statusHistory.size)
        assertEquals("2× Lomo saltado · 1× Chicha morada 1 L · +1 más", itemPreviewText(BuyerOrderPreviewData.active[0]))
    }
}
