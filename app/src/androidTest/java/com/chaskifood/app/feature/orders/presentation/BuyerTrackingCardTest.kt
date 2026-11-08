package com.chaskifood.app.feature.orders.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.orders.domain.BuyerTracking
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuyerTrackingCardTest {
    @get:Rule val compose = createComposeRule()
    private fun tracking(status: String, stage: String, assigned: Boolean = false, outcome: String? = null) =
        BuyerTracking("a".repeat(64), status, stage, assigned, if (assigned) "Ana" else null, outcome,
            null, null, 1_800_000_000_000)
    private fun show(value: BuyerTracking) = compose.setContent {
        ChaskiTheme { BuyerTrackingCard(BuyerTrackingUiState(false, value), {}) }
    }
    @Test fun withoutRiderShowsStateWithoutMap() {
        show(tracking("READY_FOR_PICKUP", "SEARCHING_RIDER"))
        compose.onNodeWithText("Buscando repartidor").assertIsDisplayed()
        compose.onNodeWithText("Todavía no hay un repartidor asignado.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mapa de la última ubicación del repartidor").assertDoesNotExist()
    }
    @Test fun assignedBeforePickupStillHasNoMap() {
        show(tracking("READY_FOR_PICKUP", "AT_MERCHANT", true))
        compose.onNodeWithText("El repartidor llegó al local").assertIsDisplayed()
        compose.onNodeWithText("La ubicación estará disponible después del recojo.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mapa de la última ubicación del repartidor").assertDoesNotExist()
    }
    @Test fun missingLocationAndEtaAreExplicitAfterPickup() {
        show(tracking("IN_DELIVERY", "ON_THE_WAY", true))
        compose.onNodeWithText("ETA no disponible").assertIsDisplayed()
        compose.onNodeWithText("Esperando la primera ubicación del repartidor.").assertIsDisplayed()
    }
    @Test fun unsuccessfulDeliveryIsNotShownAsDelivered() {
        show(tracking("COMPLETED", "COMPLETED", true, "UNSUCCESSFUL"))
        compose.onNodeWithText("Entrega no exitosa").assertIsDisplayed()
        compose.onNodeWithText("Entregado").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mapa de la última ubicación del repartidor").assertDoesNotExist()
    }
    @Test fun unavailableTrackingOffersRetry() {
        var retries = 0
        compose.setContent { ChaskiTheme {
            BuyerTrackingCard(BuyerTrackingUiState(false, message = "Sin conexión")) { retries++ }
        } }
        compose.onNodeWithText("Sin conexión").assertIsDisplayed()
        compose.onNodeWithText("Reintentar seguimiento").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
