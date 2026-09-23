package com.chaskifood.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.feature.auth.presentation.CheckEmailScreen
import com.chaskifood.app.feature.auth.presentation.ForgotPasswordScreen
import com.chaskifood.app.feature.auth.presentation.LocationScreen
import com.chaskifood.app.feature.auth.presentation.LocationSearchScreen
import com.chaskifood.app.feature.auth.presentation.SignInScreen
import com.chaskifood.app.feature.auth.presentation.SignUpPhoneScreen
import com.chaskifood.app.feature.auth.presentation.SignUpScreen
import com.chaskifood.app.feature.auth.presentation.VerifyPhoneScreen
import com.chaskifood.app.feature.main.presentation.MainScreen
import com.chaskifood.app.feature.onboarding.presentation.OnboardingScreen
import com.chaskifood.app.feature.splash.presentation.SplashScreen

@Composable
fun ChaskiNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ChaskiDestinations.SPLASH,
    ) {
        composable(ChaskiDestinations.SPLASH) {
            SplashScreen(
                onFinished = {
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
                onSignIn = {
                    navController.navigate(ChaskiDestinations.MAIN) {
                        popUpTo(ChaskiDestinations.SPLASH) { inclusive = true }
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
                onSignUp = {
                    navController.navigate(ChaskiDestinations.SIGN_UP_PHONE)
                },
                onGoSignIn = {
                    navController.popBackStack()
                },
            )
        }
        composable(ChaskiDestinations.SIGN_UP_PHONE) {
            SignUpPhoneScreen(
                onGetCode = {
                    navController.navigate(ChaskiDestinations.VERIFY_PHONE)
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(ChaskiDestinations.VERIFY_PHONE) {
            VerifyPhoneScreen(
                onVerify = {
                    navController.navigate(ChaskiDestinations.LOCATION)
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(ChaskiDestinations.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onReset = {
                    navController.navigate(ChaskiDestinations.CHECK_EMAIL)
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(ChaskiDestinations.CHECK_EMAIL) {
            CheckEmailScreen(
                onResend = {
                    navController.navigate(ChaskiDestinations.CHECK_EMAIL) {
                        popUpTo(ChaskiDestinations.CHECK_EMAIL) { inclusive = true }
                    }
                },
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
            MainScreen()
        }
    }
}