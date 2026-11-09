package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.core.navigation.LocalProtectedAction
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.orders.domain.StoreOrderSummary
import com.chaskifood.app.feature.orders.domain.StoreOrderRejectionReason
import com.chaskifood.app.feature.orders.domain.OrderStatus
import com.chaskifood.app.ui.components.*
import com.chaskifood.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StoreOrdersScreen(onBack:()->Unit,viewModel:StoreOrdersViewModel=hiltViewModel(),notificationRefresh:String?=null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(notificationRefresh){if(notificationRefresh!=null)viewModel.refreshFromNotification()}
    val authorize=LocalProtectedAction.current
    StoreOrdersContent(state,onBack,{authorize{viewModel.refresh()}},{authorize{viewModel.loadMore()}},
        {id,minutes->authorize{viewModel.accept(id,minutes)}},{id,reason,text->authorize{viewModel.reject(id,reason,text)}},
        {authorize{viewModel.selectStatus(it)}},{id,minutes->authorize{viewModel.updateEstimate(id,minutes)}},
        {id->authorize{viewModel.markReady(id)}},{id->authorize{viewModel.simulatePickup(id)}},
        {id->authorize{viewModel.simulateArrival(id)}})
}

@Composable
internal fun StoreOrdersContent(state:StoreOrdersUiState,onBack:()->Unit={},onRefresh:()->Unit={},onMore:()->Unit={},
    onAccept:(String,Int)->Unit={_,_->},onReject:(String,StoreOrderRejectionReason,String?)->Unit={_,_,_->},
    onStatus:(OrderStatus)->Unit={},onEstimate:(String,Int)->Unit={_,_->},onReady:(String)->Unit={},
    onPickup:(String)->Unit={},onArrival:(String)->Unit={}) {
    var expanded by remember(state.context){mutableStateOf<String?>(null)}
    var accepting by remember{mutableStateOf<String?>(null)}
    var rejecting by remember{mutableStateOf<String?>(null)}
    var estimating by remember{mutableStateOf<String?>(null)}
    accepting?.let{id->AcceptOrderDialog({accepting=null}){minutes->accepting=null;onAccept(id,minutes)}}
    rejecting?.let{id->RejectOrderDialog({rejecting=null}){reason,text->rejecting=null;onReject(id,reason,text)}}
    estimating?.let{id->AcceptOrderDialog({estimating=null}){minutes->estimating=null;onEstimate(id,minutes)}}
    Scaffold(containerColor=ChaskiBackground,topBar={ChaskiAppBar(title="Pedidos del local",onBackClick=onBack)}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(ChaskiDimens.ScreenPadding),
            verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
            if(state.storeName.isNotBlank())item("store"){Text(state.storeName,style=MaterialTheme.typography.titleLarge)}
            item("queues"){Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(state.selectedStatus==OrderStatus.PENDING_CONFIRMATION,{onStatus(OrderStatus.PENDING_CONFIRMATION)},{Text("Pendientes")})
                FilterChip(state.selectedStatus==OrderStatus.PREPARING,{onStatus(OrderStatus.PREPARING)},{Text("En preparación")})
                FilterChip(state.selectedStatus==OrderStatus.READY_FOR_PICKUP,{onStatus(OrderStatus.READY_FOR_PICKUP)},{Text("Listos")})
                FilterChip(state.selectedStatus==OrderStatus.IN_DELIVERY,{onStatus(OrderStatus.IN_DELIVERY)},{Text("En reparto")})}}
            when {
                state.loading->item("loading"){CircularProgressIndicator();Text("Consultando pedidos…")}
                state.accessDenied->item("denied"){Text(state.message.orEmpty(),modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
                else->{
                    item("refresh") {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(onClick=onRefresh,enabled=!state.loadingMore) { Text("Actualizar bandeja") }
                        }
                    }
                    state.message?.let{message->item("error"){Text(message,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}}
                    if(state.orders.isEmpty() && state.message==null)item("empty"){Text("No hay pedidos en esta sección.")}
                    items(state.orders,key={it.orderId}){order->
                        OrderInboxCard(order,expanded==order.orderId,state.respondingOrderId==null,
                            {expanded=if(expanded==order.orderId)null else order.orderId},{accepting=order.orderId},
                            {rejecting=order.orderId},{estimating=order.orderId},{onReady(order.orderId)},
                            {onPickup(order.orderId)},{onArrival(order.orderId)})
                    }
                    if(state.loadingMore)item("more-loading"){CircularProgressIndicator()}
                    if(state.nextCursor!=null)item("more"){ChaskiButton("Cargar más",onClick=onMore,enabled=!state.loadingMore,
                        variant=ChaskiButtonVariant.Outlined)}
                }
            }
        }
    }
}
@Composable private fun OrderInboxCard(order:StoreOrderSummary,expanded:Boolean,actionsEnabled:Boolean,onToggle:()->Unit,
    onAccept:()->Unit,onReject:()->Unit,onEstimate:()->Unit,onReady:()->Unit,
    onPickup:()->Unit,onArrival:()->Unit) {
    val date=remember(order.createdAtEpochMillis){SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).apply{
        timeZone=TimeZone.getTimeZone("America/Lima")}.format(Date(order.createdAtEpochMillis))}
    val statusText=when(order.status) {
        OrderStatus.PENDING_CONFIRMATION->"Pendiente de confirmación"
        OrderStatus.PREPARING->"En preparación"
        OrderStatus.READY_FOR_PICKUP->"Listo para recojo"
        OrderStatus.IN_DELIVERY->if(order.simulationStage=="AT_CUSTOMER")"Repartidor llegó" else "En reparto"
    }
    val statusBackground=when(order.status) {
        OrderStatus.PENDING_CONFIRMATION->ChaskiStatusPendingBg
        OrderStatus.PREPARING->ChaskiAdminCardBg
        OrderStatus.READY_FOR_PICKUP->ChaskiStatusApprovedBg
        OrderStatus.IN_DELIVERY->ChaskiStatusApprovedBg
    }
    val statusForeground=when(order.status) {
        OrderStatus.PENDING_CONFIRMATION->ChaskiStatusPendingFg
        OrderStatus.PREPARING->ChaskiAdminAccent
        OrderStatus.READY_FOR_PICKUP->ChaskiStatusApprovedFg
        OrderStatus.IN_DELIVERY->ChaskiStatusApprovedFg
    }
    Surface(modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,
        shape=MaterialTheme.shapes.large,tonalElevation=1.dp,shadowElevation=1.dp) {
        Column(Modifier.padding(ChaskiDimens.SpacingMd),verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
            Text("Pedido ${order.orderId.take(8)}",style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold,color=ChaskiTextPrimary)
            Text(date,style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            ChaskiStatusPill(statusText,statusBackground,statusForeground)
            order.estimatedPreparationMinutes?.let {
                Text("Estimación: $it minutos",style=MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color=ChaskiDivider)
            Text("Productos",style=MaterialTheme.typography.labelMedium,color=ChaskiTextMuted)
            order.items.take(2).forEach { item ->
                Text("${item.quantity} × ${item.name}",style=MaterialTheme.typography.bodyMedium)
            }
            if(order.items.size>2) Text("+${order.items.size-2} productos más",
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Total",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(formatCartPrice(order.amounts.totalMinor),style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold,color=ChaskiPrice)
            }
            TextButton(onClick=onToggle,modifier=Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text(if(expanded)"Ocultar detalle" else "Ver detalle")
            }
            if(expanded) {
                HorizontalDivider(color=ChaskiDivider)
                Text("Detalle del pedido",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                order.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                        Column(Modifier.weight(1f)) {
                            Text("${item.quantity} × ${item.name}",style=MaterialTheme.typography.bodyMedium)
                            Text("${formatCartPrice(item.unitPriceMinor)} c/u",style=MaterialTheme.typography.bodySmall,
                                color=ChaskiTextMuted)
                        }
                        Text(formatCartPrice(item.lineTotalMinor),style=MaterialTheme.typography.bodyMedium,
                            fontWeight=FontWeight.SemiBold)
                    }
                }
                OrderAmountRow("Subtotal",order.amounts.subtotalMinor)
                OrderAmountRow("Reparto",order.amounts.deliveryFeeMinor)
                OrderAmountRow("Propina",order.amounts.tipMinor)
                Text("Efectivo al recibir · PEN",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
                order.pickupCode?.let {
                    Text("Código de recojo: $it",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold)
                }
                if(order.status==OrderStatus.PENDING_CONFIRMATION) {
                    ChaskiButton("Aceptar pedido",onClick=onAccept,enabled=actionsEnabled)
                    ChaskiButton("Rechazar pedido",onClick=onReject,enabled=actionsEnabled,
                        variant=ChaskiButtonVariant.Outlined)
                } else if(order.status==OrderStatus.PREPARING) {
                    ChaskiButton("Marcar pedido listo",onClick=onReady,enabled=actionsEnabled)
                    ChaskiButton("Actualizar estimación",onClick=onEstimate,enabled=actionsEnabled,
                        variant=ChaskiButtonVariant.Outlined)
                } else if(order.status==OrderStatus.READY_FOR_PICKUP) {
                    ChaskiButton("Pedido recogido",onClick=onPickup,enabled=actionsEnabled)
                    Text("Simulación temporal desde Food.",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
                } else if(order.status==OrderStatus.IN_DELIVERY && order.simulationStage=="ON_THE_WAY") {
                    ChaskiButton("Simular llegada del repartidor",onClick=onArrival,enabled=actionsEnabled)
                } else if(order.status==OrderStatus.IN_DELIVERY && order.simulationStage=="AT_CUSTOMER") {
                    Text("Esperando confirmación de recepción del cliente.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun OrderAmountRow(label:String,amountMinor:Long) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=ChaskiTextMuted)
        Text(formatCartPrice(amountMinor),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun AcceptOrderDialog(onDismiss:()->Unit,onConfirm:(Int)->Unit) {
    var minutes by remember{mutableStateOf("30")};val value=minutes.toIntOrNull();val valid=value!=null&&value in 5..180
    AlertDialog(onDismissRequest=onDismiss,title={Text("Aceptar pedido")},
        text={Column(verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)){
            Text("Indica el tiempo estimado de preparación (5 a 180 minutos).")
            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},label={Text("Minutos")},singleLine=true,
                isError=minutes.isNotEmpty()&&!valid)
        }},confirmButton={TextButton(onClick={onConfirm(value!!)},enabled=valid){Text("Aceptar y preparar")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Volver")}})
}

private fun StoreOrderRejectionReason.label()=when(this){
    StoreOrderRejectionReason.STORE_BUSY->"Alta demanda"
    StoreOrderRejectionReason.PRODUCT_UNAVAILABLE->"Producto no disponible"
    StoreOrderRejectionReason.CLOSING_SOON->"Local próximo a cerrar"
    StoreOrderRejectionReason.OTHER->"Otro"
}
@Composable private fun RejectOrderDialog(onDismiss:()->Unit,onConfirm:(StoreOrderRejectionReason,String?)->Unit) {
    var reason by remember{mutableStateOf(StoreOrderRejectionReason.STORE_BUSY)};var explanation by remember{mutableStateOf("")}
    val valid=reason!=StoreOrderRejectionReason.OTHER||explanation.trim().length in 3..300
    AlertDialog(onDismissRequest=onDismiss,title={Text("Rechazar pedido")},text={Column{
        Text("Selecciona el motivo del rechazo.")
        StoreOrderRejectionReason.entries.forEach{option->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            RadioButton(selected=reason==option,onClick={reason=option});Text(option.label(),modifier=Modifier.padding(top=12.dp))}}
        if(reason==StoreOrderRejectionReason.OTHER)OutlinedTextField(explanation,{explanation=it.take(300)},
            label={Text("Explicación obligatoria")},minLines=2,isError=explanation.isNotEmpty()&&!valid)
    }},confirmButton={TextButton(onClick={onConfirm(reason,explanation.trim().takeIf{reason==StoreOrderRejectionReason.OTHER})},enabled=valid){Text("Confirmar rechazo")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Volver")}})
}
