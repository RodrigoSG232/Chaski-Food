package com.chaskifood.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurfaceVariant
import com.chaskifood.app.ui.theme.ChaskiTextMuted

enum class FlowTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Filled.Home),
    Search("Buscar", Icons.Filled.Search),
    Orders("Pedidos", Icons.Filled.ShoppingCart),
    Account("Cuenta", Icons.Filled.Person),
}

/**
 * Barra de navegación inferior unificada para todo el flujo de la aplicación.
 * Mantiene la misma estética (NavigationBar Material 3, colores e indicadores)
 * que ChaskiBottomNav para una experiencia de usuario uniforme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChaskiFlowBottomBar(
    selected: FlowTab,
    onTabClick: (FlowTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (WindowInsets.isImeVisible) return
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        FlowTab.entries.forEach { tab ->
            val selectedTab = tab == selected
            NavigationBarItem(
                selected = selectedTab,
                onClick = { onTabClick(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ChaskiPrimary,
                    selectedTextColor = ChaskiPrimary,
                    indicatorColor = ChaskiSurfaceVariant,
                    unselectedIconColor = ChaskiTextMuted,
                    unselectedTextColor = ChaskiTextMuted,
                ),
            )
        }
    }
}
