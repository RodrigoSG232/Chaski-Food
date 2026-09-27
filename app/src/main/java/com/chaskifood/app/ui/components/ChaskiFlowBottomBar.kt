package com.chaskifood.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

enum class FlowTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Filled.Home),
    Search("Buscar", Icons.Filled.Search),
    Orders("Pedidos", Icons.AutoMirrored.Filled.List),
    Account("Cuenta", Icons.Filled.Person),
}

/**
 * Barra de navegación inferior del flujo de compra del diseño:
 * 4 pestañas con la seleccionada en rojo y el resto en gris.
 */
@Composable
fun ChaskiFlowBottomBar(
    selected: FlowTab,
    onTabClick: (FlowTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ChaskiSurface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ChaskiDimens.BottomNavHeight)
                .padding(horizontal = ChaskiDimens.SpacingLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowTab.entries.forEach { tab ->
                val selectedTab = tab == selected
                Column(
                    modifier = Modifier
                        .clickable { onTabClick(tab) }
                        .padding(horizontal = ChaskiDimens.SpacingMd, vertical = ChaskiDimens.SpacingSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selectedTab) ChaskiPrimary else ChaskiTextMuted,
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab) FontWeight.Medium else FontWeight.Normal,
                        color = if (selectedTab) ChaskiPrimary else ChaskiTextMuted,
                    )
                }
            }
        }
    }
}