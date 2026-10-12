package com.chaskifood.app.feature.cart.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiPrimary

/** El contador se deriva del carrito observado; null significa sesión no confirmada. */
@Composable
fun CartAccessButton(
    totalUnits: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = if (totalUnits == null) "Ver carrito"
                else "Ver carrito, $totalUnits unidades"
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.CenterStart) {
                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(28.dp))
                if (totalUnits != null && totalUnits > 0) Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                        .background(ChaskiPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (totalUnits > 99) "99+" else totalUnits.toString(),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (showLabel) {
                Spacer(Modifier.width(4.dp))
                Text("Carrito", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
