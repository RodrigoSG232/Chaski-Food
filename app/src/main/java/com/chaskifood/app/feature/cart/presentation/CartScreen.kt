package com.chaskifood.app.feature.cart.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiTheme
import androidx.compose.ui.tooling.preview.Preview

/** La conexión con el grafo de navegación se hará en la fase 5. */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onExploreProducts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        topBar = { ChaskiAppBar(title = "Mi carrito", onBackClick = onBack) },
    ) { padding ->
        CartContent(
            state = state,
            onBack = onBack,
            onExploreProducts = onExploreProducts,
            onRetry = viewModel::retryLoading,
            onChangeQuantity = viewModel::changeQuantity,
            onRemoveProduct = viewModel::removeProduct,
            onConfirmStoreChange = viewModel::confirmStoreChange,
            onCancelStoreChange = viewModel::cancelStoreChange,
            onDismissMessage = viewModel::dismissMessage,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Contenido sin estado: recibe datos y eventos; CartViewModel conserva las reglas y efectos. */
@Composable
internal fun CartContent(
    state: CartUiState,
    onBack: () -> Unit,
    onExploreProducts: () -> Unit,
    onRetry: () -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onConfirmStoreChange: (String) -> Unit,
    onCancelStoreChange: (String) -> Unit,
    onDismissMessage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(ChaskiBackground)) {
        when (val source = state.source) {
            CartState.Loading -> CartPageState(
                title = "Cargando tu carrito",
                description = "Espera a que termine la lectura de tus productos.",
                loading = true,
            )
            CartState.ReadError -> CartPageState(
                title = "No pudimos cargar tu carrito",
                description = "Comprueba la información de tu cuenta e inténtalo otra vez.",
                error = true,
                action = "Reintentar",
                onAction = onRetry,
            )
            CartState.SignedOut -> CartPageState(
                title = "Inicia sesión para ver tu carrito",
                description = "Tus productos se conservan en este dispositivo para cuando vuelvas a entrar con tu cuenta.",
                action = "Volver",
                onAction = onBack,
            )
            is CartState.Ready -> if (source.cart.items.isEmpty()) {
                CartPageState(
                    title = "Tu carrito está vacío",
                    description = "Elige productos de un local y organízalos aquí antes de continuar.",
                    action = "Explorar productos",
                    onAction = onExploreProducts,
                    busy = state.isBusy,
                    message = state.message,
                    onDismissMessage = onDismissMessage,
                )
            } else {
                CartReadyContent(
                    cart = source.cart,
                    state = state,
                    onExploreProducts = onExploreProducts,
                    onChangeQuantity = onChangeQuantity,
                    onRemoveProduct = onRemoveProduct,
                    onDismissMessage = onDismissMessage,
                )
            }
        }
        state.confirmation?.let { confirmation ->
            CartStoreChangeDialog(
                confirmation = confirmation,
                busy = state.isBusy,
                message = state.message?.text,
                onConfirm = { onConfirmStoreChange(confirmation.token) },
                onCancel = { onCancelStoreChange(confirmation.token) },
            )
        }
    }
}

@Composable
private fun CartPageState(
    title: String,
    description: String,
    loading: Boolean = false,
    error: Boolean = false,
    busy: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    message: CartUiMessage? = null,
    onDismissMessage: (Long) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ChaskiDimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) CircularProgressIndicator()
        else Icon(
            imageVector = if (error) Icons.Filled.WarningAmber else Icons.Filled.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        Text(description, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { CartMessage(it, onDismiss = { onDismissMessage(it.id) }) }
        if (busy) {
            Spacer(Modifier.height(ChaskiDimens.SpacingSm))
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Actualizando carrito…", style = MaterialTheme.typography.bodyMedium)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(ChaskiDimens.SpacingLg))
            ChaskiButton(text = action, onClick = onAction, enabled = !busy)
        }
    }
}

@Composable
private fun CartReadyContent(
    cart: Cart,
    state: CartUiState,
    onExploreProducts: () -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onDismissMessage: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(ChaskiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            item(key = "store-context") {
                Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs)) {
                    Text("Productos de un mismo local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Las cantidades y precios se guardan en tu carrito; la oferta se valida al agregar o aumentar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.message?.takeIf { state.confirmation == null }?.let { message ->
                item(key = "message-${message.id}") {
                    CartMessage(message, onDismiss = { onDismissMessage(message.id) })
                }
            }
            if (state.isBusy) item(key = "operation") {
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Actualizando carrito…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(cart.items, key = { it.productId }) { item ->
                CartLine(
                    item = item,
                    enabled = !state.isBusy && state.confirmation == null,
                    onDecrease = { onChangeQuantity(item.productId, item.quantity - 1) },
                    onIncrease = { onChangeQuantity(item.productId, item.quantity + 1) },
                    onRemove = { onRemoveProduct(item.productId) },
                )
            }
        }
        CartSummary(cart.subtotalMinor, enabled = !state.isBusy, onExploreProducts = onExploreProducts)
    }
}

@Composable
private fun CartLine(
    item: CartProductSnapshot,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(ChaskiDimens.SpacingMd)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
                Box(
                    modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // El snapshot local todavía no guarda URL de imagen; el icono también funciona sin conexión.
                    Icon(Icons.Filled.ShoppingCart,
                        contentDescription = "Imagen no disponible de ${item.productName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs)) {
                    Text(item.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatCartPrice(item.unitPriceMinor), style = MaterialTheme.typography.bodyMedium,
                        color = ChaskiPrice, fontWeight = FontWeight.SemiBold)
                    Text("Precio provisional por unidad", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease, enabled = enabled && item.quantity > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = "Reducir cantidad de ${item.productName}")
                    }
                    Text(item.quantity.toString(), modifier = Modifier.width(32.dp).semantics {
                        contentDescription = "Cantidad de ${item.productName}: ${item.quantity}"
                    },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onIncrease, enabled = enabled && item.quantity < Int.MAX_VALUE) {
                        Icon(Icons.Filled.Add, contentDescription = "Aumentar cantidad de ${item.productName}")
                    }
                }
                TextButton(onClick = onRemove, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Eliminar ${item.productName}"
                    }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(ChaskiDimens.SpacingXs))
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun CartSummary(subtotalMinor: Long, enabled: Boolean, onExploreProducts: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ChaskiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        ) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal provisional", style = MaterialTheme.typography.titleMedium)
                Text(formatCartPrice(subtotalMinor), style = MaterialTheme.typography.titleLarge,
                    color = ChaskiPrice, fontWeight = FontWeight.Bold)
            }
            Text(
                "Agregar al carrito no reserva disponibilidad ni fija el precio final.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChaskiButton(text = "Seguir explorando", onClick = onExploreProducts,
                enabled = enabled, variant = ChaskiButtonVariant.Outlined)
        }
    }
}

