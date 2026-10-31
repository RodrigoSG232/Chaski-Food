package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.cart.presentation.CartFeedbackBanner
import com.chaskifood.app.feature.cart.presentation.CartAccessButton
import com.chaskifood.app.feature.cart.presentation.CartUiState
import com.chaskifood.app.feature.cart.presentation.CartUiMessageKind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import java.util.Locale

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    onSignIn: () -> Unit,
    onSelectAddress: () -> Unit,
    cartState: CartUiState,
    onAddToCart: (String, String, Int) -> Unit,
    onDismissCartMessage: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAdded: (storeId: String) -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    // Solo una confirmación «agregado» posterior a ESTE toque devuelve al catálogo del local; errores y cambios de
    // local siguen resolviéndose aquí.
    var awaitingAddFor by rememberSaveable { mutableStateOf<String?>(null) }
    val added = cartState.message?.takeIf { it.kind == CartUiMessageKind.Success }
    LaunchedEffect(added?.id, awaitingAddFor) {
        val storeId = awaitingAddFor
        if (added != null && storeId != null) { awaitingAddFor = null; onAdded(storeId) }
    }
    Column(modifier.fillMaxSize().background(ChaskiBackground)) {
        ChaskiAppBar(title = "Detalle del producto", onBackClick = onBack, actions = {
            CartAccessButton(totalUnits = cartState.totalUnits, onClick = onOpenCart)
        })
        when (val current = state) {
            ProductDetailUiState.Loading -> ProductDetailMessage("Cargando producto…")
            is ProductDetailUiState.Success -> {
                val product = current.product
                var quantity by rememberSaveable(product.storeId, product.id) { mutableIntStateOf(1) }
                Column(Modifier.fillMaxSize()) {
                    ProductDetailContent(product, Modifier.weight(1f))
                    ProductDetailPurchase(
                        product = product,
                        quantity = quantity,
                        onQuantityChange = { quantity = it },
                        cartState = cartState,
                        onAddToCart = { storeId, productId, units ->
                            awaitingAddFor = storeId; onAddToCart(storeId, productId, units)
                        },
                        onSignIn = onSignIn,
                        onSelectAddress = onSelectAddress,
                        onDismissCartMessage = onDismissCartMessage,
                    )
                }
            }
            is ProductDetailUiState.Unavailable -> ProductDetailMessage(
                message = current.message,
                action = when {
                    cartState.source == CartState.SignedOut -> "Iniciar sesión" to onSignIn
                    current.message.startsWith("Selecciona una dirección de entrega") ->
                        "Elegir dirección" to onSelectAddress
                    else -> null
                },
            )
            is ProductDetailUiState.Error -> ProductDetailMessage(current.message)
        }
    }
}

@Composable
internal fun ProductDetailPurchase(
    product: StoreOfferProduct,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    cartState: CartUiState,
    onAddToCart: (String, String, Int) -> Unit,
    onSignIn: () -> Unit,
    onSelectAddress: () -> Unit,
    onDismissCartMessage: (Long) -> Unit,
) {
    val busy = cartState.isBusy
    val signedOut = cartState.source == CartState.SignedOut
    val canAdd = cartState.source is CartState.Ready && !busy && cartState.confirmation == null
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(ChaskiDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        cartState.message?.takeIf { cartState.confirmation == null }?.let { message ->
            CartFeedbackBanner(message, onDismiss = { onDismissCartMessage(message.id) },
                onSelectAddress = onSelectAddress)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
            IconButton(onClick = { onQuantityChange(quantity - 1) }, enabled = canAdd && quantity > 1) {
                Icon(Icons.Filled.Remove, contentDescription = "Reducir cantidad")
            }
            Text(quantity.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onQuantityChange(quantity + 1) },
                enabled = canAdd && quantity < Int.MAX_VALUE) {
                Icon(Icons.Filled.Add, contentDescription = "Aumentar cantidad")
            }
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (signedOut) ChaskiButton(text = "Iniciar sesión", onClick = onSignIn)
        else ChaskiButton(text = if (busy) "Validando oferta…" else "Agregar al carrito",
            onClick = { onAddToCart(product.storeId, product.id, quantity) }, enabled = canAdd)
        Text("Precio y disponibilidad provisionales; agregar no reserva el producto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProductDetailContent(product: StoreOfferProduct, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
    ) {
        ProductImage(product)
        Column(
            modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            Text(
                "S/ %.2f".format(Locale.ROOT, product.price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ChaskiPrice,
            )
            Text(
                product.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "${product.categoryName} · ${product.storeName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(product.description, style = MaterialTheme.typography.bodyLarge)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Tiempo estimado: ${product.prepTimeMinutes} min")
            }
        }
    }
}

@Composable
private fun ProductImage(product: StoreOfferProduct) {
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sin imagen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!product.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = "Imagen de ${product.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ProductDetailMessage(message: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxSize().padding(ChaskiDimens.SpacingXl),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (action != null) ChaskiButton(text = action.first,
            onClick = action.second, variant = ChaskiButtonVariant.Outlined)
    }
}
