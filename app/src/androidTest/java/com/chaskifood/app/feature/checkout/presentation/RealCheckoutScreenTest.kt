package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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
import android.os.SystemClock
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.domain.DeliveryQuote
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.checkout.data.SystemCheckoutClock
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
        val model = CheckoutViewModel(repository, SystemCheckoutClock())
        var cartOpens = 0
        var addressOpens = 0
        show(model, onOpenCart = { cartOpens++ }, onSelectAddress = { addressOpens++ })

        compose.waitUntil(5_000) { repository.reviews == 1 }
        compose.onNodeWithText("Comprobando carrito, dirección, oferta y reparto…").assertIsDisplayed()

        gate.complete(Unit)
        compose.onNodeWithText("Compra revisada. La confirmación del pedido estará disponible próximamente.")
            .assertDoesNotExist()
        compose.onNodeWithText("Local").assertIsDisplayed()
        compose.onNodeWithText("Costo de reparto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 3.80").assertIsDisplayed()
        compose.onNodeWithText("S/ 15.80").performScrollTo().assertIsDisplayed()

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
        val model = CheckoutViewModel(repository, SystemCheckoutClock())
        var backs = 0
        show(model, onBack = { backs++ })

        compose.onNodeWithText("Inicia sesión para revisar tu compra.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Regresar").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun aceptacionDePreciosSeDescartaAlCambiarElCarrito() {
        val repository = ScreenCheckoutRepository()
        val model = CheckoutViewModel(repository, SystemCheckoutClock())
        show(model)
        compose.onNodeWithText("Aceptar precios actualizados").performScrollTo().performClick()
        compose.onNodeWithText("Efectivo al recibir").performScrollTo()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.runOnIdle { assertEquals(true, model.uiState.value.reviewComplete) }

        compose.runOnIdle { repository.context.value = CheckoutContextState.Ready(screenContext(2)) }
        compose.onNodeWithText("La compra cambió. Vuelve a comprobarla antes de continuar.").assertIsDisplayed()
        compose.runOnIdle { assertFalse(model.uiState.value.acceptedPriceChanges) }
    }

    @Test fun cotizacionVencidaRetiraElResumenYOfreceReintento() {
        val repository = ScreenCheckoutRepository()
        repository.next = CheckoutReviewResult.Ready(screenQuote().copy(
            deliveryQuoteDeadlineMillis = SystemClock.elapsedRealtime() + 500L))
        val model = CheckoutViewModel(repository, SystemCheckoutClock())
        show(model)
        compose.waitUntil(5_000) { model.uiState.value.result is CheckoutReviewResult.Error }
        compose.onNodeWithText("La cotización de reparto venció. Vuelve a comprobar la compra.").assertIsDisplayed()
        compose.onNodeWithText("Costo de reparto").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test fun confirmarPendienteRecuperacionYExitoSinDuplicar() {
        val repository=ScreenCheckoutRepository()
        val quote=acceptedScreenQuote()
        repository.next=CheckoutReviewResult.Ready(quote)
        repository.confirmGate=CompletableDeferred()
        val model=CheckoutViewModel(repository,SystemCheckoutClock())
        show(model)
        compose.onNodeWithText("Aceptar precios actualizados").performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.RadioButton)).performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Confirmar pedido"))
        compose.onNodeWithText("Confirmar pedido").performClick()
        compose.runOnIdle { model.confirm();assertEquals(1,repository.confirms) }
        compose.onNodeWithText("Verificando confirmación. No vuelvas a enviar otra compra.").assertIsDisplayed()
        compose.runOnIdle { repository.confirmGate!!.complete(Unit) }
        compose.onNodeWithText("Consultar resultado").assertIsDisplayed()
        compose.runOnIdle { repository.orderResult=CheckoutConfirmation.Success(OrderReceipt("order-local",
            "00000000-0000-0000-0000-000000000001",quote.acceptedPurchase!!,System.currentTimeMillis()),true) }
        compose.onNodeWithText("Consultar resultado").performClick()
        compose.onNodeWithText("Pedido confirmado: order-local").assertIsDisplayed()
        compose.onNodeWithText("Estado: PENDING_CONFIRMATION").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,repository.confirms);assertEquals(1,repository.recoveries) }
    }

    @Test fun recuperacionPersonalNoIniciaRevisionNiOfreceCompraNueva() {
        val repository=ScreenCheckoutRepository()
        repository.pendingOrder=CheckoutConfirmation.Pending("original")
        val model=CheckoutViewModel(repository,SystemCheckoutClock())
        compose.setContent { ChaskiTheme { RealCheckoutScreen({}, {}, {}, viewModel=model,recoveryOnly=true) } }
        compose.onNodeWithText("Consultar resultado").assertIsDisplayed()
        compose.onNodeWithText("Reintentar intento original").assertDoesNotExist()
        compose.onNodeWithText("Confirmar pedido").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,repository.reviews);assertEquals(0,repository.confirms) }
    }

    @Test fun erroresDefinitivosConflictoYRevisionRequeridaMuestranAccionesSeguras() {
        val state=androidx.compose.runtime.mutableStateOf<CheckoutConfirmation>(CheckoutConfirmation.ReviewRequired)
        var recoveries=0;var reviews=0
        compose.setContent { ChaskiTheme { CheckoutConfirmationPanel(state.value,{recoveries++},{},{reviews++}) } }
        compose.onNodeWithText("Volver a comprobar").performClick()
        compose.runOnIdle { assertEquals(1,reviews);state.value=CheckoutConfirmation.Conflict("original") }
        compose.onNodeWithText("Consultar resultado").performClick()
        compose.runOnIdle { assertEquals(1,recoveries);state.value=CheckoutConfirmation.Rejected("original",OrderRejection.QUOTE_EXPIRED) }
        compose.onNodeWithText("Revisar nuevamente").assertIsDisplayed()
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
    var confirms = 0
    var recoveries = 0
    var confirmGate: CompletableDeferred<Unit>? = null
    var pendingOrder: CheckoutConfirmation = CheckoutConfirmation.None
    var orderResult: CheckoutConfirmation = CheckoutConfirmation.Pending("original")
    override suspend fun pending(ownerUid: String,generation: Long,intentId: String?) = pendingOrder
    override suspend fun confirm(context: CheckoutContext,quote: CheckoutQuote): CheckoutConfirmation {
        confirms++;confirmGate?.await();return orderResult
    }
    override suspend fun recover(ownerUid: String,generation: Long,intentId: String): CheckoutConfirmation {
        recoveries++;return orderResult
    }

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
    subtotalMinor = 1200, deliveryFeeMinor = 380, totalMinor = 1580,
    deliveryQuote = DeliveryQuote(1_918.8, 380, CartCurrency.PEN, "v1",
        System.currentTimeMillis(), System.currentTimeMillis() + 300_000L,
        com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0),
        com.chaskifood.app.feature.address.domain.model.AddressCoordinates(-12.0, -77.0)),
    deliveryQuoteDeadlineMillis = SystemClock.elapsedRealtime() + 265_000L,
)

private fun acceptedScreenQuote(): CheckoutQuote {
    val plain=screenQuote()
    val q=plain.deliveryQuote.copy(quoteId="quote-local")
    val accepted=AcceptedPurchase("customer","business","store","Negocio","Local de prueba",null,"Cliente",null,
        OrderLocationSnapshot("Origen",q.storeLocation),OrderDeliverySnapshot("address","Casa",OrderLocationSnapshot("Lima",q.deliveryLocation,"","")),
        listOf(OrderItem("product","category","Plato",1,1200)),OrderAmounts(1200,380,1580),AcceptedDeliveryQuote("quote-local",q))
    return plain.copy(deliveryQuote=q,acceptedPurchase=accepted)
}
