package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.ChaskiTextField
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import java.util.Locale

@Composable
fun OfferSearchScreen(
    onStoreClick: (String) -> Unit,
    onProductClick: (String, String) -> Unit,
    onSelectAddress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfferSearchViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    OfferSearchContent(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onCategorySelected = viewModel::selectCategory,
        onStoreClick = { onStoreClick(it.id) },
        onProductClick = { onProductClick(it.storeId, it.id) },
        onSelectAddress = onSelectAddress,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun OfferSearchContent(
    state: OfferSearchUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onStoreClick: (DiscoverableStore) -> Unit,
    onProductClick: (StoreOfferProduct) -> Unit,
    onSelectAddress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        OfferSearchUiState.Loading -> SearchMessage("Buscando oferta disponible…", modifier)
        OfferSearchUiState.SignedOut -> SearchMessage("Inicia sesión para buscar establecimientos y productos.", modifier)
        OfferSearchUiState.AddressRequired -> SearchMessage(
            message = "Selecciona una dirección para buscar locales con cobertura.",
            modifier = modifier,
            action = "SELECCIONAR DIRECCIÓN",
            onAction = onSelectAddress,
        )
        is OfferSearchUiState.Error -> SearchMessage(
            message = state.message,
            modifier = modifier,
            action = "REINTENTAR",
            onAction = onRetry,
        )
        is OfferSearchUiState.Content -> LazyColumn(
            modifier = modifier.fillMaxSize().background(ChaskiBackground),
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            item(key = "search") {
                Column(
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        end = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                ) {
                    Text(
                        "Buscar establecimientos o productos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    ChaskiTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = "Ej. ceviche, postres o Miraflores",
                        leadingIcon = Icons.Filled.Search,
                    )
                }
            }
            if (state.categories.isNotEmpty()) item(key = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ChaskiDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("Todos") },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { onCategorySelected(category.id) },
                            label = { Text(category.name, modifier = Modifier.widthIn(max = 220.dp)) },
                        )
                    }
                }
            }
            if (state.results.stores.isEmpty() && state.results.products.isEmpty()) {
                item(key = "empty") {
                    SearchMessage(
                        "No encontramos opciones disponibles con estos criterios.",
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                if (state.results.stores.isNotEmpty()) {
                    item(key = "stores-title") { SearchSectionTitle("Establecimientos") }
                    items(state.results.stores, key = { "store-${it.id}" }) { store ->
                        StoreResultCard(
                            store,
                            onClick = { onStoreClick(store) },
                            modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
                        )
                    }
                }
                if (state.results.products.isNotEmpty()) {
                    item(key = "products-title") { SearchSectionTitle("Productos") }
                    items(state.results.products, key = { "${it.storeId}-${it.id}" }) { product ->
                        ProductResultCard(
                            product,
                            onClick = { onProductClick(product) },
                            modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding).semantics { heading() },
    )
}

@Composable
private fun StoreResultCard(store: DiscoverableStore, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(ChaskiDimens.SpacingMd), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(store.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(store.address, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${store.businessCategory} · ${String.format(Locale.forLanguageTag("es-PE"), "%.1f km", store.distanceKm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProductResultCard(product: StoreOfferProduct, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(ChaskiDimens.SpacingMd),
            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.padding(18.dp),
                )
                    if (!product.imageUrl.isNullOrBlank()) AsyncImage(
                        model = product.imageUrl,
                        contentDescription = "Imagen de ${product.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${product.categoryName} · ${product.storeName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    product.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "S/ %.2f".format(Locale.ROOT, product.price),
                color = ChaskiPrice,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SearchMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ChaskiDimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            ChaskiButton(text = action, onClick = onAction, variant = ChaskiButtonVariant.Accent)
        }
    }
}
