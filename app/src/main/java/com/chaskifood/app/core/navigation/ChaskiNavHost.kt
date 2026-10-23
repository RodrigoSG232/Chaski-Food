package com.chaskifood.app.core.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.currentBackStackEntryAsState
import com.chaskifood.app.ui.components.DemoNotice
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.address.presentation.AddressRoute
import com.chaskifood.app.feature.address.presentation.AddressAction
import com.chaskifood.app.feature.address.presentation.AddressViewModel
import com.chaskifood.app.feature.auth.presentation.CheckEmailScreen
import com.chaskifood.app.feature.auth.presentation.ForgotPasswordScreen
import com.chaskifood.app.feature.auth.presentation.LocationScreen
import com.chaskifood.app.feature.auth.presentation.LocationSearchScreen
import com.chaskifood.app.feature.auth.presentation.PhoneAuthViewModel
import com.chaskifood.app.feature.auth.presentation.SignInScreen
import com.chaskifood.app.feature.auth.presentation.SignUpPhoneScreen
import com.chaskifood.app.feature.auth.presentation.SignUpScreen
import com.chaskifood.app.feature.auth.presentation.VerifyPhoneScreen
import com.chaskifood.app.feature.business.presentation.AdminAuditLogScreen
import com.chaskifood.app.feature.business.presentation.AdminBusinessReviewScreen
import com.chaskifood.app.feature.business.presentation.AdminBusinessSupervisionScreen
import com.chaskifood.app.feature.business.presentation.BusinessStatusScreen
import com.chaskifood.app.feature.business.presentation.ManageStoresScreen
import com.chaskifood.app.feature.business.presentation.RegisterBusinessScreen
import com.chaskifood.app.feature.business.presentation.StoreOperationsScreen
import com.chaskifood.app.feature.cart.presentation.YourOrderScreen
import com.chaskifood.app.feature.cart.presentation.CartScreen
import com.chaskifood.app.feature.cart.presentation.CartViewModel
import com.chaskifood.app.feature.cart.presentation.CartConfirmationHost
import com.chaskifood.app.feature.catalog.presentation.ManageCatalogScreen
import com.chaskifood.app.feature.catalog.presentation.StoreProductConfigurationScreen
import com.chaskifood.app.feature.checkout.presentation.AddCardScreen
import com.chaskifood.app.feature.checkout.presentation.CheckoutScreen
import com.chaskifood.app.feature.checkout.presentation.RealCheckoutScreen
import com.chaskifood.app.feature.checkout.presentation.RealOrderConfirmedScreen
import com.chaskifood.app.feature.checkout.presentation.PaymentScreen
import com.chaskifood.app.feature.main.presentation.MainScreen
import com.chaskifood.app.feature.profile.presentation.ProfileSettingsScreen
import com.chaskifood.app.feature.menu.presentation.MenuToppingScreen
import com.chaskifood.app.feature.onboarding.presentation.OnboardingScreen
import com.chaskifood.app.feature.orders.presentation.OrderDeliveredScreen
import com.chaskifood.app.feature.orders.presentation.OrderPlacedScreen
import com.chaskifood.app.feature.orders.presentation.OrderRatingScreen
import com.chaskifood.app.feature.orders.presentation.OrderTrackProgressScreen
import com.chaskifood.app.feature.orders.presentation.OrderTrackingScreen
import com.chaskifood.app.feature.profile.presentation.AddLocationScreen
import com.chaskifood.app.feature.profile.presentation.LinkSocialAccountsScreen
import com.chaskifood.app.feature.profile.presentation.LocationPickerScreen
import com.chaskifood.app.feature.profile.presentation.PaymentMethodsScreen
import com.chaskifood.app.feature.profile.presentation.ReferFriendScreen
import com.chaskifood.app.feature.restaurants.presentation.RestaurantsScreen
import com.chaskifood.app.feature.search.presentation.FoodsPerCategoryScreen
import com.chaskifood.app.feature.search.presentation.FilterScreen
import com.chaskifood.app.feature.search.presentation.ProductDetailScreen
import com.chaskifood.app.feature.search.presentation.StoreOfferScreen
import com.chaskifood.app.feature.splash.presentation.SplashScreen
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab

