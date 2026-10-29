package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.core.navigation.LocalProtectedAction
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.orders.domain.StoreOrderSummary
import com.chaskifood.app.ui.components.*
import com.chaskifood.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StoreOrdersScreen(onBack:()->Unit,viewModel:StoreOrdersViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authorize=LocalProtectedAction.current
    StoreOrdersContent(state,onBack,{authorize{viewModel.refresh()}},{authorize{viewModel.loadMore()}})
}

@Composable
internal fun StoreOrdersContent(state:StoreOrdersUiState,onBack:()->Unit={},onRefresh:()->Unit={},onMore:()->Unit={}) {
    var expanded by remember(state.context){mutableStateOf<String?>(null)}
    Scaffold(containerColor=ChaskiBackground,topBar={ChaskiAppBar(title="Pedidos pendientes",onBackClick=onBack)}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(ChaskiDimens.ScreenPadding),
            verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
            if(state.storeName.isNotBlank())item("store"){Text(state.storeName,style=MaterialTheme.typography.titleLarge)}
            when {
                state.loading->item("loading"){CircularProgressIndicator();Text("Consultando pedidos…")}
                state.accessDenied->item("denied"){Text(state.message.orEmpty(),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
                else->{
                    item("refresh"){ChaskiButton("Actualizar bandeja",onClick=onRefresh,enabled=!state.loadingMore)}
                    state.message?.let{message->item("error"){Text(message,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}}
                    if(state.orders.isEmpty() && state.message==null)item("empty"){Text("No hay pedidos pendientes en este local.")}
                    items(state.orders,key={it.orderId}){order->
                        OrderInboxCard(order,expanded==order.orderId){expanded=if(expanded==order.orderId)null else order.orderId}
                    }
                    if(state.loadingMore)item("more-loading"){CircularProgressIndicator()}
                    if(state.nextCursor!=null)item("more"){ChaskiButton("Cargar más",onClick=onMore,enabled=!state.loadingMore,
                        variant=ChaskiButtonVariant.Outlined)}
                }
            }
        }
    }
}
@Composable private fun OrderInboxCard(order:StoreOrderSummary,expanded:Boolean,onToggle:()->Unit) {
    val date=remember(order.createdAtEpochMillis){SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).apply{
        timeZone=TimeZone.getTimeZone("America/Lima")}.format(Date(order.createdAtEpochMillis))}
    Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(ChaskiDimens.SpacingMd),verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
            Text("Pedido ${order.orderId.take(8)}",fontWeight=FontWeight.Bold)
            Text(date);Text("Pendiente de confirmación")
            order.items.forEach{Text("${it.quantity} × ${it.name}")}
            Text("Total: ${formatCartPrice(order.amounts.totalMinor)}",fontWeight=FontWeight.Bold)
            TextButton(onClick=onToggle){Text(if(expanded)"Ocultar detalle" else "Ver detalle")}
            if(expanded){
                Text("Identificador: ${order.orderId}")
                order.items.forEach{Text("${it.name}: ${formatCartPrice(it.unitPriceMinor)} × ${it.quantity} = ${formatCartPrice(it.lineTotalMinor)}")}
                Text("Subtotal: ${formatCartPrice(order.amounts.subtotalMinor)}")
                Text("Reparto: ${formatCartPrice(order.amounts.deliveryFeeMinor)}")
                Text("Efectivo al recibir · PEN")
            }
        }
    }
}
