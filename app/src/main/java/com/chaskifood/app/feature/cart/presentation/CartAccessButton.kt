package com.chaskifood.app.feature.cart.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiPrimary

/** El contador se deriva del carrito observado; null significa sesión no confirmada. */
@Composable
fun CartAccessButton(totalUnits: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = if (totalUnits == null) "Ver carrito"
                else "Ver carrito, $totalUnits unidades"
        },
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(28.dp))
            if (totalUnits != null && totalUnits > 0) Box(
                modifier = Modifier.size(18.dp).background(ChaskiPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (totalUnits > 99) "99+" else totalUnits.toString(),
                    fontSize = 10.sp, color = Color.White)
            }
        }
    }
}
