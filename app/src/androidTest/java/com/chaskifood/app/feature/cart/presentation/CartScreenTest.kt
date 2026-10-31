package com.chaskifood.app.feature.cart.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartScreenTest {
    @get:Rule val compose = createComposeRule()

    private val cookie = CartProductSnapshot("business", "store", "cookie", "Cookie Sandwich", 1050, 2)
    private val cart = Cart("uid", listOf(cookie))

    @Test fun accesoMuestraUnidadesRealesYOcultaContadorSinSesion() {
        val units = mutableStateOf<Long?>(3)
        var opened = 0
        compose.setContent {
            ChaskiTheme { CartAccessButton(units.value, onClick = { opened++ }, showLabel = true) }
        }
        compose.onNodeWithText("Carrito").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ver carrito, 3 unidades").performClick()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.runOnIdle { units.value = null }
        compose.onNodeWithContentDescription("Ver carrito").assertIsDisplayed()
        compose.onNodeWithText("3").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun carritoVacioOfreceExplorar() {
        var explored = 0
        show(CartUiState(source = CartState.Ready(Cart("uid"))), onExplore = { explored++ })
        compose.onNodeWithText("Tu carrito está vacío").assertIsDisplayed()
        compose.onNodeWithText("Explorar productos").performClick()
        compose.runOnIdle { assertEquals(1, explored) }
    }

    @Test fun carritoConProductosPermiteAbrirRevisionDeCompra() {
        var reviews = 0
        show(CartUiState(source = CartState.Ready(cart)), onCheckout = { reviews++ })
        compose.onNodeWithText("Continuar").performClick()
        compose.runOnIdle { assertEquals(1, reviews) }
    }

    @Test fun carritoVacioMuestraAvisoYProgresoSinPermitirNavegarDuranteOperacion() {
        val empty = Cart("uid")
        val state = mutableStateOf(CartUiState(source = CartState.Ready(empty),
            message = CartUiMessage(7, "No se pudo verificar la oferta.")))
        var explored = 0
        show(state.value, stateOverride = state, onExplore = { explored++ })
        compose.onNodeWithText("No se pudo verificar la oferta.").assertIsDisplayed()
        compose.runOnIdle {
            state.value = CartUiState(source = CartState.Ready(empty), operation = CartUiOperation.Add("cookie"))
        }
        compose.onNodeWithText("Actualizando carrito…").assertIsDisplayed()
        compose.onNodeWithText("Explorar productos").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, explored) }
    }

    @Test fun carritoPresentaImportesExactosYActualizaSubtotalObservado() {
        val bread = CartProductSnapshot("business", "store", "bread", "Pan integral", 400, 1)
        val state = mutableStateOf(CartUiState(source = CartState.Ready(Cart("uid", listOf(cookie, bread)))))
        show(state.value, stateOverride = state)
        compose.onNodeWithText("Cookie Sandwich").assertIsDisplayed()
        compose.onAllNodesWithText("Precio unitario provisional").assertCountEquals(2)
        compose.onNodeWithText("S/ 10.50").assertIsDisplayed()
        compose.onNodeWithText("Cantidad: 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Subtotal de Cookie Sandwich: S/ 21.00")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Subtotal de Pan integral: S/ 4.00")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 25.00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Imagen no disponible de Cookie Sandwich").assertIsDisplayed()
        compose.runOnIdle {
            state.value = CartUiState(source = CartState.Ready(Cart("uid", listOf(cookie.copy(quantity = 3), bread))))
        }
        compose.onNodeWithContentDescription("Subtotal de Cookie Sandwich: S/ 31.50")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 35.50").assertIsDisplayed()
        compose.onNodeWithText("S/ 25.00").assertDoesNotExist()
    }

    @Test fun controlesEnvíanCantidadesObjetivoYEliminación() {
        val changes = mutableListOf<Pair<String, Int>>()
        val removals = mutableListOf<String>()
        show(CartUiState(source = CartState.Ready(cart)),
            onChange = { id, quantity -> changes += id to quantity }, onRemove = { removals += it })
        compose.onNodeWithContentDescription("Aumentar cantidad de Cookie Sandwich").performClick()
        compose.onNodeWithContentDescription("Reducir cantidad de Cookie Sandwich").performClick()
        compose.onNodeWithContentDescription("Eliminar Cookie Sandwich").performClick()
        compose.runOnIdle {
            assertEquals(listOf("cookie" to 3, "cookie" to 1), changes)
            assertEquals(listOf("cookie"), removals)
        }
    }

    @Test fun cargaSesionCerradaYErrorOfrecenAccionesCorrectas() {
        val state = mutableStateOf(CartUiState())
        var retries = 0
        var backs = 0
        show(state.value, stateOverride = state, onRetry = { retries++ }, onBack = { backs++ })
        compose.onNodeWithText("Cargando tu carrito").assertIsDisplayed()
        compose.runOnIdle { state.value = CartUiState(source = CartState.SignedOut) }
        compose.onNodeWithText("Inicia sesión para ver tu carrito").assertIsDisplayed()
        compose.onNodeWithText("Iniciar sesión").performClick()
        compose.runOnIdle { state.value = CartUiState(source = CartState.ReadError) }
        compose.onNodeWithText("No pudimos cargar tu carrito").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, retries)
        }
    }

    @Test fun operacionEnCursoDeshabilitaControlesYEvitaDobleAccion() {
        var changes = 0
        show(CartUiState(source = CartState.Ready(cart), operation = CartUiOperation.ChangeQuantity("cookie")),
            onChange = { _, _ -> changes++ })
        compose.onNodeWithText("Actualizando carrito…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aumentar cantidad de Cookie Sandwich").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reducir cantidad de Cookie Sandwich").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eliminar Cookie Sandwich").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun dialogoCancelaOConfirmaConElTokenMostrado() {
        val incoming = cookie.copy(storeId = "other", quantity = 1)
        val confirmation = CartOperationResult.ConfirmationRequired("token-1", cart, incoming)
        var cancelled: String? = null
        var confirmed: String? = null
        show(CartUiState(source = CartState.Ready(cart), confirmation = confirmation),
            onCancel = { cancelled = it }, onConfirm = { confirmed = it })
        compose.onNodeWithText("¿Cambiar de local?").assertIsDisplayed()
        compose.onNodeWithText("1 × Cookie Sandwich").assertIsDisplayed()
        compose.onNodeWithText("Conservar mi carrito").performClick()
        compose.onNodeWithText("Reemplazar carrito").performClick()
        compose.runOnIdle {
            assertEquals("token-1", cancelled)
            assertEquals("token-1", confirmed)
        }
    }

    @Test fun ofertaActualizadaExigeNuevaConfirmacionYMuestraErrorPersistente() {
        val confirmation = CartOperationResult.ConfirmationRequired(
            "token-2", cart, cookie.copy(storeId = "other", unitPriceMinor = 1200), offerChanged = true)
        val state = CartUiState(source = CartState.Ready(cart), confirmation = confirmation,
            message = CartUiMessage(4, "No se pudo verificar la oferta."))
        var confirmed: String? = null
        show(state, onConfirm = { confirmed = it })
        compose.onNodeWithText("La oferta cambió").assertIsDisplayed()
        compose.onNodeWithText("Precio provisional por unidad: S/ 12.00").assertIsDisplayed()
        compose.onNodeWithText("No se pudo verificar la oferta.").assertIsDisplayed()
        compose.onNodeWithText("Confirmar oferta actualizada").performClick()
        compose.runOnIdle { assertEquals("token-2", confirmed) }
    }

    @Test fun avisoSoloSeDescartaPorAccionDelUsuario() {
        var dismissed: Long? = null
        show(CartUiState(source = CartState.Ready(cart), message = CartUiMessage(9, "Oferta no disponible")),
            onDismiss = { dismissed = it })
        compose.onNodeWithText("Oferta no disponible").assertIsDisplayed()
        compose.onNodeWithContentDescription("Descartar aviso").performClick()
        compose.runOnIdle { assertEquals(9L, dismissed) }
    }

    private fun show(
        state: CartUiState,
        stateOverride: androidx.compose.runtime.State<CartUiState>? = null,
        onBack: () -> Unit = {},
        onExplore: () -> Unit = {},
        onCheckout: () -> Unit = {},
        onRetry: () -> Unit = {},
        onChange: (String, Int) -> Unit = { _, _ -> },
        onRemove: (String) -> Unit = {},
        onConfirm: (String) -> Unit = {},
        onCancel: (String) -> Unit = {},
        onDismiss: (Long) -> Unit = {},
    ) {
        compose.setContent {
            ChaskiTheme {
                val current = stateOverride?.value ?: state
                CartContent(
                    state = current,
                    onBack = onBack,
                    onExploreProducts = onExplore,
                    onCheckout = onCheckout,
                    onRetry = onRetry,
                    onChangeQuantity = onChange,
                    onRemoveProduct = onRemove,
                    onDismissMessage = onDismiss,
                )
                CartConfirmationHost(current, onConfirm, onCancel)
            }
        }
    }
}
