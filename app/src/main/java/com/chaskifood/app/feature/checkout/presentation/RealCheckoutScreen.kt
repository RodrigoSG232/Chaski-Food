package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.ui.components.ChaskiAppBar

@Composable
fun RealCheckoutScreen(
    onBack: () -> Unit,
    onSelectAddress: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val paymentSelected by viewModel.paymentSelected.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(topBar = { ChaskiAppBar(title = "Revisar compra", onBackClick = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val current = state) {
                CheckoutState.Loading -> item { CircularProgressIndicator() }
                CheckoutState.AddressRequired -> item {
                    Text("Selecciona una dirección de entrega para validar la compra.")
                    Button(onClick = onSelectAddress) { Text("SELECCIONAR DIRECCIÓN") }
                }
                is CheckoutState.Error -> item {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::retry) { Text("REINTENTAR VALIDACIÓN") }
                    TextButton(onClick = onSelectAddress) { Text("CAMBIAR DIRECCIÓN") }
                }
                is CheckoutState.Ready -> {
                    val quote = current.quote
                    item {
                        Text("Entrega en", style = MaterialTheme.typography.titleMedium)
                        Text(quote.address.addressText)
                        TextButton(onClick = onSelectAddress) { Text("CAMBIAR DIRECCIÓN") }
                    }
                    item { Text(quote.storeName, style = MaterialTheme.typography.titleLarge) }
                    items(quote.items.size) { index ->
                        val line = quote.items[index]
                        Column {
                            Text("${line.quantity} × ${line.name}")
                            Text("${formatCartPrice(line.unitPriceMinor)} c/u · ${formatCartPrice(line.lineTotalMinor)}")
                            if (line.priceChanged) Text("Precio actualizado: antes ${formatCartPrice(current.cart.items.first { it.productId == line.productId }.unitPriceMinor)}",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (quote.priceChanged) item {
                        Text("Los precios cambiaron. Revisa el nuevo total antes de confirmar.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        HorizontalDivider()
                        AmountRow("Subtotal", quote.subtotalMinor)
                        AmountRow("Reparto", quote.deliveryFeeMinor)
                        AmountRow("Total", quote.totalMinor)
                        TextButton(onClick = viewModel::retry, enabled = !busy) {
                            Text("ACTUALIZAR DISPONIBILIDAD Y PRECIOS")
                        }
                    }
                    item {
                        Text("Modalidad de pago", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = paymentSelected, onClick = viewModel::selectCash)
                            Text("Efectivo al recibir")
                        }
                        Text("El pago se realizará al entregar el pedido.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { viewModel.confirm(onCreated) }, enabled = paymentSelected && !busy,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "CONFIRMANDO…" else "CONFIRMAR PEDIDO")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amountMinor: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(formatCartPrice(amountMinor))
    }
}
