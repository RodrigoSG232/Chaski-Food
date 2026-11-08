package com.chaskifood.app.feature.orders.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BuyerOrderCancellationScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun detail(status: String): BuyerOrderDetail {
        val line = StoreOrderLine("product", "Plato", 1, 1000, 1000)
        val summary = BuyerOrderSummary("a".repeat(64), 1_800_000_000_000, status, true, BuyerOrderPhase.ACTIVE,
            "Local", "Negocio", 1, 1300, listOf(BuyerOrderItemPreview("Plato", 1)), 1)
        return BuyerOrderDetail(summary, null, "Av. Local", BuyerOrderDelivery(null, "Av. Cliente", null, null),
            listOf(line), OrderAmounts(1000, 300, 1300), OrderPaymentMethod.CASH_ON_DELIVERY,
            listOf(BuyerOrderStatusEntry(status, true, 1_800_000_000_000)))
    }

    @Test fun pendingOrderRequiresConfirmationBeforeCallingCancel() {
        var cancellations = 0
        compose.setContent { ChaskiTheme { BuyerOrderDetailContent(
            BuyerOrderDetailUiState(loading = false, detail = detail("PENDING_CONFIRMATION")), {}, {}, { cancellations++ }) } }

        compose.onNodeWithText("Cancelar pedido").performScrollTo().performClick()
        compose.onNodeWithText("¿Confirmas que deseas cancelar este pedido?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, cancellations) }
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test fun preparingHidesCancellation() {
        compose.setContent { ChaskiTheme { BuyerOrderDetailContent(
            BuyerOrderDetailUiState(loading = false, detail = detail("PREPARING")), {}, {}) } }
        compose.onNodeWithText("Cancelar pedido").assertDoesNotExist()
    }

    @Test fun pendingCancellationInProgressCannotBeRepeated() {
        compose.setContent { ChaskiTheme { BuyerOrderDetailContent(
            BuyerOrderDetailUiState(loading = false, detail = detail("PENDING_CONFIRMATION"), cancelling = true), {}, {}) } }
        compose.onNodeWithText("Cancelando…").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }
}
