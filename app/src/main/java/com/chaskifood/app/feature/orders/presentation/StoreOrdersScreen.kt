package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.chaskifood.app.feature.orders.domain.StorePickup
import com.chaskifood.app.feature.orders.domain.StoreRiderAssignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.core.navigation.LocalProtectedAction
import com.chaskifood.app.feature.cart.presentation.formatCartPrice
import com.chaskifood.app.feature.orders.domain.StoreOrderSummary
import com.chaskifood.app.feature.orders.domain.StoreOrderRejectionReason
import com.chaskifood.app.feature.orders.domain.OrderStatus
import com.chaskifood.app.feature.orders.domain.PreparationExtension
import com.chaskifood.app.feature.orders.domain.StoreOrderLine
import com.chaskifood.app.feature.orders.domain.OrderAmounts
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
        {authorize{viewModel.selectStatus(it)}},{id,minutes->authorize{viewModel.extendPreparation(id,minutes)}},
        {id->authorize{viewModel.markReady(id)}},{id->authorize{viewModel.simulatePickup(id)}},
        {id->authorize{viewModel.simulateArrival(id)}},{authorize{viewModel.retryPendingExtension()}},
        {authorize{viewModel.discardPendingExtension()}},
        onVerifyPickup={id->authorize{viewModel.openPickupVerification(id)}},
        onSubmitPickupCode={code->authorize{viewModel.submitPickupCode(code)}},
        onRetryPickup={authorize{viewModel.retryPickupVerification()}},
        onDismissPickup=viewModel::dismissPickupVerification,onSnackbarShown=viewModel::snackbarShown,
        onDismissRiderChange=viewModel::dismissRiderChange)
}

