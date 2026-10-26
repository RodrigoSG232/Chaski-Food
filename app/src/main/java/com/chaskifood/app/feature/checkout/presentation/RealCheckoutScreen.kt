package com.chaskifood.app.feature.checkout.presentation

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.review()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        topBar = { ChaskiAppBar(title = "Revisar compra", onBackClick = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(ChaskiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            when {
                state.loading -> item("loading") {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
                        CircularProgressIndicator()
                        Text("Comprobando carrito, dirección y oferta actual…")
                    }
                }
                state.result == null -> {
                    item("changed") { CheckoutMessage("La compra cambió. Vuelve a comprobarla antes de continuar.") }
                    item("review-again") { ChaskiButton("Volver a comprobar", onClick = viewModel::review) }
                }
                state.result is CheckoutReviewResult.SignedOut -> item("signed-out") {
                    CheckoutMessage("Inicia sesión para revisar tu compra.")
                }
                state.result is CheckoutReviewResult.Error -> {
                    item("error") { CheckoutMessage((state.result as CheckoutReviewResult.Error).message) }
                    item("retry") { ChaskiButton("Reintentar", onClick = viewModel::review) }
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
                    item("retry") { ChaskiButton("Volver a comprobar", onClick = viewModel::review) }
                }
                state.result is CheckoutReviewResult.Ready -> {
                    val quote = (state.result as CheckoutReviewResult.Ready).quote
                    item("store") { CheckoutInfo("Local", quote.storeName) }
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
                                    onClick = viewModel::acceptPriceChanges,
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
                                        onClick = { viewModel.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY) },
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
                            if (state.reviewComplete) "Compra revisada. La confirmación del pedido estará disponible próximamente."
                            else "Selecciona la modalidad de pago y revisa cualquier cambio de precio.",
                        )
                    }
                    item("review-again") {
                        ChaskiButton("Volver a comprobar", onClick = viewModel::review,
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
            Text("Importes revisados con la oferta actual. Se volverán a validar al confirmar el pedido.",
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
