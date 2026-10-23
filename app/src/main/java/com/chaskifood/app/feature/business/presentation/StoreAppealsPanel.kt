package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedBg
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import androidx.compose.ui.text.font.FontWeight
import java.text.DateFormat
import java.util.Date

@Composable
fun StoreAppealsPanel(
    admin: Boolean, stores: List<BusinessStore> = emptyList(),
    initialStoreId: String? = null, onDismiss: () -> Unit,
    viewModel: StoreAppealsViewModel = hiltViewModel(),
) {
    val appeals by viewModel.appeals.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var accept by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val appealList = (appeals as? ApiResult.Success)?.data.orEmpty()
    LaunchedEffect(initialStoreId) {
        if (!admin && initialStoreId != null) {
            targetId = initialStoreId
            draft = ""
        }
    }
    DisposableEffect(admin, viewModel) {
        viewModel.clearMessage()
        viewModel.load(admin)
        onDispose { viewModel.stopObserving() }
    }
    LaunchedEffect(appeals, initialStoreId) {
        val store = stores.firstOrNull { it.id == initialStoreId }
        if (store != null && appealList.any {
                it.storeId == store.id && it.suspensionId == store.suspensionId
            }) targetId = null
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = ChaskiBackground) {
            Column(Modifier.fillMaxSize()) {
                AuthAppBar(onBackClick = { if (!busy) onDismiss() })
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Text(if (admin) "Apelaciones de locales" else "Suspensiones y apelaciones",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ChaskiTextPrimary)
                Text(if (admin) "Revisa cada solicitud y responde al dueño."
                    else "Revisa el motivo de suspensión y presenta tu apelación.",
                    color = ChaskiTextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message?.startsWith("Apelación enviada") == true) {
                    AppealSentBanner(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                } else message?.let { Text(it, modifier = Modifier.padding(bottom = 12.dp)) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!admin) {
                        item { AppealSectionTitle("Locales suspendidos") }
                        val suspended = stores.filter { it.status == StoreStatus.SUSPENDED }
                        if (suspended.isEmpty()) item { Text("No tienes locales suspendidos.") }
                        items(suspended, key = { "store_" + it.id }) { store ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(store.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Motivo: ${store.suspensionReason?.takeIf { it.isNotBlank() } ?: "No especificado"}")
                                Text(suspensionDate(store.suspendedAt), style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
                                val currentAppeal = appealList.firstOrNull {
                                    it.storeId == store.id && it.suspensionId == store.suspensionId
                                }
                                when (currentAppeal?.status) {
                                    "PENDING" -> AppealSentBanner()
                                    "REJECTED" -> Text("Apelación rechazada: ${currentAppeal.response}", color = MaterialTheme.colorScheme.error)
                                    "ACCEPTED" -> Text("Apelación aceptada", color = ChaskiStatusApprovedFg)
                                    else -> Button(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                                        onClick = { viewModel.clearMessage(); targetId = store.id; draft = "" }) {
                                        Text("APELAR SUSPENSIÓN")
                                    }
                                }
                            } }
                        }
                    }
                    item { AppealSectionTitle("Historial de apelaciones") }
                    when (val result = appeals) {
                        null -> item { CircularProgressIndicator() }
                        is ApiResult.Failure -> item { Text(result.message.orEmpty()); TextButton(onClick = { viewModel.load(admin) }) { Text("REINTENTAR") } }
                        is ApiResult.Success -> {
                            if (result.data.isEmpty()) item { Text("Todavía no hay apelaciones.") }
                            val orderedAppeals = if (admin) result.data.sortedWith(
                                compareBy<StoreAppeal> { it.status != "PENDING" }.thenByDescending { it.createdAt },
                            ) else result.data
                            items(orderedAppeals, key = { "appeal_" + it.id }) { appeal ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(appeal.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(when (appeal.status) { "PENDING" -> "Pendiente de revisión"; "ACCEPTED" -> "Aceptada"; "REJECTED" -> "Rechazada"; else -> "Estado desconocido" })
                                    Text(eventDate(appeal.createdAt), style = MaterialTheme.typography.bodySmall)
                                    Text("Motivo: ${appeal.suspensionReason}")
                                    Text("Explicación: ${appeal.explanation}")
                                    if (appeal.response.isNotBlank()) Text("Respuesta: ${appeal.response}")
                                    if (admin && appeal.status == "PENDING") {
                                        Button(enabled = !busy, onClick = { viewModel.clearMessage(); targetId = appeal.id; accept = true; draft = "" }) { Text("ACEPTAR Y REACTIVAR") }
                                        OutlinedButton(enabled = !busy, onClick = { viewModel.clearMessage(); targetId = appeal.id; accept = false; draft = "" }) { Text("RECHAZAR") }
                                    }
                                } }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    targetId?.let { id ->
        AlertDialog(onDismissRequest = { if (!busy) targetId = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text(if (!admin) "Apelar suspensión" else if (accept) "Aceptar apelación" else "Rechazar apelación") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!admin) stores.firstOrNull { it.id == id }?.let { store ->
                    Text(store.name, fontWeight = FontWeight.Bold)
                    Text("Motivo: ${store.suspensionReason?.takeIf { it.isNotBlank() } ?: "No especificado"}")
                    Text(suspensionDate(store.suspendedAt), color = ChaskiTextMuted)
                }
                Text(if (!admin) "Explica qué corregiste. Enviar la apelación no reactiva el local automáticamente." else "La respuesta quedará en el historial y se notificará al dueño.")
                OutlinedTextField(value = draft, onValueChange = { if (it.length <= 2000) draft = it }, enabled = !busy,
                    label = { Text(if (admin) "Respuesta" else "Explicación") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("${draft.trim().length}/2000 · mínimo 10 caracteres")
                message?.let { Text(it) }
            } },
            confirmButton = { Button(enabled = !busy && draft.trim().length in 10..2000, onClick = {
                if (admin) viewModel.resolve(id, accept, draft) { targetId = null; draft = "" }
                else viewModel.submit(id, draft) { targetId = null; draft = "" }
            }) { Text(if (busy) "GUARDANDO…" else "ENVIAR") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { targetId = null }) { Text("CANCELAR") } })
    }
}

@Composable
private fun AppealSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        color = ChaskiTextPrimary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AppealSentBanner(modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ChaskiStatusApprovedBg)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                tint = ChaskiStatusApprovedFg, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Apelación enviada", fontWeight = FontWeight.Bold, color = ChaskiStatusApprovedFg)
                Text("Pendiente de revisión. El local sigue suspendido por ahora.",
                    style = MaterialTheme.typography.bodySmall, color = ChaskiStatusApprovedFg)
            }
        }
    }
}

private fun eventDate(time: Long): String = if (time == 0L) "Sincronizando fecha…"
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

private fun suspensionDate(time: Long?): String = if (time == null) "Fecha de suspensión no disponible"
    else "Suspendido el ${eventDate(time)}"
