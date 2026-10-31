package com.chaskifood.app.feature.orders.presentation

import com.chaskifood.app.feature.orders.domain.*

/** Datos ficticios solo para @Preview (HU17): sin Hilt, Firebase ni red. */
internal object BuyerOrderPreviewData {
    private const val T = 1_800_000_000_000L
    private fun id(prefix: String) = prefix + "0".repeat(64 - prefix.length)
    private fun p(vararg lines: Pair<String, Int>) = lines.map { BuyerOrderItemPreview(it.first, it.second) }

    val active = listOf(
        BuyerOrderSummary(id("7f3a9c21"), T, "IN_DELIVERY", true, BuyerOrderPhase.ACTIVE, "Local de ejemplo · Centro",
            "Negocio de ejemplo", 4, 4600, p("Lomo saltado" to 2, "Chicha morada 1 L" to 1), 3),
        BuyerOrderSummary(id("b41e07d5"), T - 1_500_000, "PENDING_CONFIRMATION", true, BuyerOrderPhase.ACTIVE, "Pollería Imaginaria · Sur",
            "Negocio de ejemplo", 1, 3250, p("Pollo a la brasa ¼" to 1), 1),
        BuyerOrderSummary(id("2c9d41aa"), T - 60_000_000, "AWAITING_RIDER", false, BuyerOrderPhase.ACTIVE, "Local de ejemplo · Norte",
            "Negocio de ejemplo", 2, 1800, p("Ají de gallina" to 1, "Gaseosa 500 ml" to 1), 2),
    )
    val historical = listOf(
        BuyerOrderSummary(id("9a07c3e1"), T - 300_000_000, "COMPLETED", true, BuyerOrderPhase.HISTORICAL, "Local de ejemplo · Centro",
            "Negocio de ejemplo", 3, 5400, p("Ceviche clásico" to 2, "Chicha morada 1 L" to 1), 2),
        BuyerOrderSummary(id("4d1f88b0"), T - 600_000_000, "CANCELLED", true, BuyerOrderPhase.HISTORICAL, "Pollería Imaginaria · Sur",
            "Negocio de ejemplo", 2, 2800, p("Pollo a la brasa ¼" to 1, "Papas fritas" to 1), 2),
        BuyerOrderSummary(id("c3e2a915"), T - 900_000_000, "REJECTED", true, BuyerOrderPhase.HISTORICAL, "Sanguchería de Prueba",
            "Negocio de ejemplo", 2, 1950, p("Pan con chicharrón" to 1, "Jugo de papaya" to 1), 2),
    )

    fun list(phase: BuyerOrderPhase, state: BuyerOrdersPhaseState) =
        BuyerOrdersUiState(selected = phase, phases = BuyerOrderPhase.entries.associateWith { if (it == phase) state else BuyerOrdersPhaseState() })

    val detail = BuyerOrderDetail(active[0], "900 000 000", "Av. Ficticia 123, Cercado (ejemplo)",
        BuyerOrderDelivery("Casa", "Calle Imaginaria 456, dpto. 2B", "Frente al parque de ejemplo", "Tocar el timbre"),
        listOf(StoreOrderLine("p1", "Lomo saltado", 2, 1500, 3000), StoreOrderLine("p2", "Chicha morada 1 L", 1, 800, 800),
            StoreOrderLine("p3", "Porción de yuca", 1, 300, 300)),
        OrderAmounts(4100, 500, 4600), OrderPaymentMethod.CASH_ON_DELIVERY,
        listOf(BuyerOrderStatusEntry("PENDING_CONFIRMATION", true, T), BuyerOrderStatusEntry("PREPARING", true, T + 240_000),
            BuyerOrderStatusEntry("READY_FOR_PICKUP", true, T + 1_560_000), BuyerOrderStatusEntry("IN_DELIVERY", true, T + 1_980_000)))
}
