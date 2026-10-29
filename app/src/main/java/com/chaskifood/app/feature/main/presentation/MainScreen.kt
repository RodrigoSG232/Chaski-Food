package com.chaskifood.app.feature.main.presentation

import com.chaskifood.app.ui.components.EmptyState

import com.chaskifood.app.core.navigation.ChaskiDestinations

import com.chaskifood.app.core.navigation.LocalNavigationAccess

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.business.presentation.CommercialHomeScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.core.navigation.ChaskiTab
import com.chaskifood.app.core.navigation.selectMainTab
import com.chaskifood.app.feature.home.presentation.HomeScreen
import com.chaskifood.app.feature.orders.presentation.OrdersScreen
import com.chaskifood.app.feature.profile.presentation.AccountScreen
import com.chaskifood.app.feature.search.presentation.OfferSearchScreen
import com.chaskifood.app.ui.components.ChaskiBottomNav
import com.chaskifood.app.ui.theme.ChaskiSurface

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    requestedTab: ChaskiTab? = null,
    onTabRequestHandled: () -> Unit = {},
    deliveryAddress: String? = null,
    deliveryAddressUnavailable: Boolean = false,
    deliveryNotice: String? = null,
    onDismissDeliveryNotice: () -> Unit = {},
    onSeeAllRestaurants: () -> Unit = {},
    onOpenCart: () -> Unit = {},
    cartUnits: Long? = null,
    onRestaurantClick: (String) -> Unit = {},
    onProductClick: (String, String) -> Unit = { _, _ -> },
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
    onOpenStoreOperations: () -> Unit = {},
    onOpenManageCatalog: () -> Unit = {},
    onOpenStoreProductConfiguration: () -> Unit = {},
    onOpenAdminReview: () -> Unit = {},
    onOpenAdminSupervision: () -> Unit = {},
    onOpenAdminAuditLog: () -> Unit = {},
    onOpenManageStores: () -> Unit = {},
    onOpenSelectedHours: (String?, String?) -> Unit = { _, _ -> },
    onOpenSelectedOffers: (String?, String?) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {},
) {
    val navController = rememberNavController()
    val access = LocalNavigationAccess.current
    val observed = access?.states?.collectAsStateWithLifecycle()?.value
    val resolved = access?.current()?.takeIf { it.origin == observed?.origin }?.resolution as? AccessResolution.Resolved
    val experience = resolved?.experience ?: CommercialExperience.CLIENT
    val commercial = experience != CommercialExperience.CLIENT
    val localCount = resolved?.capabilities?.grants.orEmpty().filter { it.capability == AccessCapability.READ_STORE_ORDERS }
        .mapNotNull { it.scope as? AccessScope.Store }.distinct().size
    val homeLabel = if (experience == CommercialExperience.OWNER) "Mi negocio" else if (localCount > 1) "Mis locales" else "Mi local"
    LaunchedEffect(commercial) {
        if (commercial && navController.currentDestination?.route in listOf(ChaskiTab.SEARCH.route, ChaskiTab.ORDERS.route)) {
            navController.selectMainTab(ChaskiTab.HOME)
        }
    }

    LaunchedEffect(requestedTab, commercial) {
        if (requestedTab != null) {
            navController.selectMainTab(if (commercial && requestedTab != ChaskiTab.ACCOUNT) ChaskiTab.HOME else requestedTab)
            onTabRequestHandled()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = ChaskiSurface,
        bottomBar = { if (commercial) CommercialBottomNav(navController, homeLabel) else ChaskiBottomNav(navController = navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChaskiTab.HOME.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(ChaskiTab.HOME.route) {
                if (commercial) CommercialHomeScreen(onOpenManageStores, onOpenManageCatalog, onOpenSelectedHours, onOpenSelectedOffers)
                else if (access == null || access.allows(ChaskiDestinations.CART)) HomeScreen(
                    deliveryAddress = deliveryAddress,
                    deliveryAddressUnavailable = deliveryAddressUnavailable,
                    deliveryNotice = deliveryNotice,
                    onDismissDeliveryNotice = onDismissDeliveryNotice,
                    onSeeAll = onSeeAllRestaurants,
                    onRestaurantClick = onRestaurantClick,
                    onOpenLocations = onOpenLocations,
                    onOpenCart = onOpenCart,
                    cartUnits = cartUnits,
                ) else EmptyState("Utiliza Cuenta para acceder a tus funciones autorizadas.")
            }
            composable(ChaskiTab.SEARCH.route) {
                if (access == null || access.allows(ChaskiDestinations.CART)) OfferSearchScreen(
                    onStoreClick = onRestaurantClick,
                    onProductClick = onProductClick,
                    onSelectAddress = onOpenLocations,
                ) else EmptyState("Las compras nuevas no están habilitadas para esta cuenta.")
            }
            composable(ChaskiTab.ORDERS.route) {
                if (!commercial) OrdersScreen(
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
                    onOpenStoreOperations = onOpenStoreOperations,
                    onOpenManageCatalog = onOpenManageCatalog,
                    onOpenStoreProductConfiguration = onOpenStoreProductConfiguration,
                    onOpenAdminReview = onOpenAdminReview,
                    onOpenAdminSupervision = onOpenAdminSupervision,
                    onOpenAdminAuditLog = onOpenAdminAuditLog,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}