@Composable
private fun CartMessage(message: CartUiMessage, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = ChaskiDimens.SpacingMd), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message.text, modifier = Modifier.weight(1f).padding(ChaskiDimens.SpacingSm),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Descartar aviso")
            }
        }
    }
}

@Composable
private fun CartStoreChangeDialog(
    confirmation: CartOperationResult.ConfirmationRequired,
    busy: Boolean,
    message: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val incoming = confirmation.incomingItem
    Dialog(onDismissRequest = { if (!busy) onCancel() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(ChaskiDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
            ) {
                Text(if (confirmation.offerChanged) "La oferta cambió" else "¿Cambiar de local?",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (confirmation.offerChanged)
                        "Las condiciones de la oferta cambiaron. Revísalas antes de reemplazar el carrito."
                    else "Este producto es de otro local. Si continúas, reemplazará los productos de tu carrito.",
                )
                Text("${incoming.quantity} × ${incoming.productName}", fontWeight = FontWeight.SemiBold)
                Text("Precio provisional por unidad: ${formatCartPrice(incoming.unitPriceMinor)}", color = ChaskiPrice)
                Text("Tu carrito se conserva hasta que confirmes.", style = MaterialTheme.typography.bodySmall)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Validando oferta…")
                }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                ChaskiButton(
                    text = if (confirmation.offerChanged) "Confirmar oferta actualizada" else "Reemplazar carrito",
                    onClick = onConfirm,
                    enabled = !busy,
                )
                ChaskiButton(text = "Conservar mi carrito", onClick = onCancel,
                    enabled = !busy, variant = ChaskiButtonVariant.Outlined)
            }
        }
    }
}

@Preview(name = "Carrito con productos", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CartFilledPreview() {
    ChaskiTheme {
        CartContent(
            state = CartUiState(source = CartState.Ready(Cart("preview", listOf(
                CartProductSnapshot("business", "store", "product", "Producto de ejemplo", 1050, 2),
            )))),
            onBack = {}, onExploreProducts = {}, onRetry = {}, onChangeQuantity = { _, _ -> },
            onRemoveProduct = {}, onConfirmStoreChange = {}, onCancelStoreChange = {}, onDismissMessage = {},
        )
    }
}

@Preview(name = "Carrito vacío", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CartEmptyPreview() {
    ChaskiTheme {
        CartContent(
            state = CartUiState(source = CartState.Ready(Cart("preview"))),
            onBack = {}, onExploreProducts = {}, onRetry = {}, onChangeQuantity = { _, _ -> },
            onRemoveProduct = {}, onConfirmStoreChange = {}, onCancelStoreChange = {}, onDismissMessage = {},
        )
    }
}
