package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.orders.domain.BuyerOrderPhase
import com.chaskifood.app.feature.orders.domain.BuyerOrderSummary
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.*

/**
 * Lista de compras propias (HU17), compartida por la pestaña Pedidos del cliente y «Mis compras» de las cuentas
 * comerciales: solo cambia la cabecera que recibe cada pantalla. Se actualiza al entrar, al volver a la app y al deslizar.
 */
@Composable
fun BuyerOrdersScreen(
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    viewModel: BuyerOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.refresh(); onPauseOrDispose { } }
    BuyerOrdersContent(state, onSelect = viewModel::select, onRefresh = viewModel::refresh, onLoadMore = viewModel::loadMore,
        onOpenOrder = onOpenOrder, modifier = modifier, header = header)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuyerOrdersContent(
    state: BuyerOrdersUiState,
    onSelect: (BuyerOrderPhase) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val current = state.current
    Column(modifier.fillMaxSize().background(ChaskiBackground)) {
        header()
        if (!state.signedIn) {
            OrdersMessage(Icons.Filled.Lock, "Inicia sesión para ver tus pedidos",
                "Por seguridad, cerramos la vista al cambiar de cuenta o cerrar sesión.")
            return@Column
        }
        PhaseTabs(state.selected, onSelect)
        PullToRefreshBox(isRefreshing = current.loading && current.loaded, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                current.orders.isEmpty() && (current.loading || current.loadingMore) -> OrdersLoading("Cargando tus pedidos…")
                current.orders.isEmpty() && current.message != null -> OrdersMessage(Icons.Filled.WifiOff, "No pudimos cargar tus pedidos",
                    current.message, "Reintentar", ChaskiButtonVariant.Primary, if (current.nextCursor != null) onLoadMore else onRefresh)
                current.orders.isEmpty() && current.nextCursor != null -> OrdersMessage(Icons.Filled.Search, "Seguimos buscando",
                    "Revisamos tus pedidos más recientes. Aún quedan pedidos anteriores por revisar.", "Buscar más",
                    ChaskiButtonVariant.Outlined, onLoadMore)
                current.confirmedEmpty -> if (state.selected == BuyerOrderPhase.ACTIVE)
                    OrdersMessage(Icons.AutoMirrored.Filled.ReceiptLong, "No tienes pedidos en curso",
                        "Cuando hagas un pedido, podrás consultar su estado aquí.")
                else OrdersMessage(Icons.Filled.Archive, "Aún no tienes pedidos anteriores",
                    "Aquí verás tus pedidos entregados, cancelados o rechazados.")
                // Sin datos aún (pestaña recién elegida): mismo aspecto que la carga, nunca «sin pedidos».
                current.orders.isEmpty() -> OrdersLoading("Cargando tus pedidos…")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(current.orders, key = { it.orderId }) { BuyerOrderRow(it, onClick = { onOpenOrder(it.orderId) }) }
                    item("footer") { ListFooter(state.selected, current, onLoadMore, onRefresh) }
                }
            }
        }
    }
}

@Composable
private fun PhaseTabs(selected: BuyerOrderPhase, onSelect: (BuyerOrderPhase) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = ChaskiDimens.SpacingLg, end = ChaskiDimens.SpacingLg, bottom = ChaskiDimens.SpacingSm),
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        for ((phase, label) in listOf(BuyerOrderPhase.ACTIVE to "En curso", BuyerOrderPhase.HISTORICAL to "Historial")) {
            val on = phase == selected
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) ChaskiAction else ChaskiSurface)
                .then(if (on) Modifier else Modifier.border(1.dp, ChaskiBorder, RoundedCornerShape(20.dp)))
                .selectable(selected = on, role = Role.Tab, onClick = { onSelect(phase) })
                .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingSm)) {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    color = if (on) ChaskiOnPrimary else ChaskiTextPrimary)
            }
        }
    }
}

@Composable
internal fun BuyerOrderStatusChip(status: String, known: Boolean) {
    val ui = buyerOrderStatusUi(status, known)
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(ui.background)
        .then(if (ui.outlined) Modifier.border(1.dp, ChaskiTextDisabled, RoundedCornerShape(8.dp)) else Modifier)
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(ui.icon, contentDescription = null, tint = ui.content, modifier = Modifier.size(16.dp))
        Text(ui.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = ui.content)
    }
}

