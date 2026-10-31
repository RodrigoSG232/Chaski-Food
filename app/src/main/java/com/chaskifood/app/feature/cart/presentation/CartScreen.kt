package com.chaskifood.app.feature.cart.presentation

import com.chaskifood.app.core.navigation.ChaskiDestinations

import com.chaskifood.app.core.navigation.LocalProtectedAction

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.cart.domain.Cart
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

@Composable
fun CartScreen(
    onBack: () -> Unit,
    onExploreProducts: () -> Unit,
    onCheckout: () -> Unit = {},
    onSignIn: () -> Unit = onBack,
    onSelectAddress: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val purchase = LocalProtectedAction.current
    Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        topBar = { ChaskiAppBar(title = "Mi carrito", onBackClick = onBack) },
    ) { padding ->
        CartContent(
            state = state,
            onBack = onBack,
            onExploreProducts = onExploreProducts,
            onCheckout = { purchase(onCheckout) },
            onSignIn = onSignIn,
            onSelectAddress = onSelectAddress,
            onRetry = viewModel::retryLoading,
            onChangeQuantity = { id, quantity -> purchase { viewModel.changeQuantity(id, quantity) } },
            onRemoveProduct = { id -> purchase { viewModel.removeProduct(id) } },
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
    onCheckout: () -> Unit = {},
    onSignIn: () -> Unit = onBack,
    onSelectAddress: () -> Unit = {},
    onRetry: () -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onRemoveProduct: (String) -> Unit,
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
                action = "Iniciar sesión",
                onAction = onSignIn,
            )
            is CartState.Ready -> if (source.cart.items.isEmpty()) {
                CartPageState(
                    title = "Tu carrito está vacío",
                    description = "Elige productos de un local y organízalos aquí antes de continuar.",
                    action = "Explorar productos",
                    onAction = onExploreProducts,
                    busy = state.isBusy,
                    message = state.message.takeIf { state.confirmation == null },
                    onDismissMessage = onDismissMessage,
                    onSelectAddress = onSelectAddress,
                )
            } else {
                CartReadyContent(
                    cart = source.cart,
                    state = state,
                    onExploreProducts = onExploreProducts,
                    onCheckout = onCheckout,
                    onChangeQuantity = onChangeQuantity,
                    onRemoveProduct = onRemoveProduct,
                    onDismissMessage = onDismissMessage,
                    onSelectAddress = onSelectAddress,
                )
            }
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
    onSelectAddress: () -> Unit = {},
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
        message?.let { CartFeedbackBanner(it, onDismiss = { onDismissMessage(it.id) },
            onSelectAddress = onSelectAddress) }
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
    onCheckout: () -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onDismissMessage: (Long) -> Unit,
    onSelectAddress: () -> Unit,
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
                    CartFeedbackBanner(message, onDismiss = { onDismissMessage(message.id) },
                        onSelectAddress = onSelectAddress)
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
        CartSummary(cart.subtotalMinor, enabled = !state.isBusy && state.confirmation == null,
            onExploreProducts = onExploreProducts, onCheckout = onCheckout)
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
    val lineSubtotal = formatCartPrice(Math.multiplyExact(item.unitPriceMinor, item.quantity.toLong()))
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
                    Text("Precio unitario provisional", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCartPrice(item.unitPriceMinor), style = MaterialTheme.typography.bodyMedium,
                        color = ChaskiPrice, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Cantidad: ${item.quantity}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().semantics {
                contentDescription = "Subtotal de ${item.productName}: $lineSubtotal"
            }) {
                Text("Subtotal del producto", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lineSubtotal,
                    style = MaterialTheme.typography.titleMedium, color = ChaskiPrice,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CartSummary(subtotalMinor: Long, enabled: Boolean,
    onExploreProducts: () -> Unit, onCheckout: () -> Unit) {
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
            ChaskiButton(text = "Continuar", onClick = onCheckout, enabled = enabled)
            ChaskiButton(text = "Seguir explorando", onClick = onExploreProducts,
                enabled = enabled, variant = ChaskiButtonVariant.Outlined)
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
            onBack = {}, onExploreProducts = {}, onCheckout = {}, onRetry = {}, onChangeQuantity = { _, _ -> },
            onRemoveProduct = {}, onDismissMessage = {},
        )
    }
}

@Preview(name = "Carrito vacío", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CartEmptyPreview() {
    ChaskiTheme {
        CartContent(
            state = CartUiState(source = CartState.Ready(Cart("preview"))),
            onBack = {}, onExploreProducts = {}, onCheckout = {}, onRetry = {}, onChangeQuantity = { _, _ -> },
            onRemoveProduct = {}, onDismissMessage = {},
        )
    }
}
