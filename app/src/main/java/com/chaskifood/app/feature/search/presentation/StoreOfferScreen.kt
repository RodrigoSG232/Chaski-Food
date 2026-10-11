package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun StoreOfferScreen(
    onBack: () -> Unit,
    onProductClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreOfferViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    Column(modifier.fillMaxSize()) {
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
}

@Composable
fun StoreOfferContent(
    offer: StoreOffer,
    onProductClick: (com.chaskifood.app.feature.search.domain.StoreOfferProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember(offer.store.id) { mutableStateOf<String?>(null) }
    val products = offer.products.filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        item(key = "store") {
            Column(
                Modifier.fillMaxWidth().padding(ChaskiDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(offer.store.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(offer.store.address, style = MaterialTheme.typography.bodyMedium)
                Text(offer.store.businessCategory, style = MaterialTheme.typography.bodySmall)
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
                        label = { Text(category.name) },
                    )
                }
            }
        }
        if (products.isEmpty()) item(key = "empty") {
            StoreOfferMessage("Este local no tiene productos disponibles por ahora.")
        } else items(products, key = { it.id }) { product ->
            ProductResultCard(
                product = product,
                onClick = { onProductClick(product) },
                modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
            )
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