@Composable
internal fun StoreOrdersContent(state:StoreOrdersUiState,onBack:()->Unit={},onRefresh:()->Unit={},onMore:()->Unit={},
    onAccept:(String,Int)->Unit={_,_->},onReject:(String,StoreOrderRejectionReason,String?)->Unit={_,_,_->},
    onStatus:(OrderStatus)->Unit={},onExtend:(String,Int)->Unit={_,_->},onReady:(String)->Unit={},
    onPickup:(String)->Unit={},onArrival:(String)->Unit={},onRetryExtension:()->Unit={},onDiscardExtension:()->Unit={},
    onVerifyPickup:(String)->Unit={},onSubmitPickupCode:(String)->Unit={},onRetryPickup:()->Unit={},onDismissPickup:()->Unit={},
    onSnackbarShown:()->Unit={},onDismissRiderChange:(String)->Unit={}) {
    val snackbarHost=remember{SnackbarHostState()}
    LaunchedEffect(state.snackbar){state.snackbar?.let{snackbarHost.showSnackbar(it);onSnackbarShown()}}
    state.pickupDialog?.let{PickupVerificationSheet(it,onSubmitPickupCode,onRetryPickup,onDismissPickup)}
    var expanded by remember(state.context){mutableStateOf<String?>(null)}
    var accepting by remember{mutableStateOf<String?>(null)}
    var rejecting by remember{mutableStateOf<String?>(null)}
    var extending by remember{mutableStateOf<String?>(null)}
    accepting?.let{id->AcceptOrderDialog({accepting=null}){minutes->accepting=null;onAccept(id,minutes)}}
    rejecting?.let{id->RejectOrderDialog({rejecting=null}){reason,text->rejecting=null;onReject(id,reason,text)}}
    extending?.let{id->state.orders.firstOrNull{it.orderId==id&&it.estimatedReadyAtEpochMillis!=null}?.let{order->
        ExtendPreparationDialog(order.estimatedReadyAtEpochMillis!!,{extending=null}){minutes->extending=null;onExtend(id,minutes)}}}
    Scaffold(containerColor=ChaskiBackground,topBar={ChaskiAppBar(title="Pedidos del local",onBackClick=onBack)},
        snackbarHost={SnackbarHost(snackbarHost)}) {padding->
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
                    state.pendingExtension?.let{pending->item("pending-extension"){
                        PendingExtensionCard(pending,state.respondingOrderId==null,state.extendingOrderId!=null,
                            onRetryExtension,onDiscardExtension)}}
                    if(state.orders.isEmpty() && state.message==null)item("empty"){Text("No hay pedidos en esta sección.")}
                    items(state.orders,key={it.orderId}){order->
                        OrderInboxCard(order,expanded==order.orderId,state.respondingOrderId==null,
                            {expanded=if(expanded==order.orderId)null else order.orderId},{accepting=order.orderId},
                            {rejecting=order.orderId},{extending=order.orderId},{onReady(order.orderId)},
                            {onPickup(order.orderId)},{onArrival(order.orderId)},extending=state.extendingOrderId==order.orderId,
                            riderChanged=order.orderId in state.riderChangedOrderIds,onVerifyPickup={onVerifyPickup(order.orderId)},
                            onDismissRiderChange={onDismissRiderChange(order.orderId)})
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
    onAccept:()->Unit,onReject:()->Unit,onExtend:()->Unit,onReady:()->Unit,
    onPickup:()->Unit,onArrival:()->Unit,extending:Boolean=false,riderChanged:Boolean=false,onVerifyPickup:()->Unit={},
    onDismissRiderChange:()->Unit={}) {
    val date=remember(order.createdAtEpochMillis){SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).apply{
        timeZone=TimeZone.getTimeZone("America/Lima")}.format(Date(order.createdAtEpochMillis))}
    val statusText=when(order.status) {
        OrderStatus.PENDING_CONFIRMATION->"Pendiente de confirmación"
        OrderStatus.PREPARING->"En preparación"
        OrderStatus.READY_FOR_PICKUP->when{order.pickup?.locked==true->"Recojo bloqueado";order.pickup?.verified==true->"Código verificado"
            else->"Listo para recojo"}
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
            order.estimatedReadyAtEpochMillis?.let {
                // Reloj local por minuto (sin red): solo cambia el texto cuando la hora estimada vence.
                val now by produceState(System.currentTimeMillis(),it){while(true){delay(60_000);value=System.currentTimeMillis()}}
                val overdue=order.status==OrderStatus.PREPARING&&now>it
                Text(if(order.status==OrderStatus.PREPARING)storePreparationEstimateText(it,now)
                    else "Listo aprox. a las ${formatOrderClockTime(it)}",style=MaterialTheme.typography.bodyMedium,
                    fontWeight=FontWeight.SemiBold,color=if(overdue)ChaskiStatusPendingFg else ChaskiTextPrimary)
                if(extending)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    Text("Ampliando tiempo…",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)}
                if(order.preparationExtendedMinutes>0)Text("Incluye +${order.preparationExtendedMinutes} min de ampliación",
                    style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            }
            if(order.status==OrderStatus.READY_FOR_PICKUP)PickupSection(order.pickup,riderChanged,actionsEnabled,onVerifyPickup,onDismissRiderChange)
            if(order.status==OrderStatus.IN_DELIVERY&&order.riderManaged)PickupNotice(Icons.Filled.LocalShipping,
                "Recogido por el repartidor y en camino al cliente. Confirmado por Chaski Rider.",ChaskiStatusObservedBg,ChaskiStatusObservedFg)
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
                if(order.status==OrderStatus.PENDING_CONFIRMATION) {
                    ChaskiButton("Aceptar pedido",onClick=onAccept,enabled=actionsEnabled)
                    ChaskiButton("Rechazar pedido",onClick=onReject,enabled=actionsEnabled,
                        variant=ChaskiButtonVariant.Outlined)
                } else if(order.status==OrderStatus.PREPARING) {
                    ChaskiButton("Marcar pedido listo",onClick=onReady,enabled=actionsEnabled)
                    ChaskiButton("Ampliar tiempo",onClick=onExtend,enabled=actionsEnabled,
                        variant=ChaskiButtonVariant.Outlined)
                } else if(order.status==OrderStatus.READY_FOR_PICKUP && !order.riderManaged) {
                    // UI only: hiding this is not a security barrier; the backend must reject it for Rider orders (E5–E7).
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


/** HU21/01, 02, 07, 08, 09: logistics state of a ready order. The Food pickup code is never shown here. */
@Composable private fun PickupSection(pickup:StorePickup?,riderChanged:Boolean,actionsEnabled:Boolean,onVerify:()->Unit,
    onDismissRiderChange:()->Unit) {
    if(riderChanged)Surface(Modifier.fillMaxWidth(),color=ChaskiAdminCardBg,shape=MaterialTheme.shapes.medium){
        Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Filled.Sync,null,tint=ChaskiAdminAccent)
            Text(if(pickup?.assignment!=null)"El repartidor cambió. La verificación anterior ya no vale: verifica el código del nuevo repartidor."
                else "El repartidor asignado se liberó. Chaski Rider busca otro.",Modifier.weight(1f).padding(8.dp),
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextPrimary)
            IconButton(onClick=onDismissRiderChange){Icon(Icons.Filled.Close,"Descartar aviso",tint=ChaskiTextMuted)}
        }
    }
    val assignment=pickup?.assignment
    if(pickup==null||assignment==null){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Filled.HourglassTop,null,tint=ChaskiTextMuted)
            Text("Buscando repartidor. Podrás verificar el código cuando Chaski Rider asigne a alguien.",
                style=MaterialTheme.typography.bodyMedium)
        }
        return
    }
    RiderRow(assignment,if(riderChanged)"Nuevo repartidor asignado" else "Repartidor asignado")
    when{
        pickup.locked->{
            PickupNotice(Icons.Filled.Lock,"Se agotaron los 5 intentos. Un administrador de Chaski debe revisar el caso y "+
                "desbloquearlo; mientras tanto no se pueden ingresar códigos.",ChaskiStatusRejectedBg,ChaskiStatusRejectedFg)
            ChaskiButton("Verificar código del repartidor",onClick={},enabled=false)
            Text("Intentos disponibles: 0",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
        }
        pickup.verified->{
            PickupNotice(Icons.Filled.Verified,"Código verificado. Esperando que el repartidor confirme el recojo en Chaski Rider.",
                ChaskiAdminCardBg,ChaskiAdminAccent)
            Text("El pedido sigue «Listo para recoger» hasta la confirmación del repartidor.",
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
        }
        else->{
            Text("Pide al repartidor que te dicte su código de 4 dígitos.",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            ChaskiButton("Verificar código del repartidor",onClick=onVerify,enabled=actionsEnabled)
            Text("Intentos disponibles: ${pickup.attemptsRemaining}",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
        }
    }
}

@Composable private fun RiderRow(assignment:StoreRiderAssignment,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(Modifier.size(40.dp),shape=MaterialTheme.shapes.extraLarge,color=ChaskiAdminCardBg){
            Box(contentAlignment=Alignment.Center){Icon(if(assignment.vehicleType=="BICYCLE")Icons.Filled.PedalBike
                else Icons.Filled.TwoWheeler,null,tint=ChaskiAdminAccent)}}
        Column{
            Text(label,style=MaterialTheme.typography.labelSmall,color=ChaskiTextMuted)
            Text(assignment.riderDisplayName?:"Repartidor de Chaski Rider",style=MaterialTheme.typography.titleSmall,
                fontWeight=FontWeight.Bold)
            Text(riderVehicleText(assignment),style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
        }
    }
}

/** Only the contract fields: vehicle type and plate (a bicycle may have none). Never phone or rider id. */
internal fun riderVehicleText(assignment:StoreRiderAssignment):String {
    val vehicle=when(assignment.vehicleType){"MOTORCYCLE"->"Motocicleta";"BICYCLE"->"Bicicleta";else->"Vehículo"}
    return assignment.vehiclePlate?.let{"$vehicle · Placa $it"}?:vehicle
}

@Composable private fun PickupNotice(icon:ImageVector,text:String,background:Color,foreground:Color) {
    Surface(Modifier.fillMaxWidth().semantics{liveRegion=LiveRegionMode.Polite},color=background,shape=MaterialTheme.shapes.medium){
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(icon,null,tint=foreground);Text(text,style=MaterialTheme.typography.bodyMedium,color=foreground)
        }
    }
}

/**
 * HU21/03–06: one numeric field of four digits. While sending, nothing can close or resend it. A wrong code clears the
 * field (new attempt, new operation). A connection error offers Retry (same operation) or a safe exit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PickupVerificationSheet(dialog:PickupVerificationUi,onSubmit:(String)->Unit,onRetry:()->Unit,onDismiss:()->Unit) {
    val sending=dialog.phase==PickupVerificationPhase.SENDING
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,
        confirmValueChange={it!=SheetValue.Hidden||dialog.phase!=PickupVerificationPhase.SENDING})
    var code by remember(dialog.orderId,dialog.inputGeneration){mutableStateOf("")}
    ModalBottomSheet(onDismissRequest={if(!sending)onDismiss()},sheetState=sheetState,containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text("Verificar código del repartidor",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("${dialog.assignment.riderDisplayName?:"Repartidor"} · ${riderVehicleText(dialog.assignment)}",
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            val invalid=dialog.phase==PickupVerificationPhase.INVALID_CODE
            OutlinedTextField(code,{if(!sending&&dialog.phase!=PickupVerificationPhase.CONNECTION_ERROR)code=it.filter(Char::isDigit).take(4)},
                modifier=Modifier.fillMaxWidth().semantics{contentDescription="Código de 4 dígitos del repartidor"},
                singleLine=true,isError=invalid,enabled=!sending&&dialog.phase!=PickupVerificationPhase.CONNECTION_ERROR,
                placeholder={Text("_ _ _ _",Modifier.fillMaxWidth(),textAlign=TextAlign.Center)},
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle=TextStyle(fontSize=24.sp,letterSpacing=8.sp,textAlign=TextAlign.Center),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword,autoCorrectEnabled=false))
            val (icon,message,color)=when(dialog.phase){
                PickupVerificationPhase.EDITING->Triple(null,"Ingresa los 4 dígitos que te dicta el repartidor.",ChaskiTextMuted)
                PickupVerificationPhase.SENDING->Triple(null,"Verificando con Chaski Food… No cierres esta ventana.",ChaskiTextMuted)
                PickupVerificationPhase.INVALID_CODE->Triple(Icons.Filled.Error,"Código incorrecto. Te quedan ${dialog.attemptsRemaining} "+
                    "intentos. Ingresa el código de nuevo: cada código nuevo es un intento distinto. Si se agotan, el recojo se "+
                    "bloquea y deberá revisarlo un administrador de Chaski.",ChaskiStatusRejectedFg)
                PickupVerificationPhase.CONNECTION_ERROR->Triple(Icons.Filled.WifiOff,"No pudimos saber si la verificación llegó. "+
                    "«Reintentar» consulta el resultado de esta misma operación (no envía un código nuevo). Si sales, el resultado "+
                    "se verá al actualizar la bandeja.",ChaskiTextPrimary)
            }
            Row(Modifier.semantics{liveRegion=LiveRegionMode.Polite},horizontalArrangement=Arrangement.spacedBy(8.dp)){
                icon?.let{Icon(it,null,tint=color)};Text(message,style=MaterialTheme.typography.bodySmall,color=color)}
            if(sending)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(dialog.phase==PickupVerificationPhase.CONNECTION_ERROR)ChaskiButton("Reintentar",onClick=onRetry)
            else ChaskiButton(if(sending)"Verificando…" else "Verificar",onClick={onSubmit(code)},enabled=!sending&&code.length==4)
            ChaskiButton("Cancelar",onClick=onDismiss,enabled=!sending,variant=ChaskiButtonVariant.Outlined)
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

/**
 * HU20: adds minutes to the current estimate; it never replaces the HU19 duration. The new time shown is a preview
 * computed with the device clock; foodApi returns the authoritative time.
 */
@Composable internal fun ExtendPreparationDialog(currentReadyAtEpochMillis:Long,onDismiss:()->Unit,
    nowEpochMillis:()->Long={System.currentTimeMillis()},onConfirm:(Int)->Unit) {
    var minutes by remember{mutableStateOf("10")}
    val value=minutes.toIntOrNull()
    val valid=value!=null&&value in PreparationExtension.MIN_MINUTES..PreparationExtension.MAX_MINUTES
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ampliar tiempo de preparación")},
        text={Column(verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)){
            Text("Se sumarán minutos a la hora estimada actual; no se reemplaza la estimación inicial.")
            Text("Hora estimada actual: ${formatOrderClockTime(currentReadyAtEpochMillis)}",fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(5,10,15,30).forEach{option->
                    FilterChip(value==option,{minutes=option.toString()},{Text("+$option min")})
                }
            }
            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(2)},label={Text("Minutos adicionales (5 a 60)")},
                singleLine=true,isError=minutes.isNotEmpty()&&!valid)
            if(valid)Text("Nueva hora estimada: ≈ ${formatOrderClockTime(
                extendedPreviewEpochMillis(currentReadyAtEpochMillis,nowEpochMillis(),value!!))}",fontWeight=FontWeight.Bold)
            else Text("Indica entre 5 y 60 minutos.",color=ChaskiTextMuted)
        }},
        confirmButton={TextButton(onClick={onConfirm(value!!)},enabled=valid){Text("Confirmar ampliación")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Volver")}})
}

@Composable private fun PendingExtensionCard(pending:PendingPreparationExtension,enabled:Boolean,sending:Boolean,
    onRetry:()->Unit,onDiscard:()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth(),color=ChaskiStatusPendingBg,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.padding(ChaskiDimens.SpacingMd),verticalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
            if(sending) {
                // Reintento en curso: no se afirma éxito ni fallo hasta la respuesta de foodApi.
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    Text("Ampliando tiempo… (+${pending.extension.additionalMinutes} min)",color=ChaskiStatusPendingFg)}
                return@Column
            }
            Text("La ampliación de +${pending.extension.additionalMinutes} min del pedido ${pending.orderId.take(8)} no se confirmó.",
                color=ChaskiStatusPendingFg,fontWeight=FontWeight.SemiBold)
            Text("Reintentar la envía otra vez sin sumarla dos veces. Descartar recarga la bandeja.",
                style=MaterialTheme.typography.bodySmall,color=ChaskiStatusPendingFg)
            Row(horizontalArrangement=Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                TextButton(onClick=onRetry,enabled=enabled){Text("Reintentar")}
                TextButton(onClick=onDiscard,enabled=enabled){Text("Descartar")}
            }
        }
    }
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

