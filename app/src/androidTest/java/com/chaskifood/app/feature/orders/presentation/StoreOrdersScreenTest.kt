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
        for(action in listOf("Aceptar pedido","Rechazar pedido"))compose.onNodeWithText(action).assertIsDisplayed()
        for(action in listOf("Preparar","Cancelar pedido"))compose.onNodeWithText(action).assertDoesNotExist()
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
    @Test fun preparingQueueOffersAnExtensionDialogInsteadOfDecisionActions() {
        val preparing=StoreOrderSummary("3".repeat(64),1800000000000,listOf(StoreOrderLine("p","Plato",1,1000,1000)),
            OrderAmounts(1000,300,1300),OrderStatus.PREPARING,estimatedPreparationMinutes=25,estimatedReadyAtEpochMillis=1800001500000,
            estimateRevision=1,preparationExtendedMinutes=10)
        var extended:Pair<String,Int>?=null
        compose.setContent{ChaskiTheme{StoreOrdersContent(StoreOrdersUiState(context,loading=false,storeName="Local",
            orders=listOf(preparing),selectedStatus=OrderStatus.PREPARING),onExtend={id,minutes->extended=id to minutes})}}
        compose.onNodeWithText("Listo aprox. a las",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Incluye +10 min de ampliación").assertIsDisplayed()
        compose.onNodeWithText("Ver detalle").performClick()
        compose.onNodeWithText("Aceptar pedido").assertDoesNotExist()
        compose.onNodeWithText("Ampliar tiempo").performScrollTo().performClick()
        compose.onNodeWithText("Ampliar tiempo de preparación").assertIsDisplayed()
        compose.onNodeWithText("Aceptar y preparar").assertDoesNotExist()
        compose.onNodeWithText("+15 min").performClick()
        compose.onNodeWithText("Nueva hora estimada",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Confirmar ampliación").performClick()
        compose.runOnIdle {assertEquals("3".repeat(64) to 15,extended)}
    }
    @Test fun overdueEstimateAndInFlightExtensionAreExplained() {
        val overdue=StoreOrderSummary("4".repeat(64),1700000000000,listOf(StoreOrderLine("p","Plato",1,1000,1000)),
            OrderAmounts(1000,300,1300),OrderStatus.PREPARING,estimatedPreparationMinutes=25,estimatedReadyAtEpochMillis=1700001500000,
            estimateRevision=1)
        compose.setContent{ChaskiTheme{StoreOrdersContent(StoreOrdersUiState(context,loading=false,orders=listOf(overdue),
            selectedStatus=OrderStatus.PREPARING,respondingOrderId=overdue.orderId,extendingOrderId=overdue.orderId))}}
        compose.onNodeWithText("Hora estimada vencida",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Listo aprox.",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Ampliando tiempo…").assertIsDisplayed()
    }
    @Test fun uncertainExtensionOffersRetryAndDiscard() {
        var retries=0;var discards=0
        val pending=PendingPreparationExtension("3".repeat(64),PreparationExtension("3f8a1c2e-5b7d-4e9f-8a6b-1c2d3e4f5a6b",15,1))
        compose.setContent{ChaskiTheme{StoreOrdersContent(StoreOrdersUiState(context,loading=false,pendingExtension=pending),
            onRetryExtension={retries++},onDiscardExtension={discards++})}}
        compose.onNodeWithText("no se confirmó",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick();compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle {assertEquals(1,retries);assertEquals(1,discards)}
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
            override suspend fun respond(context:StoreOrdersContext,orderId:String,decision:StoreOrderDecision)=StoreOrderDecisionResult.Unavailable
            override suspend fun extendPreparation(context:StoreOrdersContext,orderId:String,extension:PreparationExtension)=
                PreparationExtensionResult.Unavailable
            override suspend fun markReady(context:StoreOrdersContext,orderId:String)=MarkOrderReadyResult.Unavailable
            override suspend fun page(context:StoreOrdersContext,cursor:String?,status:OrderStatus):StoreOrdersResult {
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
