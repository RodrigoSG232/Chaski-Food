package com.chaskifood.app.feature.search.presentation

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.cart.presentation.CartConfirmationHost
import com.chaskifood.app.feature.cart.presentation.CartUiMessage
import com.chaskifood.app.feature.cart.presentation.CartUiMessageAction
import com.chaskifood.app.feature.cart.presentation.CartUiOperation
import com.chaskifood.app.feature.cart.presentation.CartUiState
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductDetailPurchaseTest {
    @get:Rule val compose = createComposeRule()

    private val product = StoreOfferProduct("product", "business", "store", "Local", "category",
        "Platos", "Ceviche", "Descripción", 21.5, null, 20)
    private val ready = CartUiState(source = CartState.Ready(Cart("uid")))

    @Test fun cantidadEmpiezaEnUnoYEnviaSoloIdentificadoresYCantidad() {
        val quantity = mutableIntStateOf(1)
        val additions = mutableListOf<Triple<String, String, Int>>()
        compose.setContent {
            ChaskiTheme {
                ProductDetailPurchase(product, quantity.intValue, { quantity.intValue = it }, ready,
                    { store, id, count -> additions += Triple(store, id, count) }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Reducir cantidad").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Aumentar cantidad").performClick()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("Agregar al carrito").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("store", "product", 2)), additions) }
    }

    @Test fun operacionPendienteYConfirmacionBloqueanOtraIncorporacion() {
        val state = mutableStateOf(ready.copy(operation = CartUiOperation.Add("product")))
        var additions = 0
        compose.setContent {
            ChaskiTheme {
                ProductDetailPurchase(product, 1, {}, state.value, { _, _, _ -> additions++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Validando oferta…").assertIsDisplayed()
        compose.onNodeWithText("Validando oferta…").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = ready.copy(confirmation = CartOperationResult.ConfirmationRequired(
                "token", Cart("uid"), CartProductSnapshot("business", "store", "product", "Ceviche", 2150, 1)))
        }
        compose.onNodeWithText("Agregar al carrito").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, additions) }
    }

    @Test fun sesionCerradaOfreceAccesoSinIntentarAgregar() {
        var signIns = 0
        var additions = 0
        compose.setContent {
            ChaskiTheme {
                ProductDetailPurchase(product, 1, {}, CartUiState(source = CartState.SignedOut),
                    { _, _, _ -> additions++ }, { signIns++ }, {}, {})
            }
        }
        compose.onNodeWithText("Iniciar sesión").performClick()
        compose.runOnIdle {
            assertEquals(1, signIns)
            assertEquals(0, additions)
        }
    }

    @Test fun direccionAusenteOfreceNavegacionYUnSoloDialogo() {
        val state = mutableStateOf(ready.copy(message = CartUiMessage(7, "Selecciona una dirección de entrega.",
            action = CartUiMessageAction.SelectAddress)))
        var addressRequests = 0
        var confirms = 0
        compose.setContent {
            ChaskiTheme {
                ProductDetailPurchase(product, 1, {}, state.value, { _, _, _ -> }, {},
                    { addressRequests++ }, {})
                CartConfirmationHost(state.value, { confirms++ }, {})
            }
        }
        compose.onNodeWithText("Elegir dirección").performClick()
        compose.runOnIdle { assertEquals(1, addressRequests) }
        compose.runOnIdle {
            state.value = ready.copy(confirmation = CartOperationResult.ConfirmationRequired(
                "token", Cart("uid"), CartProductSnapshot("business", "other", "product", "Ceviche", 2150, 1)))
        }
        compose.onNodeWithText("¿Cambiar de local?").assertIsDisplayed()
        compose.onNodeWithText("Reemplazar carrito").performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
    }
}
