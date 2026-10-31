package com.chaskifood.app.feature.orders.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdersScreenTest {
    @get:Rule val compose = createComposeRule()
    private val id = "7f3a9c21" + "0".repeat(56)
    private fun row(status: String = "IN_DELIVERY", known: Boolean = true) = BuyerOrderSummary(id, 1_800_000_000_000, status, known,
        BuyerOrderPhase.ACTIVE, "Local ficticio", "Negocio ficticio", 4, 4600,
        listOf(BuyerOrderItemPreview("Plato ficticio", 2), BuyerOrderItemPreview("Bebida ficticia", 1)), 3)
    private fun active(phase: BuyerOrdersPhaseState) =
        BuyerOrdersUiState(phases = BuyerOrderPhase.entries.associateWith { if (it == BuyerOrderPhase.ACTIVE) phase else BuyerOrdersPhaseState() })
    private fun show(state: BuyerOrdersUiState, open: (String) -> Unit = {}, more: () -> Unit = {}, refresh: () -> Unit = {}) =
        compose.setContent { ChaskiTheme { BuyerOrdersContent(state, {}, refresh, more, open) } }

    @Test fun filaMuestraIdAbreviadoEstadoEnEspanolYPrevisualizacionPeroAbreElIdCompleto() {
        val opened = mutableListOf<String>()
        show(active(BuyerOrdersPhaseState(listOf(row()), null, loaded = true)), open = { opened += it })
        compose.onNodeWithText("Pedido #7F3A9C21").assertIsDisplayed()
        compose.onNodeWithText("En camino").assertIsDisplayed()
        compose.onNodeWithText("2× Plato ficticio · 1× Bebida ficticia · +1 más").assertIsDisplayed()
        compose.onNodeWithText("No tienes más pedidos en curso").assertIsDisplayed()
        compose.onNodeWithText("Pedido #7F3A9C21").performClick()
        compose.runOnIdle { assertEquals(listOf(id), opened) }
    }

    @Test fun estadoDesconocidoNoRompeLaFila() {
        show(active(BuyerOrdersPhaseState(listOf(row("AWAITING_RIDER", false)), "c", loaded = true)))
        compose.onNodeWithText("Estado en actualización").assertIsDisplayed()
        compose.onNodeWithText("Cargar más").assertIsDisplayed()
    }

    @Test fun paginaVaciaConCursorNoSeMuestraComoListaVacia() {
        var more = 0
        show(active(BuyerOrdersPhaseState(emptyList(), "c", loaded = true)), more = { more++ })
        compose.onNodeWithText("Seguimos buscando").assertIsDisplayed()
        compose.onNodeWithText("No tienes pedidos en curso").assertDoesNotExist()
        compose.onNodeWithText("Buscar más").performClick()
        compose.runOnIdle { assertEquals(1, more) }
    }

    @Test fun errorDeRedNoSeConfundeConAusenciaDePedidos() {
        var refresh = 0
        show(active(BuyerOrdersPhaseState(message = "Sin conexión ficticia")), refresh = { refresh++ })
        compose.onNodeWithText("No pudimos cargar tus pedidos").assertIsDisplayed()
        compose.onNodeWithText("No tienes pedidos en curso").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, refresh) }
    }

    @Test fun vacioConfirmadoYSesionCerrada() {
        show(active(BuyerOrdersPhaseState(loaded = true)))
        compose.onNodeWithText("No tienes pedidos en curso").assertIsDisplayed()
    }

    @Test fun sesionCerradaNoMuestraCompras() {
        show(BuyerOrdersUiState(signedIn = false))
        compose.onNodeWithText("Inicia sesión para ver tus pedidos").assertIsDisplayed()
    }
}
