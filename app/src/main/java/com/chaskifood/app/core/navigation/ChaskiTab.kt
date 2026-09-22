@file:Suppress("DEPRECATION")

package com.chaskifood.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

object ChaskiDestinations {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
}

enum class ChaskiTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(route = "main/home", label = "Inicio", icon = Icons.Filled.Home),
    SEARCH(route = "main/search", label = "Buscar", icon = Icons.Filled.Search),
    ORDERS(route = "main/orders", label = "Pedidos", icon = Icons.Filled.ShoppingCart),
    ACCOUNT(route = "main/account", label = "Cuenta", icon = Icons.Filled.Person),
}