private val previewPreparingOrder = StoreOrderSummary("5e2c7a90" + "0".repeat(56), 1_800_000_000_000,
    listOf(StoreOrderLine("p1", "Lomo saltado", 2, 1500, 3000)), OrderAmounts(3000, 300, 3300), OrderStatus.PREPARING,
    estimatedPreparationMinutes = 40, estimatedReadyAtEpochMillis = 1_800_003_300_000, estimateRevision = 1, preparationExtendedMinutes = 15)

@androidx.compose.ui.tooling.preview.Preview(name = "Bandeja · en preparación (ampliado)", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun StoreOrdersPreparingPreview() = ChaskiTheme {
    StoreOrdersContent(StoreOrdersUiState(loading = false, storeName = "Local de ejemplo · Centro", orders = listOf(previewPreparingOrder),
        selectedStatus = OrderStatus.PREPARING))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Bandeja · hora vencida y ampliando", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun StoreOrdersOverdueExtendingPreview() = ChaskiTheme {
    // Hora estimada en el pasado (vencida) mientras se procesa una ampliación de +10 min.
    val overdue = previewPreparingOrder.withExtension(1_700_000_000_000, 1, 15)
    StoreOrdersContent(StoreOrdersUiState(loading = false, storeName = "Local de ejemplo · Centro", orders = listOf(overdue),
        selectedStatus = OrderStatus.PREPARING, respondingOrderId = overdue.orderId, extendingOrderId = overdue.orderId,
        pendingExtension = PendingPreparationExtension(overdue.orderId,
            PreparationExtension("3f8a1c2e-5b7d-4e9f-8a6b-1c2d3e4f5a6b", 10, 1))))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Diálogo · ampliar tiempo", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ExtendPreparationDialogPreview() = ChaskiTheme {
    ExtendPreparationDialog(1_800_003_300_000, {}, nowEpochMillis = { 1_800_001_000_000 }) {}
}

private val previewRider=StoreRiderAssignment("asg-1",1,"Carlos M.","MOTORCYCLE","ABC-123")
private fun previewReady(pickup:StorePickup?)=StoreOrderSummary("7f3a9c21"+"0".repeat(56),1_800_000_000_000,
    listOf(StoreOrderLine("p1","Lomo saltado",2,1500,3000)),OrderAmounts(3000,300,3300),OrderStatus.READY_FOR_PICKUP,
    estimatedPreparationMinutes=30,estimatedReadyAtEpochMillis=1_800_001_800_000,readyAtEpochMillis=1_800_001_700_000,pickup=pickup)
@Composable private fun ReadyPreview(pickup:StorePickup?,changed:Boolean=false)=ChaskiTheme{
    val order=previewReady(pickup)
    StoreOrdersContent(StoreOrdersUiState(loading=false,storeName="Local Centro",orders=listOf(order),
        selectedStatus=OrderStatus.READY_FOR_PICKUP,riderChangedOrderIds=if(changed)setOf(order.orderId)else emptySet()))
}
@androidx.compose.ui.tooling.preview.Preview(name="HU21/01 · buscando repartidor",showBackground=true,widthDp=360,heightDp=900)
@Composable private fun PickupSearchingPreview()=ReadyPreview(StorePickup(null,false,false,5))
@androidx.compose.ui.tooling.preview.Preview(name="HU21/02 · repartidor asignado",showBackground=true,widthDp=360,heightDp=900)
@Composable private fun PickupAssignedPreview()=ReadyPreview(StorePickup(previewRider,false,false,5))
@androidx.compose.ui.tooling.preview.Preview(name="HU21/07 · código verificado",showBackground=true,widthDp=360,heightDp=900)
@Composable private fun PickupVerifiedPreview()=ReadyPreview(StorePickup(previewRider,true,false,5))
@androidx.compose.ui.tooling.preview.Preview(name="HU21/08 · recojo bloqueado",showBackground=true,widthDp=360,heightDp=900)
@Composable private fun PickupLockedPreview()=ReadyPreview(StorePickup(previewRider,false,true,0))
@androidx.compose.ui.tooling.preview.Preview(name="HU21/09 · repartidor cambió",showBackground=true,widthDp=360,heightDp=900)
@Composable private fun PickupChangedPreview()=ReadyPreview(StorePickup(StoreRiderAssignment("asg-2",2,"Luis R.","MOTORCYCLE","XYZ-456"),false,false,3),true)
@androidx.compose.ui.tooling.preview.Preview(name="HU21/05 · código incorrecto",showBackground=true,widthDp=360,heightDp=640)
@Composable private fun PickupInvalidPreview()=ChaskiTheme{
    PickupVerificationSheet(PickupVerificationUi("x",previewRider,3,PickupVerificationPhase.INVALID_CODE),{},{},{})}
@androidx.compose.ui.tooling.preview.Preview(name="HU21/06 · error de conexión",showBackground=true,widthDp=360,heightDp=640)
@Composable private fun PickupConnectionPreview()=ChaskiTheme{
    PickupVerificationSheet(PickupVerificationUi("x",previewRider,5,PickupVerificationPhase.CONNECTION_ERROR),{},{},{})}
