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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import com.chaskifood.app.R
import com.chaskifood.app.feature.business.domain.closingDay
import com.chaskifood.app.feature.business.domain.timeInMinutes
import com.chaskifood.app.feature.business.domain.validOperatingHours
import java.util.Locale
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    val operations by viewModel.operations.collectAsState()
    val draftStates = rememberSaveableStateHolder()

    var pauseStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var pauseStoreName by rememberSaveable { mutableStateOf("") }
    var pauseSuccessRevision by rememberSaveable { mutableLongStateOf(0L) }
    val pauseOperation = operations[pauseStoreId] ?: StoreOperationState()
    LaunchedEffect(pauseStoreId, pauseOperation.successRevision) {
        if (pauseStoreId != null && pauseOperation.operation == StoreOperation.STATUS &&
            pauseOperation.successRevision > pauseSuccessRevision) pauseStoreId = null
    }

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
                        draftStates.SaveableStateProvider(store.id) {
                            StoreOperationCard(
                                store = store,
                                operationState = operations[store.id] ?: StoreOperationState(),
                                onStatusChange = { newStatus ->
                                    if (newStatus == OperationalStatus.PAUSED) {
                                        viewModel.clearOperationFeedback(store.id)
                                        pauseStoreName = store.name
                                        pauseSuccessRevision = operations[store.id]?.successRevision ?: 0L
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

    pauseStoreId?.let { storeId -> key(storeId) {
        PauseReasonDialog(
            storeName = pauseStoreName,
            operationState = pauseOperation,
            onDismiss = { pauseStoreId = null },
            onConfirm = { reason ->
                viewModel.updateStatus(storeId, OperationalStatus.PAUSED, reason)
            },
        )
    } }
}

@Composable
private fun StoreOperationCard(
    store: BusinessStore,
    operationState: StoreOperationState,
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
                    enabled = !operationState.saving && store.operationalStatus != OperationalStatus.OPEN,
                    isSelected = store.operationalStatus == OperationalStatus.OPEN,
                    activeColor = Color(0xFF2E7D32),
                    onClick = { onStatusChange(OperationalStatus.OPEN) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    text = "PAUSAR",
                    enabled = !operationState.saving,
                    isSelected = store.operationalStatus == OperationalStatus.PAUSED,
                    activeColor = Color(0xFFEF6C00),
                    onClick = { onStatusChange(OperationalStatus.PAUSED) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    text = "CERRADO",
                    enabled = !operationState.saving && store.operationalStatus != OperationalStatus.CLOSED,
                    isSelected = store.operationalStatus == OperationalStatus.CLOSED,
                    activeColor = Color(0xFFC62828),
                    onClick = { onStatusChange(OperationalStatus.CLOSED) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (operationState.operation == StoreOperation.STATUS || !expandedHours) {
                OperationFeedback(operationState)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !operationState.saving) { expandedHours = !expandedHours }
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
                    enabled = !operationState.saving,
                    operationState = operationState.takeIf { it.operation == StoreOperation.HOURS },
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    text: String,
    enabled: Boolean,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) activeColor else Color(0xFFEEEEEE),
            contentColor = if (isSelected) Color.White else Color.DarkGray,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun weekdayLabel(day: DayOfWeekEnum): String = stringResource(when (day) {
    DayOfWeekEnum.MONDAY -> R.string.weekday_monday
    DayOfWeekEnum.TUESDAY -> R.string.weekday_tuesday
    DayOfWeekEnum.WEDNESDAY -> R.string.weekday_wednesday
    DayOfWeekEnum.THURSDAY -> R.string.weekday_thursday
    DayOfWeekEnum.FRIDAY -> R.string.weekday_friday
    DayOfWeekEnum.SATURDAY -> R.string.weekday_saturday
    DayOfWeekEnum.SUNDAY -> R.string.weekday_sunday
})

@Composable
internal fun HoursEditor(
    editableHours: List<DayOperatingHours>,
    onChange: (List<DayOperatingHours>) -> Unit,
    onSave: (List<DayOperatingHours>) -> Unit,
    enabled: Boolean,
    operationState: StoreOperationState?,
) {
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var selectingOpening by rememberSaveable { mutableStateOf(true) }
    val selected = editableHours.singleOrNull { it.dayOfWeek.name == selectedDay }
    if (selected != null && enabled && selected.enabled) {
        key(selectedDay, selectingOpening) {
            HoursTimeDialog(
                day = weekdayLabel(selected.dayOfWeek),
                opening = selectingOpening,
                initialTime = if (selectingOpening) selected.openTime else selected.closeTime,
                onDismiss = { selectedDay = null },
                onConfirm = { time ->
                    onChange(editableHours.map { day ->
                        if (day.dayOfWeek != selected.dayOfWeek) day
                        else if (selectingOpening) day.copy(openTime = time) else day.copy(closeTime = time)
                    })
                    selectedDay = null
                },
            )
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.hours_format_hint), style = MaterialTheme.typography.bodyMedium)
        editableHours.forEach { item ->
            val dayLabel = weekdayLabel(item.dayOfWeek)
            val switchLabel = stringResource(R.string.hours_day_enabled, dayLabel)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = item.enabled, enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = switchLabel },
                        onCheckedChange = { checked ->
                            onChange(editableHours.map { if (it.dayOfWeek == item.dayOfWeek) it.copy(enabled = checked) else it })
                        })
                }
                if (item.enabled) {
                    listOf(true, false).forEach { opening ->
                        val label = stringResource(if (opening) R.string.hours_opening else R.string.hours_closing)
                        val time = if (opening) item.openTime else item.closeTime
                        val actionLabel = stringResource(R.string.hours_choose_time, label, dayLabel) + ": " + time
                        OutlinedButton(enabled = enabled, onClick = {
                            selectingOpening = opening
                            selectedDay = item.dayOfWeek.name
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { contentDescription = actionLabel }) {
                            Text("$label: $time", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    val closingDay = item.closingDay()
                    if (closingDay != null && closingDay != item.dayOfWeek) {
                        Text(stringResource(R.string.hours_next_day, weekdayLabel(closingDay), item.closeTime),
                            style = MaterialTheme.typography.bodyMedium)
                    } else if (item.openTime == item.closeTime) {
                        Text(stringResource(R.string.hours_same_time_error), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(stringResource(R.string.hours_day_closed), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = { onSave(editableHours.toList()) }, enabled = enabled && validOperatingHours(editableHours),
            colors = ButtonDefaults.buttonColors(containerColor = ChaskiPrimary), shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("GUARDAR HORARIO", fontWeight = FontWeight.Bold)
        }
        operationState?.let { OperationFeedback(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoursTimeDialog(
    day: String,
    opening: Boolean,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val minutes = timeInMinutes(initialTime) ?: if (opening) 8 * 60 else 22 * 60
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    val label = stringResource(if (opening) R.string.hours_opening else R.string.hours_closing)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hours_choose_time, label, day)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute)) }) {
            Text(stringResource(R.string.hours_confirm))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hours_cancel)) } },
    )
}

@Composable
private fun PauseReasonDialog(
    storeName: String,
    operationState: StoreOperationState,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!operationState.saving) onDismiss() },
        title = {
            Text(text = "Pausar Local: $storeName")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Indica el motivo de la pausa temporal (ej. Exceso de pedidos en cocina, sin insumos):",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                )
                OutlinedTextField(
                    value = reason,
                    enabled = !operationState.saving,
                    onValueChange = { reason = it },
                    label = { Text("Motivo opcional") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OperationFeedback(operationState)
            }
        },
        confirmButton = {
            Button(enabled = !operationState.saving, onClick = { onConfirm(reason.ifBlank { null }) }) {
                Text("PAUSAR LOCAL")
            }
        },
        dismissButton = {
            TextButton(enabled = !operationState.saving, onClick = onDismiss) {
                Text("CANCELAR")
            }
        },
    )
}

@Composable
private fun OperationFeedback(state: StoreOperationState) {
    if (!state.saving && state.error == null && state.message == null) return
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.saving) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = ChaskiPrimary)
            Text(if (state.operation == StoreOperation.HOURS) "Guardando horario…" else "Guardando estado del local…",
                style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private val OperatingHoursSaver = listSaver<List<DayOperatingHours>, Any>(
    save = { hours -> hours.flatMap { listOf(it.dayOfWeek.name, it.openTime, it.closeTime, it.enabled) } },
    restore = { values -> values.chunked(4).map {
        DayOperatingHours(DayOfWeekEnum.valueOf(it[0] as String), it[1] as String, it[2] as String, it[3] as Boolean)
    } },
)
