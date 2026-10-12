package com.chaskifood.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.ui.components.ChaskiBottomNav
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun detailAndCartKeepMainScopeAndBackReturnsToTheOrigin() {
        lateinit var controller: NavHostController
        lateinit var mainModel: CartScopeProbe
        lateinit var detailModel: CartScopeProbe
        lateinit var cartModel: CartScopeProbe
        compose.setContent {
            controller = rememberNavController()
            ChaskiTheme {
                NavHost(controller, ChaskiDestinations.MAIN) {
                    composable(ChaskiDestinations.MAIN) { entry ->
                        mainModel = viewModel(viewModelStoreOwner = entry)
                        TextButton(onClick = {
                            controller.navigate("restaurant/store/product/product")
                        }) { Text("Abrir producto") }
                    }
                    composable(ChaskiDestinations.PRODUCT_DETAIL) { entry ->
                        val mainEntry = controller.getBackStackEntry(ChaskiDestinations.MAIN)
                        detailModel = viewModel(viewModelStoreOwner = mainEntry)
                        TextButton(onClick = { controller.navigate(ChaskiDestinations.CART) }) {
                            Text("Ver carrito")
                        }
                    }
                    composable(ChaskiDestinations.CART) { entry ->
                        val mainEntry = controller.getBackStackEntry(ChaskiDestinations.MAIN)
                        cartModel = viewModel(viewModelStoreOwner = mainEntry)
                        Column {
                            TextButton(onClick = { controller.popBackStack() }) { Text("Volver") }
                            TextButton(onClick = { controller.openMainTab(FlowTab.Search) }) {
                                Text("Seguir explorando")
                            }
                        }
                    }
                }
            }
        }
        val mainId = compose.runOnIdle { controller.currentBackStackEntry!!.id }
        compose.onNodeWithText("Abrir producto").performClick()
        compose.onNodeWithText("Ver carrito").performClick()
        compose.runOnIdle {
            assertTrue(mainModel === detailModel && detailModel === cartModel)
            assertEquals(ChaskiDestinations.CART, controller.currentDestination?.route)
        }
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { assertEquals(ChaskiDestinations.PRODUCT_DETAIL, controller.currentDestination?.route) }
        compose.onNodeWithText("Ver carrito").performClick()
        compose.onNodeWithText("Seguir explorando").performClick()
        compose.runOnIdle {
            assertEquals(mainId, controller.currentBackStackEntry?.id)
            assertEquals(ChaskiTab.SEARCH.route,
                controller.currentBackStackEntry?.savedStateHandle?.get<String>(MAIN_TAB_REQUEST))
        }
    }

    @Test
    fun secondaryTabsReturnToTheSameMainEntryWithTheRequestedDestination() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            ChaskiTheme {
                NavHost(controller, ChaskiDestinations.MAIN) {
                    composable(ChaskiDestinations.MAIN) { Text("Main fixture") }
                    composable("secondary") {
                        ChaskiFlowBottomBar(FlowTab.Account, onTabClick = controller::openMainTab)
                    }
                }
            }
        }
        val mainId = compose.runOnIdle { controller.currentBackStackEntry!!.id }
        val destinations = listOf(ChaskiTab.HOME, ChaskiTab.SEARCH, ChaskiTab.ORDERS, ChaskiTab.ACCOUNT)
        FlowTab.entries.zip(destinations).forEach { (tab, destination) ->
            compose.runOnIdle { controller.navigate("secondary") }
            compose.onNodeWithText(tab.label).performClick()
            compose.runOnIdle {
                val entry = controller.currentBackStackEntry!!
                assertEquals(mainId, entry.id)
                assertEquals(ChaskiDestinations.MAIN, entry.destination.route)
                assertEquals(destination.route, entry.savedStateHandle.get<String>(MAIN_TAB_REQUEST))
            }
        }
        compose.runOnIdle { assertTrue(controller.previousBackStackEntry == null) }
    }

    @Test
    fun mainTabsRestoreTheirStateAndRepeatedSelectionDoesNotDuplicateTheTab() {
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            ChaskiTheme {
                Scaffold(bottomBar = { ChaskiBottomNav(controller) }) { padding ->
                    NavHost(controller, ChaskiTab.HOME.route, modifier = Modifier.padding(padding)) {
                        ChaskiTab.entries.forEach { tab ->
                            composable(tab.route) { Text("Page: ${tab.label}") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(ChaskiTab.SEARCH.label).performClick()
        compose.runOnIdle { controller.currentBackStackEntry!!.savedStateHandle["query"] = "pollo" }
        compose.onNodeWithText(ChaskiTab.ACCOUNT.label).performClick()
        compose.onNodeWithText(ChaskiTab.SEARCH.label).performClick()
        compose.runOnIdle {
            assertEquals("pollo", controller.currentBackStackEntry!!.savedStateHandle.get<String>("query"))
        }
        compose.onNodeWithText(ChaskiTab.SEARCH.label).performClick()
        compose.runOnIdle {
            assertTrue(controller.popBackStack())
            assertEquals(ChaskiTab.HOME.route, controller.currentDestination!!.route)
        }
    }
}

class CartScopeProbe : ViewModel()
