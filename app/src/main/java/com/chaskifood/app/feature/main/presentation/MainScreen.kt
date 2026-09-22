package com.chaskifood.app.feature.main.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.core.navigation.ChaskiTab
import com.chaskifood.app.feature.home.presentation.HomeScreen
import com.chaskifood.app.ui.components.ChaskiBottomNav

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { ChaskiBottomNav(navController = navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChaskiTab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ChaskiTab.HOME.route) {
                HomeScreen()
            }
            composable(ChaskiTab.SEARCH.route) {
                PlaceholderScreen(title = "Buscar", message = "Busca restaurantes y platillos")
            }
            composable(ChaskiTab.ORDERS.route) {
                PlaceholderScreen(title = "Pedidos", message = "Aún no tienes pedidos")
            }
            composable(ChaskiTab.ACCOUNT.route) {
                PlaceholderScreen(title = "Cuenta", message = "Inicia sesión para ver tu cuenta")
            }
        }
    }
}