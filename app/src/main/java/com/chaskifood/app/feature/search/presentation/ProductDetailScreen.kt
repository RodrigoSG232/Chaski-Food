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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import java.util.Locale

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    Column(modifier.fillMaxSize().background(ChaskiBackground)) {
        ChaskiAppBar(title = "Detalle del producto", onBackClick = onBack)
        when (val current = state) {
            ProductDetailUiState.Loading -> ProductDetailMessage("Cargando producto…")
            is ProductDetailUiState.Success -> ProductDetailContent(current.product)
            is ProductDetailUiState.Unavailable -> ProductDetailMessage(current.message)
            is ProductDetailUiState.Error -> ProductDetailMessage(current.message)
        }
    }
}

@Composable
fun ProductDetailContent(product: StoreOfferProduct, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
                "Imagen del producto · Firebase Storage",
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
private fun ProductDetailMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(ChaskiDimens.SpacingXl), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
