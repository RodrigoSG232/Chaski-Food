package com.chaskifood.app.feature.business.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedBg
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedFg
import com.chaskifood.app.ui.theme.ChaskiStatusObservedBg
import com.chaskifood.app.ui.theme.ChaskiStatusObservedFg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedBg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun StoreOperationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreOperationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    var pauseStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    val storeToPause = (uiState as? StoreOperationsUiState.Success)?.stores?.find { it.id == pauseStoreId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ChaskiDimens.ScreenPadding),
        ) {
            Text(
                text = "Panel Operativo de Locales",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = "Gestiona la disponibilidad en tiempo real y los horarios de atención de los locales a tu cargo.",
                fontSize = 14.sp,
                color = ChaskiTextMuted,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingLg))

            if (actionMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = actionMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearActionMessage() }) {
                            Text("OK")
                        }
                    }
                }
            }

            when (val state = uiState) {
                is StoreOperationsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = ChaskiPrimary)
                    }
                }
                is StoreOperationsUiState.NoStoresAssigned -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No tienes ningún local asignado para operar en este momento.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChaskiTextMuted,
                        )
                    }
                }
                is StoreOperationsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = viewModel::loadAssignedStores) { Text("REINTENTAR") }
                        }
                    }
                }
                is StoreOperationsUiState.Success -> {
                    state.stores.forEach { store ->
                        key(store.id) {
                            StoreOperationCard(
                                store = store,
                                onStatusChange = { newStatus ->
                                    if (newStatus == OperationalStatus.PAUSED) {
                                        pauseStoreId = store.id
                                    } else {
                                        viewModel.updateStatus(store.id, newStatus, null)
                                    }
                                },
                                onSaveHours = { newHours ->
                                    viewModel.updateHours(store.id, newHours)
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (storeToPause != null) {
        PauseReasonDialog(
            storeName = storeToPause?.name ?: "",
            onDismiss = { pauseStoreId = null },
            onConfirm = { reason ->
                storeToPause?.let { store ->
                    viewModel.updateStatus(store.id, OperationalStatus.PAUSED, reason)
                }
                pauseStoreId = null
            },
        )
    }
}

@Composable
private fun StoreOperationCard(
    store: BusinessStore,
    onStatusChange: (OperationalStatus) -> Unit,
    onSaveHours: (List<DayOperatingHours>) -> Unit,
) {
    val isAvailable = store.isAvailableForOrders()
    var expandedHours by rememberSaveable(store.id) { mutableStateOf(false) }
    var draftHours by rememberSaveable(store.id, stateSaver = OperatingHoursSaver) {
        mutableStateOf(store.operatingHours)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Store,
                        contentDescription = null,
                        tint = ChaskiPrimary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = store.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ChaskiTextPrimary,
                    )
                }

                val (badgeBg, badgeFg, badgeText) = when {
                    isAvailable -> Triple(ChaskiStatusApprovedBg, ChaskiStatusApprovedFg, "RECIBIENDO PEDIDOS")
                    store.operationalStatus == OperationalStatus.PAUSED -> Triple(ChaskiStatusObservedBg, ChaskiStatusObservedFg, "PAUSADO")
                    else -> Triple(ChaskiStatusRejectedBg, ChaskiStatusRejectedFg, "NO DISPONIBLE")
                }

                Box(
                    modifier = Modifier
                        .background(color = badgeBg, shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = store.address,
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                )
            }

            if (store.operationalStatus == OperationalStatus.PAUSED && !store.pauseReason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Motivo de pausa: ${store.pauseReason}",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Estado Operativo Manual:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChaskiTextMuted,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusButton(
                    text = "ABIERTO",
                    isSelected = store.operationalStatus == OperationalStatus.OPEN,
                    activeColor = Color(0xFF2E7D32),
                    onClick = { onStatusChange(OperationalStatus.OPEN) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    text = "PAUSAR",
                    isSelected = store.operationalStatus == OperationalStatus.PAUSED,
                    activeColor = Color(0xFFEF6C00),
                    onClick = { onStatusChange(OperationalStatus.PAUSED) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    text = "CERRADO",
                    isSelected = store.operationalStatus == OperationalStatus.CLOSED,
                    activeColor = Color(0xFFC62828),
                    onClick = { onStatusChange(OperationalStatus.CLOSED) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedHours = !expandedHours }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = ChaskiPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Configurar Horario Semanal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChaskiPrimary,
                    )
                }
                Icon(
                    imageVector = if (expandedHours) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = ChaskiPrimary,
                )
            }

            AnimatedVisibility(visible = expandedHours) {
                HoursEditor(
                    editableHours = draftHours,
                    onChange = { draftHours = it },
                    onSave = onSaveHours,
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) activeColor else Color(0xFFEEEEEE),
            contentColor = if (isSelected) Color.White else Color.DarkGray,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(36.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HoursEditor(
    editableHours: List<DayOperatingHours>,
    onChange: (List<DayOperatingHours>) -> Unit,
    onSave: (List<DayOperatingHours>) -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        editableHours.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.dayOfWeek.name.take(3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                )

                Switch(
                    checked = item.enabled,
                    onCheckedChange = { enabled ->
                        onChange(editableHours.mapIndexed { i, day -> if (i == index) item.copy(enabled = enabled) else day })
                    },
                )

                if (item.enabled) {
                    OutlinedTextField(
                        value = item.openTime,
                        onValueChange = { open ->
                            onChange(editableHours.mapIndexed { i, day -> if (i == index) item.copy(openTime = open) else day })
                        },
                        label = { Text("Abre", fontSize = 10.sp) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = item.closeTime,
                        onValueChange = { close ->
                            onChange(editableHours.mapIndexed { i, day -> if (i == index) item.copy(closeTime = close) else day })
                        },
                        label = { Text("Cierra", fontSize = 10.sp) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                } else {
                    Text(
                        text = "No atiende este día",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onSave(editableHours.toList()) },
            colors = ButtonDefaults.buttonColors(containerColor = ChaskiPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("GUARDAR HORARIO", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PauseReasonDialog(
    storeName: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Pausar Local: $storeName")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Indica el motivo de la pausa temporal (ej. Exceso de pedidos en cocina, sin insumos):",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo opcional") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason.ifBlank { null }) }) {
                Text("PAUSAR LOCAL")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        },
    )
}

private val OperatingHoursSaver = listSaver<List<DayOperatingHours>, Any>(
    save = { hours -> hours.flatMap { listOf(it.dayOfWeek.name, it.openTime, it.closeTime, it.enabled) } },
    restore = { values -> values.chunked(4).map {
        DayOperatingHours(DayOfWeekEnum.valueOf(it[0] as String), it[1] as String, it[2] as String, it[3] as Boolean)
    } },
)
