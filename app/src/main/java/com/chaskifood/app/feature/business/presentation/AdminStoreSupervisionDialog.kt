package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreStatus

@Composable
fun AdminStoreSupervisionDialog(
    businessName: String,
    stores: ApiResult<List<BusinessStore>>?,
    actionState: UiState<Unit?>,
    onDismiss: () -> Unit,
    onChange: (BusinessStore, String?) -> Unit,
) {
    var selected by remember { mutableStateOf<BusinessStore?>(null) }
    var reason by remember { mutableStateOf("") }
    val busy = actionState is UiState.Loading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Locales de $businessName") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (actionState is UiState.Error) Text(actionState.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                when (stores) {
                    null -> CircularProgressIndicator()
                    is ApiResult.Failure -> Text(stores.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                    is ApiResult.Success -> {
                        if (stores.data.isEmpty()) Text("Este negocio no tiene locales.")
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(stores.data, key = { it.id }) { store ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(store.name, style = MaterialTheme.typography.titleSmall)
                                    Text(when (store.status) {
                                        StoreStatus.ACTIVE -> "Activo"
                                        StoreStatus.INACTIVE -> "Inactivo"
                                        StoreStatus.SUSPENDED -> "Suspendido"
                                    })
                                    store.suspensionReason?.let { Text(it) }
                                    TextButton(enabled = !busy, onClick = {
                                        if (store.status == StoreStatus.SUSPENDED) onChange(store, null)
                                        else { selected = store; reason = "" }
                                    }) { Text(if (store.status == StoreStatus.SUSPENDED) "REACTIVAR" else "SUSPENDER") }
                                }
                            }
                        }
                    }
                }
                selected?.let { store ->
                    // Mantener el motivo visible hasta que el servidor confirme el cambio.
                    val confirmed = (stores as? ApiResult.Success)?.data?.find { it.id == store.id }
                    LaunchedEffect(confirmed?.status) { if (confirmed?.status == StoreStatus.SUSPENDED) selected = null }
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, enabled = !busy,
                        label = { Text("Motivo para suspender ${store.name}") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && reason.isNotBlank(), onClick = { onChange(store, reason) }) {
                        Text("CONFIRMAR SUSPENSIÓN")
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("CERRAR") } },
    )
}
