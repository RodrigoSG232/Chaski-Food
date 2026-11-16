package com.chaskifood.app.feature.orders.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.orders.domain.PickupIncident
import com.chaskifood.app.feature.orders.domain.PickupUnlockRequest
import com.chaskifood.app.ui.components.*
import com.chaskifood.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/** Pencil HU21 / Admin 02–07: pickup incidents. Never shows the pickup code nor buyer data. */
@Composable
fun PickupIncidentsScreen(onBack:()->Unit,viewModel:PickupIncidentsViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled=state.selected!=null){viewModel.backToList()}
    PickupIncidentsContent(state,onBack,viewModel::load,viewModel::select,viewModel::backToList,viewModel::requestUnlock,
        viewModel::confirmUnlock,viewModel::cancelConfirmation,viewModel::retryUnlock)
}

@Composable
internal fun PickupIncidentsContent(state:PickupIncidentsUiState,onBack:()->Unit={},onReload:()->Unit={},onSelect:(String)->Unit={},
    onBackToList:()->Unit={},onRequestUnlock:(String)->Unit={},onConfirm:()->Unit={},onCancelConfirm:()->Unit={},onRetry:()->Unit={}) {
    val selected=state.selected
    Scaffold(containerColor=ChaskiBackground,topBar={ChaskiAppBar(title=if(selected==null)"Incidencias de recojo" else "Incidencia de recojo",
        onBackClick=if(selected==null)onBack else onBackToList)}) {padding->
        Box(Modifier.fillMaxSize().padding(padding)){
            when{
                state.accessDenied->Message(Icons.Filled.GppBad,"Tu cuenta ya no tiene permiso de administración.",null)
                selected!=null&&state.unlockedCycle!=null->UnlockDone(selected,state.unlockedCycle,onBackToList)
                selected!=null->IncidentDetail(selected,state,onRequestUnlock,onConfirm,onCancelConfirm,onRetry,onBackToList)
                state.loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                state.loadError!=null->Message(Icons.Filled.WifiOff,state.loadError,onReload)
                state.incidents.isEmpty()->Message(Icons.Filled.VerifiedUser,"No hay recojos bloqueados. Cuando un local agote los "+
                    "intentos de un código, aparecerá aquí.",onReload)
                else->LazyColumn(contentPadding=PaddingValues(ChaskiDimens.ScreenPadding),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    item{Text("Pedidos cuyo código de recojo se bloqueó tras 5 intentos incorrectos. Solo se muestran los datos "+
                        "necesarios para revisarlos.",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)}
                    items(state.incidents,key={it.orderId}){IncidentCard(it){onSelect(it.orderId)}}
                    if(state.truncated)item{Text("Se muestran las 50 más antiguas.",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)}
                }
            }
        }
    }
}

private fun lockedTime(millis:Long)=SimpleDateFormat("dd/MM HH:mm",Locale.forLanguageTag("es-PE")).apply{
    timeZone=TimeZone.getTimeZone("America/Lima")}.format(Date(millis))

@Composable private fun IncidentCard(incident:PickupIncident,onClick:()->Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=MaterialTheme.shapes.large,tonalElevation=1.dp,shadowElevation=1.dp){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("Pedido #${incident.shortId}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                ChaskiStatusPill("Bloqueado",ChaskiStatusRejectedBg,ChaskiStatusRejectedFg)
            }
            Text(incident.storeName?:"Local",style=MaterialTheme.typography.bodyMedium)
            Text("Bloqueado desde ${lockedTime(incident.lockedAtEpochMillis)} · Ciclo ${incident.cycle} · ${incident.failures} intentos fallidos",
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
            Text(if(incident.hasAssignment)"Con repartidor asignado" else "Sin repartidor asignado",
                style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)
        }
    }
}

