package com.chaskifood.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiRating
import com.chaskifood.app.ui.theme.ChaskiRatingBadge
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

/**
 * Píldora de calificación (estrella + valor) del diseño.
 */
@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChaskiRatingBadge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = ChaskiRating,
        )
        Text(
            text = rating.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = ChaskiTextPrimary,
        )
    }
}