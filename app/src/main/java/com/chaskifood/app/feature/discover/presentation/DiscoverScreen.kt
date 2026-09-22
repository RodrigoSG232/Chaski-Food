@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.discover.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.chaskifood.app.ui.components.collectChaskiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.ui.components.ChaskiTextField
import com.chaskifood.app.ui.components.EmptyState
import com.chaskifood.app.ui.components.FoodCard
import com.chaskifood.app.ui.components.RestaurantCard
import com.chaskifood.app.ui.components.StateContent
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiTextMuted

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by collectChaskiState(viewModel.uiState)

    Column(modifier = modifier.fillMaxSize()) {
        DiscoverHeader()

        StateContent(state = state) { restaurants ->
            if (restaurants.isEmpty()) {
                EmptyState(message = "No hay restaurantes disponibles por ahora")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = ChaskiDimens.ScreenPadding,
                        end = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingSm,
                        bottom = ChaskiDimens.SpacingXl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                ) {
                    item { SectionTitle(text = "Populares cerca de ti") }
                    items(restaurants, key = { it.id }) { restaurant ->
                        RestaurantCard(
                            name = restaurant.name,
                            cuisine = restaurant.cuisine,
                            rating = restaurant.rating,
                            deliveryTimeMin = restaurant.deliveryTimeMin,
                        )
                    }
                    item { SectionTitle(text = "Recomendados para ti") }
                    items(DiscoverViewModel.recommendedFood, key = { it.id }) { food ->
                        FoodCard(
                            name = food.name,
                            description = food.description,
                            price = food.price,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingLg,
                bottom = ChaskiDimens.SpacingSm,
            ),
    ) {
        Text(
            text = "Buenas tardes,",
            style = MaterialTheme.typography.bodyMedium,
            color = ChaskiTextMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dónde quieres pedir hoy?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = ChaskiDimens.SpacingMd),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = ChaskiPrimary,
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingXs))
            Text(
                text = "San Isidro, Lima",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChaskiTextField(
            value = "",
            onValueChange = {},
            placeholder = "Buscar restaurantes o platillos",
            leadingIcon = Icons.Filled.Search,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = ChaskiDimens.SpacingSm),
    )
}