@Composable
fun ChaskiNavHost() {
    val navController = rememberNavController()
    val onTabSelected: (FlowTab) -> Unit = { navController.openMainTab(it) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (backStackEntry?.destination?.route in demoDestinations) DemoNotice()
        Box(Modifier.weight(1f)) {
        NavHost(
            navController = navController,
            startDestination = ChaskiDestinations.SPLASH,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ChaskiDestinations.SPLASH) {
                SplashScreen(
                    onNavigateToMain = {
                        navController.navigate(ChaskiDestinations.MAIN) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(ChaskiDestinations.ONBOARDING) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(ChaskiDestinations.ONBOARDING) {
                OnboardingScreen(
                    onGetStarted = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(ChaskiDestinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(ChaskiDestinations.SIGN_IN) {
                SignInScreen(
                    onSignIn = { hasPhone ->
                        if (hasPhone) {
                            navController.navigate(ChaskiDestinations.MAIN) {
                                popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                            }
                        } else {
                            navController.navigate(ChaskiDestinations.SIGN_UP_PHONE)
                        }
                    },
                    onForgotPassword = {
                        navController.navigate(ChaskiDestinations.FORGOT_PASSWORD)
                    },
                    onGoSignUp = {
                        navController.navigate(ChaskiDestinations.SIGN_UP)
                    },
                )
            }
            composable(ChaskiDestinations.SIGN_UP) {
                SignUpScreen(
                    onSignUp = { hasPhone ->
                        if (hasPhone) {
                            navController.navigate(ChaskiDestinations.MAIN) {
                                popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                            }
                        } else {
                            navController.navigate(ChaskiDestinations.SIGN_UP_PHONE)
                        }
                    },
                    onGoSignIn = {
                        navController.popBackStack()
                    },
                )
            }
            composable(ChaskiDestinations.SIGN_UP_PHONE) {
                val phoneViewModel: PhoneAuthViewModel = hiltViewModel()
                SignUpPhoneScreen(
                    viewModel = phoneViewModel,
                    onGetCode = { verificationId ->
                        if (verificationId.isBlank()) {
                            navController.navigate(ChaskiDestinations.MAIN) { popUpTo(0) { inclusive = true } }
                        } else {
                            val encodedId = Uri.encode(verificationId)
                            navController.navigate("verify_phone/$encodedId")
                        }
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = ChaskiDestinations.VERIFY_PHONE,
                arguments = listOf(navArgument("verificationId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val verificationId = backStackEntry.arguments?.getString("verificationId") ?: ""
                val phoneEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(ChaskiDestinations.SIGN_UP_PHONE)
                }
                val phoneViewModel: PhoneAuthViewModel = hiltViewModel(phoneEntry)
                LaunchedEffect(verificationId) {
                    if (phoneViewModel.verificationId.value == null) phoneViewModel.verificationId.value = verificationId
                }
                VerifyPhoneScreen(
                    viewModel = phoneViewModel,
                    onVerify = {
                        navController.navigate(ChaskiDestinations.MAIN) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                    onBack = {
                        phoneViewModel.clearFeedback()
                        navController.popBackStack()
                    },
                )
            }
            composable(ChaskiDestinations.FORGOT_PASSWORD) {
                ForgotPasswordScreen(
                    onResetSent = { email ->
                        val encodedEmail = Uri.encode(email)
                        navController.navigate("check_email/$encodedEmail") {
                            popUpTo(ChaskiDestinations.FORGOT_PASSWORD) { inclusive = true }
                        }
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = ChaskiDestinations.CHECK_EMAIL,
                arguments = listOf(navArgument("email") { type = NavType.StringType }),
            ) { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email") ?: ""
                CheckEmailScreen(
                    email = email,
                    onResend = { },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(ChaskiDestinations.LOCATION) {
                LocationScreen(
                    onUseCurrentLocation = {
                        navController.navigate(ChaskiDestinations.MAIN) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                    onEnterNewAddress = {
                        navController.navigate(ChaskiDestinations.LOCATION_SEARCH)
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(ChaskiDestinations.LOCATION_SEARCH) {
                LocationSearchScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(ChaskiDestinations.MAIN) { mainEntry ->
                val requestedRoute by mainEntry.savedStateHandle
                    .getStateFlow<String?>(MAIN_TAB_REQUEST, null).collectAsStateWithLifecycle()
                val addressViewModel: AddressViewModel = hiltViewModel()
                val addressState by addressViewModel.uiState.collectAsStateWithLifecycle()
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    requestedTab = ChaskiTab.entries.find { it.route == requestedRoute },
                    onTabRequestHandled = { mainEntry.savedStateHandle[MAIN_TAB_REQUEST] = null },
                    deliveryAddress = addressState.book?.let { book ->
                        book.addresses.find { it.id == book.selectedAddressId }?.addressText
                    },
                    deliveryAddressUnavailable = addressState.book == null,
                    deliveryNotice = addressState.deliveryNotice,
                    onDismissDeliveryNotice = { addressViewModel.onAction(AddressAction.DismissMessage) },
                    onSeeAllRestaurants = {
                        navController.navigate(ChaskiDestinations.RESTAURANTS)
                    },
                    cartUnits = cartState.totalUnits,
                    onOpenCart = {
                        navController.navigate(ChaskiDestinations.CART) { launchSingleTop = true }
                    },
                    onRestaurantClick = { storeId ->
                        navController.navigate(
                            ChaskiDestinations.RESTAURANT_DETAIL.replace("{storeId}", Uri.encode(storeId)),
                        )
                    },
                    onProductClick = { storeId, productId ->
                        navController.navigate(
                            ChaskiDestinations.PRODUCT_DETAIL
                                .replace("{storeId}", Uri.encode(storeId))
                                .replace("{productId}", Uri.encode(productId)),
                        )
                    },
                    onOpenCategories = {
                        navController.navigate(ChaskiDestinations.SEARCH_CATEGORIES)
                    },
                    onOpenFood = {
                        navController.navigate(ChaskiDestinations.MENU_TOPPING)
                    },
                    onTrack = {
                        navController.navigate(ChaskiDestinations.ORDER_TRACKING)
                    },
                    onDelivered = {
                        navController.navigate(ChaskiDestinations.ORDER_DELIVERED)
                    },
                    onOpenPaymentMethods = {
                        navController.navigate(ChaskiDestinations.PROFILE_PAYMENTS)
                    },
                    onOpenLocations = {
                        navController.navigate(ChaskiDestinations.ADDRESS_BOOK) { launchSingleTop = true }
                    },
                    onOpenReferral = {
                        navController.navigate(ChaskiDestinations.REFER_FRIEND)
                    },
                    onOpenProfileInfo = {
                        navController.navigate(ChaskiDestinations.PROFILE_INFO)
                    },
                    onOpenSecurity = {
                        navController.navigate(ChaskiDestinations.FORGOT_PASSWORD)
                    },
                    onOpenSocialAccounts = {
                        navController.navigate(ChaskiDestinations.SOCIAL_ACCOUNTS)
                    },
                    onOpenBusiness = {
                        navController.navigate(ChaskiDestinations.BUSINESS_STATUS)
                    },
                    onOpenStoreOperations = {
                        navController.navigate(ChaskiDestinations.STORE_OPERATIONS)
                    },
                    onOpenManageCatalog = {
                        navController.navigate(ChaskiDestinations.MANAGE_CATALOG)
                    },
                    onOpenStoreProductConfiguration = {
                        navController.navigate(ChaskiDestinations.STORE_PRODUCT_CONFIGURATION)
                    },
                    onOpenAdminReview = {
                        navController.navigate(ChaskiDestinations.ADMIN_BUSINESS_REVIEW)
                    },
                    onOpenAdminSupervision = {
                        navController.navigate(ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION)
                    },
                    onOpenAdminAuditLog = {
                        navController.navigate(ChaskiDestinations.ADMIN_AUDIT_LOG)
                    },
                    onSignOut = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(ChaskiDestinations.ADDRESS_BOOK) { entry ->
                // Una sola observación para Inicio y gestión, con vida del destino Main.
                val mainEntry = remember(entry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
                val viewModel: AddressViewModel = hiltViewModel(mainEntry)
                AddressRoute(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSignIn = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = ChaskiDestinations.REGISTER_BUSINESS + "?resubmit={resubmit}",
                arguments = listOf(navArgument("resubmit") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                RegisterBusinessScreen(
                    isResubmission = entry.arguments?.getBoolean("resubmit") == true,
                    onSubmitted = {
                        navController.navigate(ChaskiDestinations.BUSINESS_STATUS) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.BUSINESS_STATUS) {
                BusinessStatusScreen(
                    onRegisterNew = {
                        navController.navigate(ChaskiDestinations.REGISTER_BUSINESS)
                    },
                    onEditAndResubmit = {
                        navController.navigate(ChaskiDestinations.REGISTER_BUSINESS + "?resubmit=true")
                    },
                    onManageStores = {
                        navController.navigate(ChaskiDestinations.MANAGE_STORES)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.MANAGE_STORES) {
                ManageStoresScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.STORE_OPERATIONS) {
                StoreOperationsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.MANAGE_CATALOG) {
                ManageCatalogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.STORE_PRODUCT_CONFIGURATION) {
                StoreProductConfigurationScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.ADMIN_BUSINESS_REVIEW) {
                AdminBusinessReviewScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION) {
                AdminBusinessSupervisionScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.ADMIN_AUDIT_LOG) {
                AdminAuditLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.PROFILE_INFO) {
                Scaffold(
                    containerColor = Color.White,
                    bottomBar = {
                        ChaskiFlowBottomBar(
                            selected = FlowTab.Account,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    ProfileSettingsScreen(
                        onSave = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                        onSignOut = {
                            navController.navigate(ChaskiDestinations.SIGN_IN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.SEARCH_CATEGORIES) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Home,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    com.chaskifood.app.feature.search.presentation.SearchCategoriesScreen(
                        onBack = { navController.popBackStack() },
                        onCategoryClick = { name ->
                            navController.navigate(
                                ChaskiDestinations.FOODS_PER_CATEGORY.replace(
                                    "{category}",
                                    Uri.encode(name),
                                ),
                            )
                        },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.RESTAURANTS) {
                RestaurantsScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onOpenLocations = {
                        navController.navigate(ChaskiDestinations.ADDRESS_BOOK) { launchSingleTop = true }
                    },
                    onRestaurantClick = { storeId ->
                        navController.navigate(
                            ChaskiDestinations.RESTAURANT_DETAIL.replace("{storeId}", Uri.encode(storeId)),
                        )
                    },
                )
            }
            composable(
                route = ChaskiDestinations.RESTAURANT_DETAIL,
                arguments = listOf(navArgument("storeId") { type = NavType.StringType }),
            ) {
                StoreOfferScreen(
                    onBack = { navController.popBackStack() },
                    onProductClick = { storeId, productId ->
                        navController.navigate(
                            ChaskiDestinations.PRODUCT_DETAIL
                                .replace("{storeId}", Uri.encode(storeId))
                                .replace("{productId}", Uri.encode(productId)),
                        )
                    },
                )
            }
            composable(
                route = ChaskiDestinations.PRODUCT_DETAIL,
                arguments = listOf(
                    navArgument("storeId") { type = NavType.StringType },
                    navArgument("productId") { type = NavType.StringType },
                ),
            ) { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
                ProductDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCart = {
                        navController.navigate(ChaskiDestinations.CART) { launchSingleTop = true }
                    },
                    onSignIn = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSelectAddress = {
                        navController.navigate(ChaskiDestinations.ADDRESS_BOOK) { launchSingleTop = true }
                    },
                    cartState = cartState,
                    onAddToCart = cartViewModel::addProduct,
                    onDismissCartMessage = cartViewModel::dismissMessage,
                )
            }
            composable(ChaskiDestinations.CART) { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                CartScreen(
                    viewModel = cartViewModel,
                    onBack = { navController.popBackStack() },
                    onExploreProducts = { navController.openMainTab(FlowTab.Search) },
                    onSignIn = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSelectAddress = {
                        navController.navigate(ChaskiDestinations.ADDRESS_BOOK) { launchSingleTop = true }
                    },
                    onCheckout = { navController.navigate(ChaskiDestinations.CHECKOUT) },
                )
            }
            composable(
                route = ChaskiDestinations.FOODS_PER_CATEGORY,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category") ?: "Africana"
                FoodsPerCategoryScreen(
                    categoryName = category,
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onFoodClick = {
                        navController.navigate(ChaskiDestinations.MENU_TOPPING)
                    },
                )
            }
            composable(ChaskiDestinations.FILTER) {
                FilterScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onShowResults = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.MENU_TOPPING) {
                MenuToppingScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onAddToOrder = {
                        navController.navigate(ChaskiDestinations.YOUR_ORDER)
                    },
                )
            }
            composable(ChaskiDestinations.YOUR_ORDER) {
                YourOrderScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onCheckout = {
                        navController.navigate(ChaskiDestinations.CHECKOUT)
                    },
                    onAddMoreItems = {
                        navController.popBackStack(ChaskiDestinations.RESTAURANTS, inclusive = false)
                    },
                )
            }
            composable(ChaskiDestinations.CHECKOUT) {
                RealCheckoutScreen(
                    onBack = { navController.popBackStack() },
                    onSelectAddress = { navController.navigate(ChaskiDestinations.ADDRESS_BOOK) },
                    onCreated = { orderId ->
                        navController.navigate("order_confirmed/$orderId") {
                            popUpTo(ChaskiDestinations.CART) { inclusive = true }
                        }
                    },
                )
            }
            composable(ChaskiDestinations.ORDER_CONFIRMED,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { entry ->
                RealOrderConfirmedScreen(
                    orderId = entry.arguments?.getString("orderId").orEmpty(),
                    onDone = { navController.openMainTab(FlowTab.Home) },
                )
            }
            composable(ChaskiDestinations.PAYMENT) {
                PaymentScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onCompletePurchase = {
                        navController.navigate(ChaskiDestinations.ORDER_PLACED)
                    },
                    onAddCard = {
                        navController.navigate(ChaskiDestinations.ADD_CARD)
                    },
                )
            }
            composable(ChaskiDestinations.ADD_CARD) {
                AddCardScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                )
            }
            composable(ChaskiDestinations.ORDER_PLACED) {
                OrderPlacedScreen(
                    onTabSelected = onTabSelected,
                    onTrackOrder = {
                        navController.navigate(ChaskiDestinations.ORDER_TRACKING) {
                            popUpTo(ChaskiDestinations.ORDER_PLACED) { inclusive = true }
                        }
                    },
                    onAddMoreOrders = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                )
            }
            composable(ChaskiDestinations.ORDER_TRACKING) {
                OrderTrackingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProgress = {
                        navController.navigate(ChaskiDestinations.ORDER_PROGRESS)
                    },
                )
            }
            composable(ChaskiDestinations.ORDER_PROGRESS) {
                OrderTrackProgressScreen(
                    onNext = {
                        navController.navigate(ChaskiDestinations.ORDER_RATING)
                    },
                )
            }
            composable(ChaskiDestinations.ORDER_DELIVERED) {
                OrderDeliveredScreen(
                    onNext = {
                        navController.navigate(ChaskiDestinations.ORDER_RATING)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.ORDER_RATING) {
                OrderRatingScreen(
                    onNext = {
                        navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChaskiDestinations.PROFILE_PAYMENTS) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Account,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    PaymentMethodsScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.REFER_FRIEND) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Account,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    ReferFriendScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.SOCIAL_ACCOUNTS) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Account,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    LinkSocialAccountsScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.ADD_LOCATION) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Home,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    AddLocationScreen(
                        onUseCurrent = {
                            navController.navigate(ChaskiDestinations.LOCATIONS)
                        },
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
            composable(ChaskiDestinations.LOCATIONS) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                            selected = com.chaskifood.app.ui.components.FlowTab.Home,
                            onTabClick = onTabSelected,
                        )
                    },
                ) { innerPadding ->
                    LocationPickerScreen(
                        onConfirm = {
                            navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false)
                        },
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
        if (backStackEntry?.destination?.route == ChaskiDestinations.PRODUCT_DETAIL ||
            backStackEntry?.destination?.route == ChaskiDestinations.CART) {
            val mainEntry = remember(backStackEntry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
            val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
            CartConfirmationHost(cartState, cartViewModel::confirmStoreChange, cartViewModel::cancelStoreChange)
        }
        }
    }
}

// These destinations currently display local sample data, without a purchase/order backend.
private val demoDestinations = setOf(
    ChaskiDestinations.SEARCH_CATEGORIES,
    ChaskiDestinations.FOODS_PER_CATEGORY,
    ChaskiDestinations.FILTER, ChaskiDestinations.MENU_TOPPING,
    ChaskiDestinations.YOUR_ORDER, ChaskiDestinations.CHECKOUT, ChaskiDestinations.PAYMENT,
    ChaskiDestinations.ORDER_PLACED, ChaskiDestinations.ORDER_TRACKING, ChaskiDestinations.ORDER_PROGRESS,
    ChaskiDestinations.ORDER_DELIVERED, ChaskiDestinations.ORDER_RATING,
)
