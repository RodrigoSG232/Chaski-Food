package com.chaskifood.app.feature.main.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.core.navigation.ChaskiTab
import com.chaskifood.app.feature.home.presentation.HomeScreen
import com.chaskifood.app.feature.orders.presentation.OrdersScreen
import com.chaskifood.app.feature.profile.presentation.AccountScreen
import com.chaskifood.app.feature.search.presentation.SearchFoodScreen
import com.chaskifood.app.ui.components.ChaskiBottomNav
import com.chaskifood.app.ui.theme.ChaskiSurface

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    deliveryAddress: String? = null,
    deliveryAddressUnavailable: Boolean = false,
    deliveryNotice: String? = null,
    onDismissDeliveryNotice: () -> Unit = {},
    onSeeAllRestaurants: () -> Unit = {},
    onOpenYourOrder: () -> Unit = {},
    onRestaurantClick: (String) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenFood: (String) -> Unit = {},
    onTrack: (String) -> Unit = {},
    onDelivered: (String) -> Unit = {},
    onOpenPaymentMethods: () -> Unit = {},
    onOpenLocations: () -> Unit = {},
    onOpenReferral: () -> Unit = {},
    onOpenProfileInfo: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenSocialAccounts: () -> Unit = {},
    onOpenBusiness: () -> Unit = {},
    onOpenAdminReview: () -> Unit = {},
    onOpenAdminSupervision: () -> Unit = {},
    onOpenAdminAuditLog: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        containerColor = ChaskiSurface,
        bottomBar = { ChaskiBottomNav(navController = navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChaskiTab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ChaskiTab.HOME.route) {
                HomeScreen(
                    deliveryAddress = deliveryAddress,
                    deliveryAddressUnavailable = deliveryAddressUnavailable,
                    deliveryNotice = deliveryNotice,
                    onDismissDeliveryNotice = onDismissDeliveryNotice,
                    onSeeAll = onSeeAllRestaurants,
                    onRestaurantClick = onRestaurantClick,
                    onOpenLocations = onOpenLocations,
                )
            }
            composable(ChaskiTab.SEARCH.route) {
                SearchFoodScreen(
                    onOpenCategories = onOpenCategories,
                    onRestaurantClick = onRestaurantClick,
                    onFoodClick = onOpenFood,
                )
            }
            composable(ChaskiTab.ORDERS.route) {
                OrdersScreen(
                    onTrack = onTrack,
                    onDelivered = onDelivered,
                )
            }
            composable(ChaskiTab.ACCOUNT.route) {
                AccountScreen(
                    onOpenProfileInfo = onOpenProfileInfo,
                    onOpenSecurity = onOpenSecurity,
                    onOpenPaymentMethods = onOpenPaymentMethods,
                    onOpenLocations = onOpenLocations,
                    onOpenSocialAccounts = onOpenSocialAccounts,
                    onOpenReferral = onOpenReferral,
                    onOpenBusiness = onOpenBusiness,
                    onOpenAdminReview = onOpenAdminReview,
                    onOpenAdminSupervision = onOpenAdminSupervision,
                    onOpenAdminAuditLog = onOpenAdminAuditLog,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}
