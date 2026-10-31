package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.orders.domain.BuyerOrderDetail
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.*

/** Detalle histórico de una compra propia (HU17). Se actualiza al abrir, al volver a la app y al deslizar hacia abajo. */
@Composable
fun BuyerOrderDetailScreen(onBack: () -> Unit, viewModel: BuyerOrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.refresh(); onPauseOrDispose { } }
    BuyerOrderDetailContent(state, onBack, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuyerOrderDetailContent(state: BuyerOrderDetailUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    val detail = state.detail
    Scaffold(containerColor = ChaskiBackground, topBar = {
        ChaskiAppBar(title = detail?.let { "Pedido #${shortOrderId(it.summary.orderId)}" } ?: "Detalle del pedido", onBackClick = onBack)
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && detail != null, onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.signedIn -> OrdersMessage(Icons.Filled.Lock, "Inicia sesión para ver tus pedidos",
                    "Por seguridad, cerramos la vista al cambiar de cuenta o cerrar sesión.")
                state.notFound -> OrdersMessage(Icons.Filled.SearchOff, "No encontramos este pedido",
                    "Puede que no exista o que no pertenezca a esta cuenta.", "Volver a mis pedidos", ChaskiButtonVariant.Outlined, onBack)
                detail != null -> DetailBody(detail, state.message, onRefresh)
                state.message != null -> OrdersMessage(Icons.Filled.WifiOff, "No pudimos cargar el pedido", state.message,
                    "Reintentar", ChaskiButtonVariant.Primary, onRefresh)
                else -> OrdersLoading("Cargando el pedido…")
            }
        }
    }
}

@Composable
private fun DetailBody(detail: BuyerOrderDetail, message: String?, onRetry: () -> Unit) {
    val summary = detail.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(ChaskiDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
        if (message != null) item("refresh-error") {
            // La última copia válida se conserva; el aviso explica que no se pudo actualizar.
            DetailCard { Text(message, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextSecondary)
                TextButton(onClick = onRetry) { Text("Reintentar") } }
        }
        item("status") {
            DetailCard {
                BuyerOrderStatusChip(summary.status, summary.statusKnown)
                Text("Realizado el ${formatOrderDateTime(summary.createdAtEpochMillis)}", style = MaterialTheme.typography.bodyMedium,
                    color = ChaskiTextSecondary)
                Text("Se actualiza al abrir esta pantalla, al volver a la app o al deslizar hacia abajo.",
                    style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
            }
        }
        item("history") {
            Section("HISTORIAL DE ESTADOS")
            DetailCard(spacing = 0.dp) {
                // Solo eventos reales de status_history, en orden cronológico; el último es el estado actual.
                detail.statusHistory.forEachIndexed { index, entry ->
                    val last = index == detail.statusHistory.lastIndex
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
                        Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (last) ChaskiPrimary else ChaskiTextDisabled))
                            if (!last) Box(Modifier.width(2.dp).weight(1f).background(ChaskiBorder))
                        }
                        Column(Modifier.padding(bottom = if (last) 0.dp else ChaskiDimens.SpacingLg)) {
                            val label = buyerOrderStatusUi(entry.status, entry.statusKnown).label
                            Text(if (last) "$label · actual" else label, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal, color = ChaskiTextPrimary)
                            Text(formatOrderShortDateTime(entry.occurredAtEpochMillis), style = MaterialTheme.typography.bodySmall,
                                color = ChaskiTextTertiary)
                        }
                    }
                }
            }
        }
        item("store") {
            Section("LOCAL")
            DetailCard {
                Text(summary.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ChaskiTextPrimary)
                Text(summary.businessName, style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
                IconLine(Icons.Filled.LocationOn, detail.pickupAddressText)
                detail.storePhone?.let { IconLine(Icons.Filled.Phone, it) }
            }
        }
        item("delivery") {
            Section("ENTREGA")
            DetailCard {
                detail.delivery.label?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = ChaskiTextPrimary) }
                Text(detail.delivery.addressText, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextSecondary)
                detail.delivery.reference?.let { Text("Referencia: $it", style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary) }
                detail.delivery.instructions?.let { Text("Indicaciones: $it", style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary) }
                Text("Dirección guardada al confirmar el pedido. Si luego la editas, aquí se conserva la original.",
                    style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = ChaskiTextTertiary)
            }
        }
        item("items") {
            Section("PRODUCTOS")
            DetailCard(spacing = 10.dp) {
                detail.items.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                        Text("${line.quantity}×", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ChaskiTextPrimary)
                        Column(Modifier.weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextPrimary)
                            Text("${formatCartPrice(line.unitPriceMinor)} c/u", style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
                        }
                        Text(formatCartPrice(line.lineTotalMinor), style = MaterialTheme.typography.bodyMedium, color = ChaskiTextPrimary)
                    }
                }
            }
        }
        item("amounts") {
            Section("RESUMEN")
            DetailCard {
                AmountRow("Subtotal", detail.amounts.subtotalMinor)
                AmountRow("Costo de reparto", detail.amounts.deliveryFeeMinor)
                HorizontalDivider(color = ChaskiBorder)
                AmountRow("Total", detail.amounts.totalMinor, bold = true)
            }
        }
        item("payment") {
            Section("PAGO")
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                    Icon(Icons.Filled.Payments, contentDescription = null, tint = ChaskiTextPrimary, modifier = Modifier.size(20.dp))
                    Text("Efectivo contra entrega", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = ChaskiTextPrimary)
                }
                Text("Método de pago registrado para este pedido.", style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ChaskiTextTertiary,
        modifier = Modifier.padding(top = ChaskiDimens.SpacingSm, bottom = ChaskiDimens.SpacingSm))
}

@Composable
private fun DetailCard(spacing: androidx.compose.ui.unit.Dp = 6.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChaskiSurface)
        .border(1.dp, ChaskiBorder, RoundedCornerShape(12.dp)).padding(ChaskiDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        Icon(icon, contentDescription = null, tint = ChaskiTextTertiary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextSecondary)
    }
}

@Composable
private fun AmountRow(label: String, minor: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val weight = if (bold) FontWeight.Bold else FontWeight.Normal
        Text(label, fontWeight = weight, color = if (bold) ChaskiTextPrimary else ChaskiTextSecondary)
        Text(formatCartPrice(minor), fontWeight = weight, color = if (bold) ChaskiTextPrimary else ChaskiTextSecondary)
    }
}

@Preview(name = "Detalle · historial de estados", showBackground = true, widthDp = 360, heightDp = 1500)
@Composable
private fun BuyerOrderDetailPreview() = ChaskiTheme {
    BuyerOrderDetailContent(BuyerOrderDetailUiState(loading = false, detail = BuyerOrderPreviewData.detail), {}, {})
}

@Preview(name = "Detalle · no encontrado", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BuyerOrderDetailNotFoundPreview() = ChaskiTheme {
    BuyerOrderDetailContent(BuyerOrderDetailUiState(loading = false, notFound = true), {}, {})
}
