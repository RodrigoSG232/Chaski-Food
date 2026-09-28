package com.chaskifood.app.core.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chaskifood.app.feature.auth.presentation.CheckEmailScreen
import com.chaskifood.app.feature.auth.presentation.ForgotPasswordScreen
import com.chaskifood.app.feature.auth.presentation.LocationScreen
import com.chaskifood.app.feature.auth.presentation.LocationSearchScreen
import com.chaskifood.app.feature.auth.presentation.PhoneAuthViewModel
import com.chaskifood.app.feature.auth.presentation.SignInScreen
import com.chaskifood.app.feature.auth.presentation.SignUpPhoneScreen
import com.chaskifood.app.feature.auth.presentation.SignUpScreen
import com.chaskifood.app.feature.auth.presentation.VerifyPhoneScreen
import com.chaskifood.app.feature.business.presentation.AdminBusinessReviewScreen
import com.chaskifood.app.feature.business.presentation.BusinessStatusScreen
import com.chaskifood.app.feature.business.presentation.RegisterBusinessScreen
import com.chaskifood.app.feature.cart.presentation.YourOrderScreen
import com.chaskifood.app.feature.checkout.presentation.AddCardScreen
import com.chaskifood.app.feature.checkout.presentation.CheckoutScreen
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
import com.chaskifood.app.feature.restaurants.presentation.RestaurantDetailScreen
import com.chaskifood.app.feature.restaurants.presentation.RestaurantsScreen
import com.chaskifood.app.feature.search.presentation.FoodsPerCategoryScreen
import com.chaskifood.app.feature.search.presentation.FilterScreen
import com.chaskifood.app.feature.splash.presentation.SplashScreen
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab

@Composable
fun ChaskiNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ChaskiDestinations.SPLASH,
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
                    val encodedId = Uri.encode(verificationId)
                    navController.navigate("verify_phone/$encodedId")
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
            val phoneViewModel: PhoneAuthViewModel = hiltViewModel()
            LaunchedEffect(verificationId) {
                phoneViewModel.verificationId.value = verificationId
            }
            VerifyPhoneScreen(
                viewModel = phoneViewModel,
                onVerify = {
                    navController.navigate(ChaskiDestinations.MAIN) {
                        popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
                    }
                },
                onBack = {
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
        composable(ChaskiDestinations.MAIN) {
            MainScreen(
                onSeeAllRestaurants = {
                    navController.navigate(ChaskiDestinations.RESTAURANTS)
                },
                onOpenYourOrder = {
                    navController.navigate(ChaskiDestinations.YOUR_ORDER)
                },
                onRestaurantClick = {
                    navController.navigate(ChaskiDestinations.RESTAURANT_DETAIL)
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
                    navController.navigate(ChaskiDestinations.ADD_LOCATION)
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
                onOpenAdminReview = {
                    navController.navigate(ChaskiDestinations.ADMIN_BUSINESS_REVIEW)
                },
                onSignOut = {
                    navController.navigate(ChaskiDestinations.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(ChaskiDestinations.REGISTER_BUSINESS) {
            RegisterBusinessScreen(
                onSubmitted = {
                    navController.navigate(ChaskiDestinations.BUSINESS_STATUS) {
                        popUpTo(ChaskiDestinations.REGISTER_BUSINESS) { inclusive = true }
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
                    navController.navigate(ChaskiDestinations.REGISTER_BUSINESS)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ChaskiDestinations.ADMIN_BUSINESS_REVIEW) {
            AdminBusinessReviewScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(ChaskiDestinations.PROFILE_INFO) {
            Scaffold(
                containerColor = Color.White,
                bottomBar = {
                    ChaskiFlowBottomBar(
                        selected = FlowTab.Account,
                        onTabClick = { tab ->
                            if (tab == FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
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
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.SEARCH_CATEGORIES) {
            androidx.compose.material3.Scaffold(
                containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                bottomBar = {
                    com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                        selected = com.chaskifood.app.ui.components.FlowTab.Home,
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
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
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.RESTAURANTS) {
            RestaurantsScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onRestaurantClick = {
                    navController.navigate(ChaskiDestinations.RESTAURANT_DETAIL)
                },
            )
        }
        composable(ChaskiDestinations.RESTAURANT_DETAIL) {
            RestaurantDetailScreen(
                restaurantName = "Garden Dining Room",
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onFoodClick = {
                    navController.navigate(ChaskiDestinations.MENU_TOPPING)
                },
                onOpenFilter = {
                    navController.navigate(ChaskiDestinations.FILTER)
                },
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
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onFoodClick = {
                    navController.navigate(ChaskiDestinations.MENU_TOPPING)
                },
            )
        }
        composable(ChaskiDestinations.FILTER) {
            FilterScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onShowResults = { navController.popBackStack() },
            )
        }
        composable(ChaskiDestinations.MENU_TOPPING) {
            MenuToppingScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onAddToOrder = {
                    navController.navigate(ChaskiDestinations.YOUR_ORDER)
                },
            )
        }
        composable(ChaskiDestinations.YOUR_ORDER) {
            YourOrderScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onCheckout = {
                    navController.navigate(ChaskiDestinations.CHECKOUT)
                },
                onAddMoreItems = {
                    navController.popBackStack(ChaskiDestinations.RESTAURANTS, inclusive = false)
                },
            )
        }
        composable(ChaskiDestinations.CHECKOUT) {
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onContinueToPayment = {
                    navController.navigate(ChaskiDestinations.PAYMENT)
                },
            )
        }
        composable(ChaskiDestinations.PAYMENT) {
            PaymentScreen(
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
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
                onHome = { navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false) },
                onAddCard = {
                    navController.popBackStack()
                },
            )
        }
        composable(ChaskiDestinations.ORDER_PLACED) {
            OrderPlacedScreen(
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
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                PaymentMethodsScreen(
                    onNext = {
                        navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false)
                    },
                    onAddCard = {
                        navController.navigate(ChaskiDestinations.ADD_CARD)
                    },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.REFER_FRIEND) {
            androidx.compose.material3.Scaffold(
                containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                bottomBar = {
                    com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                        selected = com.chaskifood.app.ui.components.FlowTab.Account,
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                ReferFriendScreen(
                    onBack = { navController.popBackStack() },
                    onMore = { navController.navigate(ChaskiDestinations.SOCIAL_ACCOUNTS) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.SOCIAL_ACCOUNTS) {
            androidx.compose.material3.Scaffold(
                containerColor = com.chaskifood.app.ui.theme.ChaskiBackground,
                bottomBar = {
                    com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                        selected = com.chaskifood.app.ui.components.FlowTab.Account,
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                LinkSocialAccountsScreen(
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.ADD_LOCATION) {
            androidx.compose.material3.Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.White,
                bottomBar = {
                    com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                        selected = com.chaskifood.app.ui.components.FlowTab.Home,
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                AddLocationScreen(
                    onUseCurrent = {
                        navController.navigate(ChaskiDestinations.LOCATIONS)
                    },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(ChaskiDestinations.LOCATIONS) {
            androidx.compose.material3.Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.White,
                bottomBar = {
                    com.chaskifood.app.ui.components.ChaskiFlowBottomBar(
                        selected = com.chaskifood.app.ui.components.FlowTab.Home,
                        onTabClick = { tab ->
                            if (tab == com.chaskifood.app.ui.components.FlowTab.Home) {
                                navController.popBackStack(
                                    ChaskiDestinations.MAIN,
                                    inclusive = false,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                LocationPickerScreen(
                    onConfirm = {
                        navController.popBackStack(ChaskiDestinations.MAIN, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}