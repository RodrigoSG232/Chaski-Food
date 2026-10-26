package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.ui.theme.ChaskiTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealCheckoutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingReadyInvalidacionBlockedErrorYReintento() {
        val repository = ScreenCheckoutRepository()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val model = CheckoutViewModel(repository)
        var cartOpens = 0
        var addressOpens = 0
        show(model, onOpenCart = { cartOpens++ }, onSelectAddress = { addressOpens++ })

        compose.waitUntil(5_000) { repository.reviews == 1 }
        compose.onNodeWithText("Comprobando carrito, dirección y oferta actual…").assertIsDisplayed()

        gate.complete(Unit)
        compose.onNodeWithText("Compra revisada. La confirmación del pedido estará disponible próximamente.")
            .assertDoesNotExist()
        compose.onNodeWithText("Local").assertIsDisplayed()

        compose.runOnIdle { repository.context.value = CheckoutContextState.Ready(screenContext(2)) }
        compose.onNodeWithText("La compra cambió. Vuelve a comprobarla antes de continuar.").assertIsDisplayed()
        compose.onNodeWithText("Local").assertDoesNotExist()

        compose.runOnIdle { repository.next = CheckoutReviewResult.Blocked(
            listOf("Dirección fuera de cobertura"), canChangeAddress = true) }
        compose.onNodeWithText("Volver a comprobar").performClick()
        compose.onNodeWithText("La compra necesita cambios").assertIsDisplayed()
        compose.onNodeWithText("Cambiar dirección").performScrollTo().performClick()
        compose.onNodeWithText("Volver al carrito").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, addressOpens)
            assertEquals(1, cartOpens)
            repository.next = CheckoutReviewResult.Error("Red de prueba no disponible")
        }
        compose.onNodeWithText("Volver a comprobar").performScrollTo().performClick()
        compose.onNodeWithText("Red de prueba no disponible").assertIsDisplayed()
        compose.runOnIdle { repository.next = CheckoutReviewResult.Ready(screenQuote()) }
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Productos").performScrollTo().assertIsDisplayed()
    }

    @Test fun signedOutYRegreso() {
        val repository = ScreenCheckoutRepository()
        repository.context.value = CheckoutContextState.SignedOut
        val model = CheckoutViewModel(repository)
        var backs = 0
        show(model, onBack = { backs++ })

        compose.onNodeWithText("Inicia sesión para revisar tu compra.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Regresar").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun aceptacionDePreciosSeDescartaAlCambiarElCarrito() {
        val repository = ScreenCheckoutRepository()
        val model = CheckoutViewModel(repository)
        show(model)
        compose.onNodeWithText("Aceptar precios actualizados").performScrollTo().performClick()
        compose.onNodeWithText("Efectivo al recibir").performScrollTo()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.runOnIdle { assertEquals(true, model.uiState.value.reviewComplete) }

        compose.runOnIdle { repository.context.value = CheckoutContextState.Ready(screenContext(2)) }
        compose.onNodeWithText("La compra cambió. Vuelve a comprobarla antes de continuar.").assertIsDisplayed()
        compose.runOnIdle { assertFalse(model.uiState.value.acceptedPriceChanges) }
    }

    private fun show(
        model: CheckoutViewModel,
        onBack: () -> Unit = {},
        onOpenCart: () -> Unit = {},
        onSelectAddress: () -> Unit = {},
    ) {
        compose.setContent {
            ChaskiTheme {
                RealCheckoutScreen(onBack, onOpenCart, onSelectAddress, viewModel = model)
            }
        }
    }
}

private class ScreenCheckoutRepository : CheckoutRepository {
    val context = MutableStateFlow<CheckoutContextState>(CheckoutContextState.Ready(screenContext()))
    var next: CheckoutReviewResult = CheckoutReviewResult.Ready(screenQuote())
    var gate: CompletableDeferred<Unit>? = null
    var reviews = 0

    override fun observeContext(): Flow<CheckoutContextState> = context
    override suspend fun isContextCurrent(context: CheckoutContext): Boolean =
        (this.context.value as? CheckoutContextState.Ready)?.context == context

    override suspend fun review(context: CheckoutContext): CheckoutReviewResult {
        reviews++
        gate?.await()
        return next
    }
}

private fun screenContext(quantity: Int = 1) = CheckoutContext(
    "customer", 0,
    Cart("customer", listOf(CartProductSnapshot("business", "store", "product", "Plato", 1000, quantity))),
    Address("address", "Casa", "Lima", AddressCoordinates(-12.0, -77.0), "", "", 1L, 1L),
)

private fun screenQuote() = CheckoutQuote(
    ownerUid = "customer", storeId = "store", storeName = "Local de prueba",
    addressId = "address", addressLabel = "Casa", addressText = "Lima",
    lines = listOf(CheckoutLine("product", "Plato", 1, 1000, 1200, 1200)),
    subtotalMinor = 1200, deliveryFeeMinor = 500, totalMinor = 1700,
)