@Composable
private fun BuyerOrderRow(order: BuyerOrderSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ChaskiSurface).clickable(onClick = onClick)
        .padding(ChaskiDimens.SpacingLg), verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        Text("Pedido #${shortOrderId(order.orderId)}", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = ChaskiTextPrimary)
        BuyerOrderStatusChip(order.status, order.statusKnown)
        Text(order.storeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = ChaskiTextSecondary)
        Text("${formatOrderDateTime(order.createdAtEpochMillis)}  •  ${formatCartPrice(order.totalMinor)}",
            style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(itemPreviewText(order), style = MaterialTheme.typography.bodyMedium, color = ChaskiTextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle", tint = ChaskiTextTertiary)
        }
    }
    HorizontalDivider(color = ChaskiBorder)
}

@Composable
private fun ListFooter(phase: BuyerOrderPhase, current: BuyerOrdersPhaseState, onLoadMore: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingLg), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        when {
            current.loadingMore -> LoadingRow("Cargando más pedidos…")
            current.message != null -> {
                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextSecondary, textAlign = TextAlign.Center)
                ChaskiButton("Reintentar", onClick = if (current.nextCursor != null) onLoadMore else onRefresh,
                    variant = ChaskiButtonVariant.Outlined, modifier = Modifier.fillMaxWidth())
            }
            current.nextCursor != null -> ChaskiButton("Cargar más", onClick = onLoadMore, variant = ChaskiButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth())
            else -> Text(if (phase == BuyerOrderPhase.ACTIVE) "No tienes más pedidos en curso" else "Ya revisaste todos tus pedidos anteriores",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = ChaskiTextTertiary)
        }
        Text("Desliza hacia abajo para actualizar", style = MaterialTheme.typography.labelSmall, color = ChaskiTextTertiary)
    }
}

@Composable
internal fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ChaskiPrimary)
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = ChaskiTextSecondary)
    }
}

@Composable
internal fun OrdersLoading(text: String) {
    Box(Modifier.fillMaxSize().padding(ChaskiDimens.SpacingLg), contentAlignment = Alignment.TopStart) { LoadingRow(text) }
}

/** Estado centrado (vacío, error, búsqueda pendiente o sesión cerrada). Se puede desplazar para que funcione el deslizar. */
@Composable
internal fun OrdersMessage(icon: ImageVector, title: String, body: String, action: String? = null,
    variant: ChaskiButtonVariant = ChaskiButtonVariant.Primary, onAction: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingXl), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                Icon(icon, contentDescription = null, tint = ChaskiTextMuted, modifier = Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ChaskiTextPrimary,
                    textAlign = TextAlign.Center)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextTertiary, textAlign = TextAlign.Center)
                if (action != null) {
                    Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                    ChaskiButton(action, onClick = onAction, variant = variant, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val previewHeader: @Composable () -> Unit = {
    Text("Tus pedidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ChaskiTextPrimary,
        modifier = Modifier.padding(ChaskiDimens.SpacingLg))
}

@Composable
private fun ListPreview(state: BuyerOrdersUiState) = ChaskiTheme {
    BuyerOrdersContent(state, {}, {}, {}, {}, header = previewHeader)
}

@Preview(name = "Pedidos · En curso", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun BuyerOrdersActivePreview() = ListPreview(BuyerOrderPreviewData.list(BuyerOrderPhase.ACTIVE,
    BuyerOrdersPhaseState(BuyerOrderPreviewData.active, nextCursor = "c", loaded = true)))

@Preview(name = "Pedidos · Historial", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun BuyerOrdersHistoricalPreview() = ListPreview(BuyerOrderPreviewData.list(BuyerOrderPhase.HISTORICAL,
    BuyerOrdersPhaseState(BuyerOrderPreviewData.historical, loaded = true)))

@Preview(name = "Pedidos · Vacío, búsqueda pendiente y error", showBackground = true, widthDp = 360, heightDp = 840)
@Composable
private fun BuyerOrdersStatesPreview() = Column {
    Box(Modifier.height(260.dp)) { ListPreview(BuyerOrderPreviewData.list(BuyerOrderPhase.ACTIVE, BuyerOrdersPhaseState(loaded = true))) }
    Box(Modifier.height(260.dp)) { ListPreview(BuyerOrderPreviewData.list(BuyerOrderPhase.HISTORICAL,
        BuyerOrdersPhaseState(nextCursor = "c", loaded = true))) }
    Box(Modifier.height(300.dp)) { ListPreview(BuyerOrderPreviewData.list(BuyerOrderPhase.ACTIVE,
        BuyerOrdersPhaseState(message = "No pudimos cargar tus pedidos. Comprueba la conexión y vuelve a intentarlo."))) }
}
