package com.chaskifood.app.feature.orders.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdersScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pestañaProductivaMuestraEstadoVacioSinPedidosFicticios() {
        compose.setContent {
            ChaskiTheme { OrdersScreen(onTrack = {}, onDelivered = {}) }
        }

        compose.onNodeWithText("Aún no tienes pedidos").assertIsDisplayed()
        compose.onNodeWithText(
            "Cuando realices tu primer pedido, podrás consultar su información aquí",
        ).assertIsDisplayed()
        compose.onNodeWithText("Pedido #001").assertDoesNotExist()
        compose.onNodeWithText("Ocean bistro").assertDoesNotExist()
    }
}
