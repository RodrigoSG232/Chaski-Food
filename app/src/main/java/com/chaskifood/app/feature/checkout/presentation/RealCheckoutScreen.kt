package com.chaskifood.app.feature.checkout.presentation

import com.chaskifood.app.core.navigation.LocalProtectedAction
import com.chaskifood.app.core.navigation.LocalNavigationAccess

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.chaskifood.app.ui.theme.ChaskiBorder
import com.chaskifood.app.ui.theme.ChaskiSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.chaskifood.app.ui.theme.ChaskiTheme
import com.chaskifood.app.feature.orders.presentation.BuyerOrderStatusChip
import com.chaskifood.app.feature.orders.presentation.shortOrderId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.checkout.domain.OrderRejection
import com.chaskifood.app.feature.checkout.domain.CheckoutConfirmation
import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun RealCheckoutScreen(
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    onSelectAddress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
    recoveryOnly: Boolean = false,
    onOpenOrder: (String) -> Unit = {},
    onPurchaseFinished: () -> Unit = onBack,
) {
    val authorize = LocalProtectedAction.current
    val origin = LocalNavigationAccess.current?.current()?.origin
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val purchased = state.confirmation is CheckoutConfirmation.Success
    BackHandler(enabled = purchased, onBack = onPurchaseFinished)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, origin, recoveryOnly) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) authorize { if (recoveryOnly) viewModel.loadPending() else viewModel.review() }
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onScreenPaused()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        // «Revisar compra» antes de confirmar; tras una confirmación exitosa (directa o recuperada), «Pedido enviado».
        // «Confirmar pedido» antes de enviar; tras el éxito (directo o recuperado), «Pedido enviado» y una compra ya
        // terminada no vuelve a la revisión: Atrás sale a la pantalla principal.
        topBar = { ChaskiAppBar(title = when { purchased -> "Pedido enviado"; recoveryOnly -> "Recuperar compra"; else -> "Confirmar pedido" },
            onBackClick = if (purchased) onPurchaseFinished else onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(ChaskiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            when {
                state.confirmation != CheckoutConfirmation.None -> item("confirmation") {
                    CheckoutConfirmationPanel(state.confirmation,
                        onRecover = { authorize { viewModel.recover() } },
                        onRetry = if (recoveryOnly) null else ({ authorize { viewModel.retryOriginal() } }),
                        onReview = if (recoveryOnly) null else ({ authorize { viewModel.review() } }),
                        onOpenOrder = onOpenOrder)
                }
                recoveryOnly -> item("no-pending") {
                    CheckoutMessage("No tienes una confirmación pendiente de recuperar en este dispositivo.")
                    ChaskiButton("Buscar intento pendiente", onClick = { authorize { viewModel.loadPending() } })
                }
                state.loading -> item("loading") {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
                        CircularProgressIndicator()
                        Text("Comprobando carrito, dirección, oferta y reparto…")
                    }
                }
                state.result == null -> {
                    item("changed") { CheckoutMessage("La compra cambió. Vuelve a comprobarla antes de continuar.") }
                    item("review-again") { ChaskiButton("Volver a comprobar", onClick = { authorize { viewModel.review() } }) }
                }
                state.result is CheckoutReviewResult.SignedOut -> item("signed-out") {
                    CheckoutMessage("Inicia sesión para revisar tu compra.")
                }
                state.result is CheckoutReviewResult.Error -> {
                    item("error") { CheckoutMessage((state.result as CheckoutReviewResult.Error).message) }
                    item("retry") { ChaskiButton("Reintentar", onClick = { authorize { viewModel.review() } }) }
                }
                state.result is CheckoutReviewResult.Blocked -> {
                    val blocked = state.result as CheckoutReviewResult.Blocked
                    item("blocked-title") { Text("La compra necesita cambios",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(blocked.reasons) { reason -> CheckoutMessage(reason) }
                    if (blocked.canChangeAddress) item("address-action") {
                        ChaskiButton("Cambiar dirección", onClick = onSelectAddress,
                            variant = ChaskiButtonVariant.Outlined)
                    }
                    item("cart-action") { ChaskiButton("Volver al carrito", onClick = onOpenCart,
                        variant = ChaskiButtonVariant.Outlined) }
                    item("retry") { ChaskiButton("Volver a comprobar", onClick = { authorize { viewModel.review() } }) }
                }
                state.result is CheckoutReviewResult.Ready -> {
                    val quote = (state.result as CheckoutReviewResult.Ready).quote
                    item("store") { CheckoutInfo("Local", quote.storeName) }
                    quote.acceptedPurchase?.let { accepted ->
                        item("consent-details") {
                            Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                                CheckoutInfo("Negocio", accepted.businessName)
                                CheckoutInfo("Comprador", accepted.buyerName)
                                accepted.buyerPhone?.let { CheckoutInfo("Teléfono de contacto", it) }
                                CheckoutInfo("Recojo", accepted.pickup.addressText)
                                accepted.storePhone?.let { CheckoutInfo("Teléfono del local", it) }
                                accepted.delivery.destination.reference?.takeIf { it.isNotBlank() }?.let { CheckoutInfo("Referencia de entrega", it) }
                                accepted.delivery.destination.instructions?.takeIf { it.isNotBlank() }?.let { CheckoutInfo("Instrucciones de entrega", it) }
                            }
                        }
                    }
                    item("address") {
                        Surface(color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd)) {
                                Text("Dirección de entrega", fontWeight = FontWeight.SemiBold)
                                Text("${quote.addressLabel}: ${quote.addressText}")
                                TextButton(onClick = onSelectAddress) { Text("CAMBIAR DIRECCIÓN") }
                            }
                        }
                    }
                    item("items-title") { Text("Productos", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold) }
                    items(quote.lines, key = { it.productId }) { line -> CheckoutLineCard(line) }
                    if (quote.hasPriceChanges) item("price-change") {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd),
                                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                                Text("El precio de uno o más productos cambió.", fontWeight = FontWeight.Bold)
                                Text("Revisa los nuevos importes antes de continuar.")
                                ChaskiButton(
                                    text = if (state.acceptedPriceChanges) "Precios revisados" else "Aceptar precios actualizados",
                                    onClick = { authorize { viewModel.acceptPriceChanges() } },
                                    enabled = !state.acceptedPriceChanges,
                                    variant = ChaskiButtonVariant.Outlined,
                                )
                            }
                        }
                    }
                    item("payment") {
                        Surface(color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd)) {
                                Text("Modalidad de pago", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = state.paymentMethod == CheckoutPaymentMethod.CASH_ON_DELIVERY,
                                        onClick = { authorize { viewModel.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY) } },
                                    )
                                    Text("Efectivo al recibir", modifier = Modifier.weight(1f))
                                }
                                if (state.paymentMethod == null) Text("Selecciona cómo pagarás.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item("summary") { CheckoutAmountSummary(quote) }
                    item("status") {
                        CheckoutMessage(
                            if (quote.acceptedPurchase == null) "La confirmación no está habilitada en este entorno."
                            else if (state.reviewComplete) "Compra revisada. Confirma para enviar el pedido."
                            else "Selecciona la modalidad de pago y revisa cualquier cambio de precio.",
                        )
                    }
                    item("confirm") {
                        ChaskiButton("Confirmar pedido", onClick = { authorize { viewModel.confirm() } },
                            enabled = state.reviewComplete && quote.acceptedPurchase != null)
                    }
                    item("review-again") {
                        ChaskiButton("Volver a comprobar", onClick = { authorize { viewModel.review() } },
                            variant = ChaskiButtonVariant.Outlined)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutInfo(title: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value)
        }
    }
}

