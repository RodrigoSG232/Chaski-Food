package com.chaskifood.app.feature.discover.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import java.util.Locale

@Composable
fun AvailableStoresContent(
    state: AvailableStoresUiState,
    onSelectAddress: () -> Unit,
    onRetry: () -> Unit,
    onStoreClick: (DiscoverableStore) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        if (showTitle) {
            Text(
                text = "Locales disponibles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (state) {
            AvailableStoresUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().height(144.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ChaskiPrimary)
            }
            AvailableStoresUiState.SignedOut -> AvailableStoresMessage(
                title = "Inicia sesión para descubrir locales",
                description = "Necesitamos tu cuenta y una dirección de entrega para mostrar opciones disponibles.",
            )
            AvailableStoresUiState.AddressRequired -> AvailableStoresMessage(
                title = "Selecciona una dirección de entrega",
                description = "Usaremos esa ubicación para comprobar qué locales tienen cobertura.",
                action = "SELECCIONAR DIRECCIÓN",
                onAction = onSelectAddress,
            )
            is AvailableStoresUiState.Empty -> AvailableStoresMessage(
                title = "No hay locales disponibles para esta dirección",
                description = "Por ahora ningún local habilitado, abierto y operativo cubre ${state.selectedAddress.addressText}.",
                action = "CAMBIAR DIRECCIÓN",
                onAction = onSelectAddress,
            )
            is AvailableStoresUiState.Error -> AvailableStoresMessage(
                title = "No pudimos consultar los locales",
                description = state.message,
                action = "REINTENTAR",
                onAction = onRetry,
                isError = true,
            )
            is AvailableStoresUiState.Success -> {
                Text(
                    text = "Para ${state.selectedAddress.label}: ${state.selectedAddress.addressText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.stores.forEach { store ->
                    AvailableStoreCard(store = store, onClick = { onStoreClick(store) })
                }
                if (state.stores.any { it.roadDistanceKm != null }) {
                    Text(
                        text = "Rutas estimadas con OSRM · © OpenStreetMap contributors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailableStoresMessage(
    title: String,
    description: String,
    action: String? = null,
    onAction: () -> Unit = {},
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(ChaskiDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.LocationOn,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else ChaskiPrimary,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(ChaskiDimens.SpacingXs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(ChaskiDimens.SpacingMd))
            ChaskiButton(text = action, onClick = onAction, variant = ChaskiButtonVariant.Accent)
        }
    }
}

@Composable
private fun AvailableStoreCard(
    store: DiscoverableStore,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = ChaskiPrimary)
                if (!store.imageUrl.isNullOrBlank()) AsyncImage(
                    model = store.imageUrl,
                    contentDescription = "Imagen de ${store.name}",
                    modifier = Modifier.fillMaxWidth().height(156.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(store.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (store.businessName != store.name) {
                    Text(store.businessName, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "${store.businessCategory} · ${distanceSummary(store)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = store.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDistance(distanceKm: Double): String =
    String.format(Locale.forLanguageTag("es-PE"), "%.1f km", distanceKm)

private fun distanceSummary(store: DiscoverableStore): String {
    val routeKm = store.roadDistanceKm
    val routeMinutes = store.roadDurationMinutes
    return if (routeKm != null && routeMinutes != null) {
        "${formatDistance(routeKm)} por ruta · $routeMinutes min aprox."
    } else {
        "${formatDistance(store.distanceKm)} en línea recta"
    }
}
