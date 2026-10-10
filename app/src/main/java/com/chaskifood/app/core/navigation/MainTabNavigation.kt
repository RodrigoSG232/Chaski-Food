package com.chaskifood.app.core.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.chaskifood.app.ui.components.FlowTab

const val MAIN_TAB_REQUEST = "main_tab_request"

fun FlowTab.mainTab(): ChaskiTab = when (this) {
    FlowTab.Home -> ChaskiTab.HOME
    FlowTab.Search -> ChaskiTab.SEARCH
    FlowTab.Orders -> ChaskiTab.ORDERS
    FlowTab.Account -> ChaskiTab.ACCOUNT
}

/** Return to the existing Main entry; keep its tab histories and ViewModels. */
fun NavHostController.openMainTab(tab: FlowTab) {
    getBackStackEntry(ChaskiDestinations.MAIN).savedStateHandle[MAIN_TAB_REQUEST] = tab.mainTab().route
    popBackStack(ChaskiDestinations.MAIN, inclusive = false)
}

/** Same selection policy for the main bar and requests from secondary screens. */
fun NavHostController.selectMainTab(tab: ChaskiTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
