package com.chaskifood.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiTextMuted

/**
 * Tarjeta de restaurante del diseño: imagen 76dp + nombre, cocina,
 * calificación y tiempo de entrega.
 */
@Composable
fun RestaurantCard(
    name: String,
    cuisine: String,
    rating: Double,
    deliveryTimeMin: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = ChaskiSurface,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(ChaskiDimens.SpacingMd)
                .clip(MaterialTheme.shapes.large),
            verticalAlignment = Alignment.Top,
        ) {
            RestaurantImagePlaceholder(
                name = name,
                size = 76,
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = cuisine,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChaskiTextMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBadge(rating = rating)
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    Text(
                        text = "≈ $deliveryTimeMin min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun RestaurantImagePlaceholder(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 76,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .size(size.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB0B0B0),
            )
        }
    }
}