@Composable private fun IncidentDetail(incident:PickupIncident,state:PickupIncidentsUiState,onRequest:(String)->Unit,onConfirm:()->Unit,
    onCancelConfirm:()->Unit,onRetry:()->Unit,onBackToList:()->Unit) {
    var reason by remember(incident.orderId){mutableStateOf("")}
    val sending=state.phase==PickupUnlockPhase.SENDING
    if(state.phase!=PickupUnlockPhase.EDITING)ConfirmUnlockDialog(incident,sending,state.error==PickupUnlockError.CONNECTION,
        onConfirm,onRetry,onCancelConfirm)
    LazyColumn(contentPadding=PaddingValues(ChaskiDimens.ScreenPadding),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,tonalElevation=1.dp){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Pedido" to "#${incident.shortId}","Local" to (incident.storeName?:"—"),
                    "Estado" to (if(incident.status=="READY_FOR_PICKUP")"Listo · recojo bloqueado" else incident.status),
                    "Bloqueado desde" to lockedTime(incident.lockedAtEpochMillis),
                    "Ciclo / intentos" to "${incident.cycle} · ${incident.failures} fallidos",
                    "Repartidor" to (if(incident.hasAssignment)"Asignado" else "Sin asignar")).forEach{(k,v)->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(k,style=MaterialTheme.typography.bodyMedium,color=ChaskiTextMuted)
                        Text(v,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)}
                }
            }}}
        item{Text("Al desbloquear se genera un código nuevo, se reinician los intentos y el código anterior deja de servir. "+
            "El repartidor vigente deberá consultarlo de nuevo en Chaski Rider.",style=MaterialTheme.typography.bodySmall,color=ChaskiTextMuted)}
        when(state.error){
            PickupUnlockError.NOT_LOCKED->item{ErrorNotice(Icons.Filled.Error,"Este pedido ya no está bloqueado (otro administrador "+
                "lo desbloqueó o cambió de estado). Vuelve a la lista para actualizarla.")}
            PickupUnlockError.PERMISSION->item{ErrorNotice(Icons.Filled.GppBad,"Tu cuenta ya no tiene permiso de administración.")}
            PickupUnlockError.CONNECTION->if(state.phase==PickupUnlockPhase.EDITING)item{ErrorNotice(Icons.Filled.WifiOff,
                "No pudimos confirmar el desbloqueo. Vuelve a la lista para ver el estado actual antes de intentarlo otra vez.")}
            else->Unit
        }
        item{Text("Motivo del desbloqueo (obligatorio)",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)}
        item{OutlinedTextField(reason,{reason=it.take(PickupUnlockRequest.MAX_REASON+20)},Modifier.fillMaxWidth(),minLines=3,
            isError=state.error==PickupUnlockError.INVALID_REASON,enabled=!sending,
            placeholder={Text("Describe por qué se desbloquea (5 a 200 caracteres)")})}
        item{Text(if(state.error==PickupUnlockError.INVALID_REASON)"Escribe un motivo de al menos 5 caracteres (sin contar espacios al inicio o al final)."
            else "${reason.trim().length}/${PickupUnlockRequest.MAX_REASON} · se recortan los espacios al inicio y al final antes de enviar",
            style=MaterialTheme.typography.bodySmall,
            color=if(state.error==PickupUnlockError.INVALID_REASON)ChaskiStatusRejectedFg else ChaskiTextMuted,
            modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
        item{ChaskiButton("Desbloquear y generar código nuevo",onClick={onRequest(reason)},
            enabled=!sending&&state.error!=PickupUnlockError.PERMISSION&&state.error!=PickupUnlockError.NOT_LOCKED)}
        if(state.error==PickupUnlockError.NOT_LOCKED)item{ChaskiButton("Volver a incidencias",onClick=onBackToList,variant=ChaskiButtonVariant.Outlined)}
    }
}

