package com.chaskifood.app.feature.main.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.chaskifood.app.core.navigation.*
import com.chaskifood.app.ui.theme.*

@Composable
internal fun CommercialBottomNav(navController: NavHostController, homeLabel: String) {
    val entry by navController.currentBackStackEntryAsState()
    CommercialBottomNavContent(homeLabel, entry?.destination?.route == ChaskiTab.ACCOUNT.route) { navController.selectMainTab(it) }
}

@Composable
internal fun CommercialBottomNavContent(homeLabel: String, accountSelected: Boolean, onSelect: (ChaskiTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        listOf(ChaskiTab.HOME, ChaskiTab.ACCOUNT).forEach { tab ->
            val label = if (tab == ChaskiTab.HOME) homeLabel else tab.label
            NavigationBarItem(selected = if (tab == ChaskiTab.ACCOUNT) accountSelected else !accountSelected,
                onClick = { onSelect(tab) }, icon = { Icon(if (tab == ChaskiTab.HOME) Icons.Default.Storefront else tab.icon, null) }, label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ChaskiPrimary, selectedTextColor = ChaskiPrimary,
                    indicatorColor = ChaskiSurfaceVariant, unselectedIconColor = ChaskiTextMuted, unselectedTextColor = ChaskiTextMuted))
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable private fun CommercialNavigationPreview() { ChaskiTheme { CommercialBottomNavContent("Mis locales", false) {} } }