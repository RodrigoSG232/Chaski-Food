package com.chaskifood.app.core.navigation

import com.chaskifood.app.feature.orders.presentation.StoreOrdersScreen
import com.chaskifood.app.feature.orders.presentation.BuyerOrderDetailScreen
import com.chaskifood.app.feature.orders.presentation.MyOrdersScreen

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
import com.chaskifood.app.feature.checkout.presentation.RealCheckoutScreen
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
fun ChaskiNavHost(
    navController: androidx.navigation.NavHostController = rememberNavController(),
    startDestination: String = ChaskiDestinations.SPLASH,
) {
    val access: AccessNavigationViewModel = hiltViewModel()
    AccessNavigationEvents(access, navController)
    val onTabSelected: (FlowTab) -> Unit = { navController.openMainTab(it) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val accessState by access.states.collectAsStateWithLifecycle()
    val noticeModel:com.chaskifood.app.core.notifications.NotificationNavigationViewModel=hiltViewModel()
    val pendingNotice by noticeModel.notifications.pending.collectAsStateWithLifecycle()
    val permissionContext=androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(accessState,pendingNotice) {
        if(accessState.phase==com.chaskifood.app.core.access.data.AccessContextPhase.RESOLVED){
            val notice=noticeModel.notifications.takeAuthorized()
            if(notice!=null){
                val route=ChaskiDestinations.STORE_ORDERS+"/${Uri.encode(notice.businessId)}/${Uri.encode(notice.storeId)}"
                access.runIfAllowedFrom(access.current().origin,route,notice.businessId,notice.storeId){
                    navController.navigate(route){launchSingleTop=true}
                    navController.currentBackStackEntry?.savedStateHandle?.set("notificationRefresh",java.util.UUID.randomUUID().toString())
                }
            }
            val resolved=accessState.resolution as? com.chaskifood.app.core.access.domain.AccessResolution.Resolved
            if(android.os.Build.VERSION.SDK_INT>=33 && access.enabled && resolved?.capabilities?.grants?.any {
                it.capability==com.chaskifood.app.core.access.domain.AccessCapability.READ_STORE_ORDERS}==true){
                val prefs=permissionContext.getSharedPreferences("notification_permission",android.content.Context.MODE_PRIVATE)
                if(!prefs.getBoolean("asked",false)){
                    prefs.edit().putBoolean("asked",true).apply()
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    if (access.enabled) {
        val main = runCatching { navController.getBackStackEntry(ChaskiDestinations.MAIN) }.getOrNull()
        if (main != null) {
            val cart: CartViewModel = hiltViewModel(main)
            val cartAccessState by cart.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(accessState, cartAccessState.confirmation?.token) {
                if (!access.allows(ChaskiDestinations.CART)) cartAccessState.confirmation?.let { cart.cancelStoreChange(it.token) }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (backStackEntry?.destination?.route in demoDestinations) DemoNotice()
        Box(Modifier.weight(1f)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            protectedComposable(access, navController, ChaskiDestinations.SPLASH) {
                SplashScreen(
                    onNavigateToMain = {
                        if(navController.currentDestination?.route==ChaskiDestinations.SPLASH)navController.navigate(ChaskiDestinations.MAIN) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        if(navController.currentDestination?.route==ChaskiDestinations.SPLASH)navController.navigate(ChaskiDestinations.ONBOARDING) {
                            popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ONBOARDING) {
                OnboardingScreen(
                    onGetStarted = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(ChaskiDestinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.SIGN_IN) {
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
            protectedComposable(access, navController, ChaskiDestinations.SIGN_UP) {
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
            protectedComposable(access, navController, ChaskiDestinations.SIGN_UP_PHONE) {
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
            protectedComposable(access, navController,
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
            protectedComposable(access, navController, ChaskiDestinations.FORGOT_PASSWORD) {
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
            protectedComposable(access, navController,
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
            protectedComposable(access, navController, ChaskiDestinations.LOCATION) {
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
            protectedComposable(access, navController, ChaskiDestinations.LOCATION_SEARCH) {
                LocationSearchScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.MAIN) { mainEntry ->
                val requestedRoute by mainEntry.savedStateHandle
                    .getStateFlow<String?>(MAIN_TAB_REQUEST, null).collectAsStateWithLifecycle()
                val addressViewModel: AddressViewModel = hiltViewModel()
                val addressState by addressViewModel.uiState.collectAsStateWithLifecycle()
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    onRecoverPurchase = { navController.navigate(ChaskiDestinations.CHECKOUT_RECOVERY) { launchSingleTop = true } },
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
                    onOpenOrder = { orderId -> navController.navigateToOrderDetail(orderId) },
                    onOpenMyPurchases = { navController.navigate(ChaskiDestinations.MY_ORDERS) { launchSingleTop = true } },
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
                    onOpenManageStores = { navController.navigate(ChaskiDestinations.MANAGE_STORES) },
                    onOpenStoreOrders = { business, store ->
                        navController.navigate(ChaskiDestinations.STORE_ORDERS + "/${Uri.encode(business)}/${Uri.encode(store)}") { launchSingleTop = true }
                    },
                    onOpenSelectedHours = { _, store ->
                        navController.navigate(ChaskiDestinations.STORE_OPERATIONS + (store?.let { "?storeId=${Uri.encode(it)}" } ?: ""))
                    },
                    onOpenSelectedOffers = { _, store ->
                        navController.navigate(ChaskiDestinations.STORE_PRODUCT_CONFIGURATION + (store?.let { "?storeId=${Uri.encode(it)}" } ?: ""))
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
            protectedComposable(access, navController, ChaskiDestinations.ADDRESS_BOOK) { entry ->
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
            protectedComposable(access, navController,
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
            protectedComposable(access, navController, ChaskiDestinations.BUSINESS_STATUS) {
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
            protectedComposable(access, navController, ChaskiDestinations.MANAGE_STORES) {
                ManageStoresScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.STORE_OPERATIONS + "?storeId={storeId}",
                arguments = listOf(navArgument("storeId") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                StoreOperationsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.MANAGE_CATALOG) {
                ManageCatalogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION + "?storeId={storeId}",
                arguments = listOf(navArgument("storeId") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                StoreProductConfigurationScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ADMIN_BUSINESS_REVIEW) {
                AdminBusinessReviewScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION) {
                AdminBusinessSupervisionScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ADMIN_AUDIT_LOG) {
                AdminAuditLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.PROFILE_INFO) {
                Scaffold(
                    containerColor = Color.White,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.SEARCH_CATEGORIES) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.RESTAURANTS) {
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
            protectedComposable(access, navController,
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
            protectedComposable(access, navController,
                route = ChaskiDestinations.PRODUCT_DETAIL,
                arguments = listOf(
                    navArgument("storeId") { type = NavType.StringType },
                    navArgument("productId") { type = NavType.StringType },
                ),
            ) { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
                val authorize = LocalProtectedAction.current
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
                    onAddToCart = { storeId, productId, quantity -> authorize { cartViewModel.addProduct(storeId, productId, quantity) } },
                    onDismissCartMessage = cartViewModel::dismissMessage,
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.CART) { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
                val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
                CartScreen(
                    viewModel = cartViewModel,
                    onBack = { navController.popBackStack() },
                    onExploreProducts = { navController.openMainTab(FlowTab.Search) },
                    onCheckout = { navController.navigate(ChaskiDestinations.CHECKOUT) { launchSingleTop = true } },
                    onSignIn = {
                        navController.navigate(ChaskiDestinations.SIGN_IN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSelectAddress = {
                        navController.navigate(ChaskiDestinations.ADDRESS_BOOK) { launchSingleTop = true }
                    },
                )
            }
            protectedComposable(access, navController,
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
            protectedComposable(access, navController, ChaskiDestinations.FILTER) {
                FilterScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onShowResults = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.MENU_TOPPING) {
                MenuToppingScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                    onAddToOrder = {
                        navController.navigate(ChaskiDestinations.YOUR_ORDER)
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.YOUR_ORDER) {
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
            protectedComposable(access, navController, ChaskiDestinations.STORE_ORDERS + "/{businessId}/{storeId}",
                arguments = listOf(navArgument("businessId") { type = NavType.StringType }, navArgument("storeId") { type = NavType.StringType })) {
                val notificationRefresh by it.savedStateHandle.getStateFlow<String?>("notificationRefresh",null).collectAsStateWithLifecycle()
                StoreOrdersScreen(onBack = { navController.popBackStack() },notificationRefresh=notificationRefresh)
            }
            protectedComposable(access, navController, ChaskiDestinations.CHECKOUT_RECOVERY) {
                RealCheckoutScreen(onBack = { navController.popBackStack() },
                    onOpenCart = {}, onSelectAddress = {}, recoveryOnly = true,
                    onOpenOrder = { orderId -> navController.navigateToOrderDetail(orderId) })
            }
            // HU17: compras propias; el ID completo viaja en la ruta y la consulta, el abreviado solo se muestra.
            protectedComposable(access, navController, ChaskiDestinations.MY_ORDERS) {
                MyOrdersScreen(onBack = { navController.popBackStack() },
                    onOpenOrder = { orderId -> navController.navigateToOrderDetail(orderId) })
            }
            protectedComposable(access, navController, ChaskiDestinations.ORDER_DETAIL,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })) {
                BuyerOrderDetailScreen(onBack = { navController.popBackStack() })
            }
            protectedComposable(access, navController, ChaskiDestinations.CHECKOUT) {
                RealCheckoutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCart = {
                        if (!navController.popBackStack(ChaskiDestinations.CART, inclusive = false)) {
                            navController.navigate(ChaskiDestinations.CART)
                        }
                    },
                    onSelectAddress = { navController.navigate(ChaskiDestinations.ADDRESS_BOOK) },
                    onOpenOrder = { orderId -> navController.navigateToOrderDetail(orderId) },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.PAYMENT) {
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
            protectedComposable(access, navController, ChaskiDestinations.ADD_CARD) {
                AddCardScreen(
                    onBack = { navController.popBackStack() },
                    onTabSelected = onTabSelected,
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ORDER_PLACED) {
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
            protectedComposable(access, navController, ChaskiDestinations.ORDER_TRACKING) {
                OrderTrackingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProgress = {
                        navController.navigate(ChaskiDestinations.ORDER_PROGRESS)
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ORDER_PROGRESS) {
                OrderTrackProgressScreen(
                    onNext = {
                        navController.navigate(ChaskiDestinations.ORDER_RATING)
                    },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ORDER_DELIVERED) {
                OrderDeliveredScreen(
                    onNext = {
                        navController.navigate(ChaskiDestinations.ORDER_RATING)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.ORDER_RATING) {
                OrderRatingScreen(
                    onNext = {
                        navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            protectedComposable(access, navController, ChaskiDestinations.PROFILE_PAYMENTS) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.REFER_FRIEND) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.SOCIAL_ACCOUNTS) {
                androidx.compose.material3.Scaffold(
                    containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.ADD_LOCATION) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        AccessFlowBottomBar(
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
            protectedComposable(access, navController, ChaskiDestinations.LOCATIONS) {
                androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    bottomBar = {
                        AccessFlowBottomBar(
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
        if (access.allows(backStackEntry?.destination?.route) && (backStackEntry?.destination?.route == ChaskiDestinations.PRODUCT_DETAIL ||
            backStackEntry?.destination?.route == ChaskiDestinations.CART)) {
            val mainEntry = remember(backStackEntry) { navController.getBackStackEntry(ChaskiDestinations.MAIN) }
            val cartViewModel: CartViewModel = hiltViewModel(mainEntry)
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
            val expected = access.current().origin
            val frameMatches = !access.enabled || AccessNavigationPolicy.matchesSharedFrame(
                access.sessionStamp(expected), backStackEntry?.savedStateHandle?.get<String>("hu34_navigation_session"),
                mainEntry.savedStateHandle.get<String>("hu34_navigation_session"), expected.session?.uid, cartState.cart?.ownerUid)
            if (frameMatches) CartConfirmationHost(cartState,
                { token -> access.runIfAllowedFrom(expected, ChaskiDestinations.CART) { cartViewModel.confirmStoreChange(token) } },
                cartViewModel::cancelStoreChange)
        }
        }
    }
}

// These destinations currently display local sample data, without a purchase/order backend.
private val demoDestinations = setOf(
    ChaskiDestinations.SEARCH_CATEGORIES,
    ChaskiDestinations.FOODS_PER_CATEGORY,
    ChaskiDestinations.FILTER, ChaskiDestinations.MENU_TOPPING,
    ChaskiDestinations.YOUR_ORDER, ChaskiDestinations.PAYMENT,
    ChaskiDestinations.ORDER_PLACED, ChaskiDestinations.ORDER_TRACKING, ChaskiDestinations.ORDER_PROGRESS,
    ChaskiDestinations.ORDER_DELIVERED, ChaskiDestinations.ORDER_RATING,
)

/** Mantiene las opciones de cliente intactas y la navegación comercial también en destinos personales. */
@Composable
private fun AccessFlowBottomBar(selected: FlowTab, onTabClick: (FlowTab) -> Unit) {
    val access = LocalNavigationAccess.current
    val observed = access?.states?.collectAsStateWithLifecycle()?.value
    val resolved = access?.current()?.takeIf { it.origin == observed?.origin }?.resolution as? com.chaskifood.app.core.access.domain.AccessResolution.Resolved
    if (resolved != null && resolved.experience != com.chaskifood.app.core.access.domain.CommercialExperience.CLIENT) {
        val owner = resolved.experience == com.chaskifood.app.core.access.domain.CommercialExperience.OWNER
        val count = resolved.capabilities.grants.filter { it.capability == com.chaskifood.app.core.access.domain.AccessCapability.READ_STORE_ORDERS }
            .map { it.scope }.distinct().size
        com.chaskifood.app.feature.main.presentation.CommercialBottomNavContent(
            if (owner) "Mi negocio" else if (count > 1) "Mis locales" else "Mi local", selected == FlowTab.Account) {
            onTabClick(if (it == ChaskiTab.ACCOUNT) FlowTab.Account else FlowTab.Home)
        }
    } else ChaskiFlowBottomBar(selected, onTabClick)
}
/** Abre el detalle con el ID completo; un ID con formato inválido lo resuelve el ViewModel como «no encontrado». */
private fun androidx.navigation.NavHostController.navigateToOrderDetail(orderId: String) {
    navigate(ChaskiDestinations.ORDER_DETAIL.replace("{orderId}", Uri.encode(orderId))) { launchSingleTop = true }
}