@Composable private fun ConfirmUnlockDialog(incident:PickupIncident,sending:Boolean,connectionError:Boolean,onConfirm:()->Unit,
    onRetry:()->Unit,onCancel:()->Unit) {
    AlertDialog(onDismissRequest={if(!sending)onCancel()},
        title={Text("¿Desbloquear el recojo del pedido #${incident.shortId}?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Se generará un código nuevo para el repartidor. Esta acción queda registrada con tu cuenta y el motivo indicado.")
            if(connectionError)Text("No pudimos confirmar el desbloqueo. Reintentar consulta el resultado de la misma operación; "+
                "no genera un segundo código.",color=ChaskiStatusRejectedFg,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            if(sending)LinearProgressIndicator(Modifier.fillMaxWidth())
        }},
        confirmButton={TextButton(onClick=if(connectionError)onRetry else onConfirm,enabled=!sending){
            Text(if(connectionError)"Reintentar" else if(sending)"Desbloqueando…" else "Desbloquear")}},
        dismissButton={TextButton(onClick=onCancel,enabled=!sending){Text("Volver")}})
}

@Composable private fun UnlockDone(incident:PickupIncident,cycle:Int,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)){
        Icon(Icons.Filled.CheckCircle,null,tint=ChaskiStatusApprovedFg,modifier=Modifier.size(48.dp))
        Text("Recojo desbloqueado",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("Pedido #${incident.shortId} · Ciclo $cycle. Se generó un código nuevo y los intentos se reiniciaron. El código no se "+
            "muestra aquí: lo recibe el repartidor vigente en Chaski Rider.",textAlign=TextAlign.Center,color=ChaskiTextMuted)
        ChaskiButton("Volver a incidencias",onClick=onBack,variant=ChaskiButtonVariant.Outlined)
    }
}

@Composable private fun ErrorNotice(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String) {
    Surface(Modifier.fillMaxWidth().semantics{liveRegion=LiveRegionMode.Polite},color=ChaskiStatusRejectedBg,shape=MaterialTheme.shapes.medium){
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(icon,null,tint=ChaskiStatusRejectedFg);Text(text,style=MaterialTheme.typography.bodyMedium,color=ChaskiTextPrimary)}
    }
}

@Composable private fun Message(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,onReload:(()->Unit)?) {
    Column(Modifier.fillMaxSize().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)){
        Icon(icon,null,tint=ChaskiTextMuted,modifier=Modifier.size(40.dp))
        Text(text,textAlign=TextAlign.Center,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        onReload?.let{TextButton(onClick=it){Text("Actualizar")}}
    }
}

private val previewIncident=PickupIncident("7f3a9c21"+"0".repeat(56),"Chicken House Centro","READY_FOR_PICKUP",1_800_000_000_000,1,5,true)
@androidx.compose.ui.tooling.preview.Preview(name="Admin 02 · incidencias",showBackground=true,widthDp=360,heightDp=800)
@Composable private fun IncidentsListPreview()=ChaskiTheme{PickupIncidentsContent(PickupIncidentsUiState(loading=false,incidents=listOf(previewIncident)))}
@androidx.compose.ui.tooling.preview.Preview(name="Admin 03 · vacío",showBackground=true,widthDp=360,heightDp=800)
@Composable private fun IncidentsEmptyPreview()=ChaskiTheme{PickupIncidentsContent(PickupIncidentsUiState(loading=false))}
@androidx.compose.ui.tooling.preview.Preview(name="Admin 07a · motivo inválido",showBackground=true,widthDp=360,heightDp=800)
@Composable private fun IncidentInvalidPreview()=ChaskiTheme{PickupIncidentsContent(PickupIncidentsUiState(loading=false,
    incidents=listOf(previewIncident),selected=previewIncident,error=PickupUnlockError.INVALID_REASON))}
@androidx.compose.ui.tooling.preview.Preview(name="Admin 06 · desbloqueado",showBackground=true,widthDp=360,heightDp=800)
@Composable private fun IncidentDonePreview()=ChaskiTheme{PickupIncidentsContent(PickupIncidentsUiState(loading=false,
    selected=previewIncident,unlockedCycle=2))}
