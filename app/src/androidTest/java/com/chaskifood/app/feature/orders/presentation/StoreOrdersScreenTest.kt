package com.chaskifood.app.feature.orders.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class StoreOrdersScreenTest {
    @get:Rule val compose=createComposeRule()
    private val target=StoreOrdersTarget("business","store")
    private val context=StoreOrdersContext("manager",1,AccessProvenance(AccessSession("manager",1),1,1),2,target)
    private fun order(id:String="1".repeat(64))=StoreOrderSummary(id,1800000000000,
        listOf(StoreOrderLine("p","Plato de prueba",2,1000,2000)),OrderAmounts(2000,300,2300))
    @Test fun loadingThenReadOnlyDetailsUseTheSameResponse() {
        val state=mutableStateOf(StoreOrdersUiState())
        compose.setContent { ChaskiTheme { StoreOrdersContent(state.value) } }
        compose.onNodeWithText("Consultando pedidos…").assertIsDisplayed()
        compose.runOnIdle {state.value=StoreOrdersUiState(context,loading=false,storeName="Local de prueba",orders=listOf(order()))}
        compose.onNodeWithText("Local de prueba").assertIsDisplayed()
        compose.onNodeWithText("2 × Plato de prueba").assertIsDisplayed()
        compose.onNodeWithText("Ver detalle").performScrollTo().performClick()
        compose.onNodeWithText("Efectivo al recibir · PEN").performScrollTo().assertIsDisplayed()
        for(action in listOf("Aceptar pedido","Rechazar pedido","Preparar","Cancelar pedido"))compose.onNodeWithText(action).assertDoesNotExist()
        compose.onNodeWithText("Teléfono del comprador").assertDoesNotExist()
    }
    @Test fun emptyErrorAndManualRefreshHaveConcreteCallbacks() {
        var refreshes=0
        val state=mutableStateOf(StoreOrdersUiState(context,loading=false))
        compose.setContent{ChaskiTheme{StoreOrdersContent(state.value,onRefresh={refreshes++})}}
        compose.onNodeWithText("No hay pedidos pendientes en este local.").assertIsDisplayed()
        compose.onNodeWithText("Actualizar bandeja").performClick()
        compose.runOnIdle {assertEquals(1,refreshes);state.value=state.value.copy(message="Red no disponible")}
        compose.onNodeWithText("Red no disponible").assertIsDisplayed()
        compose.onNodeWithText("No hay pedidos pendientes en este local.").assertDoesNotExist()
    }
    @Test fun moreResultsAndCollapsedDetailsDoNotAddBusinessActions() {
        var more=0
        val state=mutableStateOf(StoreOrdersUiState(context,loading=false,orders=listOf(order()),nextCursor="next"))
        compose.setContent{ChaskiTheme{StoreOrdersContent(state.value,onMore={more++})}}
        compose.onNodeWithText("Cargar más").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,more);state.value=state.value.copy(orders=state.value.orders+order("2".repeat(64)),nextCursor=null)}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pedido 22222222"))
        compose.onNodeWithText("Pedido 22222222").assertIsDisplayed();compose.onNodeWithText("Cargar más").assertDoesNotExist()
    }
    @Test fun permissionLossRemovesPreviouslyVisibleOrderDetails() {
        val state=mutableStateOf(StoreOrdersUiState(context,loading=false,orders=listOf(order())))
        compose.setContent{ChaskiTheme{StoreOrdersContent(state.value)}}
        compose.onNodeWithText("Pedido 11111111").assertIsDisplayed()
        compose.runOnIdle {state.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Asignación revocada")}
        compose.onNodeWithText("Asignación revocada").assertIsDisplayed()
        compose.onNodeWithText("Pedido 11111111").assertDoesNotExist();compose.onNodeWithText("Ver detalle").assertDoesNotExist()
    }
    @Test fun viewModelDrivenScreenLoadsAndClearsAfterRevocation() {
        val auth=MutableStateFlow<StoreOrdersAuthorization>(StoreOrdersAuthorization.Allowed(context))
        var reads=0
        val repo=object:StoreOrdersRepository {
            override fun observeAuthorization(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization> = auth
            override fun isCurrent(context:StoreOrdersContext)=auth.value==StoreOrdersAuthorization.Allowed(context)
            override suspend fun page(context:StoreOrdersContext,cursor:String?):StoreOrdersResult {
                reads++;return StoreOrdersResult.Loaded(StoreOrdersPage("store","Local",listOf(order()),null))
            }
        }
        val vm=StoreOrdersViewModel(repo,SavedStateHandle(mapOf("businessId" to "business","storeId" to "store")))
        compose.setContent{ChaskiTheme{StoreOrdersScreen({},vm)}}
        compose.onNodeWithText("Pedido 11111111").assertIsDisplayed()
        compose.runOnIdle {assertEquals(1,reads);auth.value=StoreOrdersAuthorization.Denied}
        compose.onNodeWithText("Tu acceso a este local ya no está autorizado.").assertIsDisplayed()
        compose.onNodeWithText("Pedido 11111111").assertDoesNotExist()
    }
}