@Composable
private fun CheckoutMessage(message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd))
    }
}

@Composable
private fun CheckoutLineCard(line: CheckoutLine) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs)) {
            Text(line.name, fontWeight = FontWeight.SemiBold)
            Text("${line.quantity} × ${formatCartPrice(line.unitPriceMinor)}")
            if (line.previousUnitPriceMinor != line.unitPriceMinor) {
                Text("Antes: ${formatCartPrice(line.previousUnitPriceMinor)} · Ahora: ${formatCartPrice(line.unitPriceMinor)}",
                    color = MaterialTheme.colorScheme.error)
            }
            Text(formatCartPrice(line.lineTotalMinor), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CheckoutAmountSummary(quote: CheckoutQuote) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
            CheckoutAmountRow("Subtotal", quote.subtotalMinor)
            CheckoutAmountRow("Costo de reparto", quote.deliveryFeeMinor)
            HorizontalDivider()
            CheckoutAmountRow("Total", quote.totalMinor, bold = true)
            Text("La cotización de reparto es temporal. Los importes se volverán a validar al confirmar el pedido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckoutAmountRow(label: String, minor: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(formatCartPrice(minor), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun CheckoutConfirmationPanel(state: CheckoutConfirmation, onRecover: () -> Unit,
    onRetry: (() -> Unit)?, onReview: (() -> Unit)?, onOpenOrder: (String) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
        when (state) {
            CheckoutConfirmation.Working -> { CircularProgressIndicator(); CheckoutMessage("Verificando confirmación. No vuelvas a enviar otra compra.") }
            is CheckoutConfirmation.Success -> {
                OrderSentPanel(state.receipt.orderId, state.receipt.status.name, state.receipt.accepted.storeName,
                    state.receipt.accepted.amounts.totalMinor, state.cartConsumed, onOpenOrder)
            }
            is CheckoutConfirmation.Pending -> {
                CheckoutMessage("Resultado pendiente de verificar. Conservamos el intento original y tu carrito.")
                ChaskiButton("Consultar resultado", onClick = onRecover)
                if (onRetry != null) ChaskiButton("Reintentar intento original", onClick = onRetry, variant = ChaskiButtonVariant.Outlined)
            }
            is CheckoutConfirmation.Conflict -> {
                CheckoutMessage("El intento presenta un conflicto. No se creará otra compra automáticamente.")
                ChaskiButton("Consultar resultado", onClick = onRecover)
            }
            is CheckoutConfirmation.Rejected -> {
                val reason = when (state.reason) {
                    OrderRejection.REVIEW_REQUIRED -> "Las condiciones de la compra cambiaron."
                    OrderRejection.QUOTE_EXPIRED -> "La cotización de reparto venció."
                    OrderRejection.PURCHASE_FORBIDDEN -> "La cuenta no puede iniciar compras nuevas."
                    OrderRejection.BUSINESS_UNAVAILABLE -> "El negocio no está habilitado para recibir compras."
                    OrderRejection.STORE_UNAVAILABLE -> "El local no está disponible."
                    OrderRejection.NO_RESPONSIBLE -> "El local no tiene un responsable asignado."
                    OrderRejection.INVALID_LOCATION -> "La ubicación ya no es válida."
                    OrderRejection.OUTSIDE_COVERAGE -> "La dirección está fuera de cobertura."
                }
                CheckoutMessage("$reason Tu carrito se conserva.")
                if (onReview != null) ChaskiButton("Revisar nuevamente", onClick = onReview)
            }
            CheckoutConfirmation.ReviewRequired -> {
                CheckoutMessage("Las condiciones cambiaron. Revisa y acepta la compra nuevamente.")
                if (onReview != null) ChaskiButton("Volver a comprobar", onClick = onReview)
            }
            CheckoutConfirmation.StorageError -> {
                CheckoutMessage("No pudimos guardar o recuperar el intento. Consulta su estado antes de confirmar otra compra.")
                ChaskiButton("Recuperar intento", onClick = onRecover)
            }
            CheckoutConfirmation.Busy -> {
                CheckoutMessage("Ya hay una operación de confirmación en curso.")
                ChaskiButton("Consultar intento pendiente", onClick = onRecover)
            }
            CheckoutConfirmation.PurchaseForbidden -> {
                CheckoutMessage("No tienes permiso para iniciar compras nuevas. Puedes recuperar una confirmación anterior.")
                ChaskiButton("Recuperar intento", onClick = onRecover)
            }
            CheckoutConfirmation.SessionChanged -> CheckoutMessage("La sesión cambió. Vuelve a entrar para consultar tus intentos.")
            CheckoutConfirmation.None -> Unit
        }
    }
}

/** HU17: confirmación exitosa, igual para la respuesta directa y la recuperada. «Ver mi pedido» usa el ID completo. */
@Composable
private fun OrderSentPanel(orderId: String, status: String, storeName: String, totalMinor: Long, cartConsumed: Boolean,
    onOpenOrder: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(OrderSentBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = OrderSentFg, modifier = Modifier.size(32.dp))
            }
            Text("Recibimos tu pedido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("El local revisará tu pedido. Puedes consultar su estado cuando quieras.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Frame 06 aprobado: tarjeta blanca con borde (sin tinte tonal de Material).
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChaskiSurface)
            .border(1.dp, ChaskiBorder, RoundedCornerShape(12.dp)).padding(ChaskiDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                Text("Pedido #${shortOrderId(orderId)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BuyerOrderStatusChip(status, known = true)
                ReceiptRow("Local", storeName)
                ReceiptRow("Pago", "Efectivo contra entrega")
                ReceiptRow("Total", formatCartPrice(totalMinor), bold = true)
        }
        if (!cartConsumed) Text("Conservamos tu carrito porque cambió después de la revisión.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChaskiButton("Ver mi pedido", onClick = { onOpenOrder(orderId) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private val OrderSentBg = Color(0xFFE8F5E9)
private val OrderSentFg = Color(0xFF1B5E20)

@Preview(name = "Checkout · Pedido enviado", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OrderSentPreview() = ChaskiTheme {
    Scaffold(topBar = { ChaskiAppBar(title = "Pedido enviado", onBackClick = {}) }) { padding ->
        Column(Modifier.padding(padding).padding(ChaskiDimens.ScreenPadding)) {
            OrderSentPanel("7f3a9c21" + "0".repeat(56), "PENDING_CONFIRMATION", "Local de ejemplo · Centro", 4600, cartConsumed = true) {}
        }
    }
}
