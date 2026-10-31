package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import com.chaskifood.app.feature.cart.presentation.CartUiMessage
import com.chaskifood.app.feature.cart.presentation.CartUiMessageKind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import java.util.Locale

@Composable
fun StoreOfferScreen(
    onBack: () -> Unit,
    onProductClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    addedMessage: CartUiMessage? = null,
    onOpenCart: () -> Unit = {},
    onDismissAdded: (Long) -> Unit = {},
    viewModel: StoreOfferViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    val snackbar = remember { SnackbarHostState() }
    // Tras «Agregar al carrito» se vuelve aquí para seguir eligiendo; el aviso ofrece ir al carrito.
    LaunchedEffect(addedMessage?.id) {
        val message = addedMessage?.takeIf { it.kind == CartUiMessageKind.Success } ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.text, actionLabel = "Ver carrito", withDismissAction = true,
            duration = SnackbarDuration.Long)
        onDismissAdded(message.id)
        if (result == SnackbarResult.ActionPerformed) onOpenCart()
    }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ChaskiAppBar(title = "Oferta del local", onBackClick = onBack)
        when (val current = state) {
            StoreOfferUiState.Loading -> StoreOfferMessage("Cargando oferta…")
            is StoreOfferUiState.Unavailable -> StoreOfferMessage(current.message)
            is StoreOfferUiState.Error -> StoreOfferMessage(current.message)
            is StoreOfferUiState.Success -> StoreOfferContent(
                offer = current.offer,
                onProductClick = { onProductClick(it.storeId, it.id) },
            )
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(ChaskiDimens.SpacingLg))
    }
}

@Composable
fun StoreOfferContent(
    offer: StoreOffer,
    onProductClick: (com.chaskifood.app.feature.search.domain.StoreOfferProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember(offer.store.id) { mutableStateOf<String?>(null) }
    val visibleCategories = offer.categories.filter { selectedCategoryId == null || it.id == selectedCategoryId }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        item(key = "store") {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(190.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(52.dp))
                    if (!offer.store.imageUrl.isNullOrBlank()) AsyncImage(
                        model = offer.store.imageUrl,
                        contentDescription = "Imagen de ${offer.store.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(ChaskiDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(offer.store.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(offer.store.address, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${offer.store.businessCategory} · ${String.format(Locale.forLanguageTag("es-PE"), "%.1f km", offer.store.distanceKm)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (offer.categories.isNotEmpty()) item(key = "categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ChaskiDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("Todos") },
                    )
                }
                items(offer.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name, modifier = Modifier.widthIn(max = 220.dp)) },
                    )
                }
            }
        }
        if (offer.products.isEmpty()) item(key = "empty") {
            StoreOfferMessage("Este local no tiene productos disponibles por ahora.")
        } else visibleCategories.forEach { category ->
            val categoryProducts = offer.products.filter { it.categoryId == category.id }
            if (categoryProducts.isNotEmpty()) {
                item(key = "title-${category.id}") {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
                    )
                }
                item(key = "products-${category.id}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ChaskiDimens.ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                    ) {
                        items(categoryProducts, key = { it.id }) { product ->
                            StoreProductTile(product, onClick = { onProductClick(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreProductTile(product: StoreOfferProduct, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(168.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(132.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(38.dp))
                if (!product.imageUrl.isNullOrBlank()) AsyncImage(
                    model = product.imageUrl,
                    contentDescription = "Imagen de ${product.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier.padding(ChaskiDimens.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "S/ %.2f".format(Locale.ROOT, product.price),
                    color = ChaskiPrice,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StoreOfferMessage(message: String) {
    Column(
        Modifier.fillMaxSize().padding(ChaskiDimens.SpacingXl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
