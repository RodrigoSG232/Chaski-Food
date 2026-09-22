package com.chaskifood.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                    navController.navigate(ChaskiDestinations.MAIN) {
                        popUpTo(ChaskiDestinations.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(ChaskiDestinations.MAIN) {
            MainScreen()
        }
    }
}