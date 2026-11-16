package com.chaskifood.app.feature.orders.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.orders.domain.CollectionReconciliationRules.EVIDENCE_MAX
import com.chaskifood.app.feature.orders.domain.CollectionReconciliationRules.formatSoles
import com.chaskifood.app.ui.components.*
import com.chaskifood.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/** Pencil HU22 / Conc 02–13: admin reconciliation of Rider collections. Never shows buyer data, actors or other evidence. */
@Composable
fun CollectionReviewsScreen(onBack: () -> Unit, viewModel: CollectionReviewsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selected != null) { viewModel.backToList() }
    CollectionReviewsContent(state, CollectionReviewsActions(onBack, viewModel::load, viewModel::select, viewModel::backToList,
        viewModel::setOutcome, viewModel::setAmount, viewModel::setEvidence, viewModel::setRefundEvidence, viewModel::requestConfirm,
        viewModel::confirm, viewModel::cancelConfirm, viewModel::retry))
}

internal data class CollectionReviewsActions(val onBack: () -> Unit = {}, val onReload: () -> Unit = {}, val onSelect: (String) -> Unit = {},
    val onBackToList: () -> Unit = {}, val onOutcome: (CollectionOutcome) -> Unit = {}, val onAmount: (String) -> Unit = {},
    val onEvidence: (String) -> Unit = {}, val onRefundEvidence: (String) -> Unit = {}, val onContinue: () -> Unit = {},
    val onConfirm: () -> Unit = {}, val onCancelConfirm: () -> Unit = {}, val onRetry: () -> Unit = {})

private const val PRIVACY_HINT = "Describe la evidencia sin incluir contraseñas, datos bancarios sensibles ni información personal innecesaria."

@Composable
internal fun CollectionReviewsContent(state: CollectionReviewsUiState, actions: CollectionReviewsActions = CollectionReviewsActions()) {
    val selected = state.selected
    val title = when {
        selected == null -> "Revisiones de cobro"
        selected.kind == CollectionReviewKind.EXCESS_PENDING_REFUND -> "Devolución de excedente"
        else -> "Revisión de cobro"
    }
    Scaffold(containerColor = ChaskiBackground, topBar = { ChaskiAppBar(title = title,
        onBackClick = if (selected == null) actions.onBack else actions.onBackToList) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.accessDenied -> Message(Icons.Filled.GppBad, "Tu cuenta ya no tiene permiso de administración.", null)
                selected != null && state.done != null -> ResultView(selected, state.done, actions.onBackToList)
                selected != null && selected.kind == CollectionReviewKind.EXCESS_PENDING_REFUND -> RefundForm(selected, state, actions)
                selected != null -> ResolveFormView(selected, state, actions)
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.loadError != null -> Message(Icons.Filled.WifiOff, state.loadError, actions.onReload)
                state.reviews.isEmpty() -> Message(Icons.Filled.CheckCircle, "Sin revisiones pendientes. No hay cobros con diferencias " +
                    "ni excedentes por devolver; los cobros exactos se registran automáticamente.", actions.onReload)
                else -> ReviewsList(state, actions)
            }
        }
    }
}

@Composable private fun ReviewsList(state: CollectionReviewsUiState, actions: CollectionReviewsActions) {
    LazyColumn(contentPadding = PaddingValues(ChaskiDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Cobros contra entrega cuyo importe declarado por Chaski Rider no coincide con el total del pedido, y " +
            "excedentes por devolver. Solo se muestran los datos necesarios para revisarlos.",
            style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted) }
        // Conc 02: the backend returns at most 50 per type; never present a truncated list as complete.
        if (state.truncated) item { Notice(Icons.Filled.Info, "Hay más revisiones de las que se muestran. Resuelve algunas y " +
            "actualiza la lista para ver el resto.", ChaskiStatusPendingBg) }
        items(state.reviews, key = { "${it.kind}-${it.orderId}" }) { ReviewCard(it) { actions.onSelect(it.orderId) } }
    }
}

private fun dateTime(millis: Long) = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es-PE")).apply {
    timeZone = TimeZone.getTimeZone("America/Lima") }.format(Date(millis))

private fun methodText(method: String) = when (method) { "CASH" -> "Efectivo"; "YAPE" -> "Yape"; "PLIN" -> "Plin"; else -> method }

@Composable private fun ReviewCard(review: CollectionReview, onClick: () -> Unit) {
    val refund = review.kind == CollectionReviewKind.EXCESS_PENDING_REFUND
    Surface(Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir revisión", onClick = onClick), shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pedido #${review.shortId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (refund) ChaskiStatusPill("Devolución pendiente", ChaskiAdminCardBg, ChaskiAdminAccent)
                else ChaskiStatusPill("Por revisar", ChaskiStatusPendingBg, ChaskiStatusObservedFg)
            }
            Text(review.storeName ?: "Local", style = MaterialTheme.typography.bodyMedium)
            Text(if (refund) "Excedente ${formatSoles(review.resolution!!.excessMinor)} por devolver"
                else "Esperado ${formatSoles(review.expectedMinor)} · Declarado ${formatSoles(review.declaredMinor)}",
                style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
            Text(listOfNotNull(if (refund) "Cobro completo conciliado" else null, methodText(review.method),
                review.openedAtEpochMillis?.let { "entregado ${dateTime(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
        }
    }
}

@Composable private fun KeyValue(key: String, value: String, valueColor: androidx.compose.ui.graphics.Color = ChaskiTextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable private fun SummaryCard(review: CollectionReview) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyValue("Pedido", "#${review.shortId}")
            KeyValue("Local", review.storeName ?: "—")
            KeyValue(if (review.kind == CollectionReviewKind.OPEN_REVIEW) "Método" else "Método original", methodText(review.method))
            review.openedAtEpochMillis?.let { KeyValue("Entregado", dateTime(it)) }
            if (review.kind == CollectionReviewKind.OPEN_REVIEW) {
                KeyValue("Total esperado (Food)", formatSoles(review.expectedMinor))
                KeyValue("Declarado por Rider", formatSoles(review.declaredMinor), ChaskiStatusObservedFg)
                Text("La declaración original de Chaski Rider se conserva sin cambios.", style = MaterialTheme.typography.bodySmall,
                    color = ChaskiTextMuted)
            } else {
                KeyValue("Excedente", formatSoles(review.resolution!!.excessMinor), ChaskiAdminAccent)
                KeyValue("Estado", "Pendiente de devolución")
            }
        }
    }
}

@Composable private fun OutcomeOption(selected: Boolean, enabled: Boolean, title: String, description: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium, color = if (selected) ChaskiAdminCardBg else ChaskiBackground,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ChaskiAdminAccent else ChaskiBorder)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted) }
        }
    }
}

@Composable private fun ResolveFormView(review: CollectionReview, state: CollectionReviewsUiState, actions: CollectionReviewsActions) {
    val form = state.form
    val frozen = state.phase != ReconciliationPhase.EDITING || state.error != null && state.error != ReconciliationError.REJECTED
    val amountError = form.amountError(review.expectedMinor)
    if (state.phase != ReconciliationPhase.EDITING) state.confirmation?.let { ConfirmResolveDialog(review, it, state, actions) }
    LazyColumn(contentPadding = PaddingValues(ChaskiDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SummaryCard(review) }
        errorItem(state.error, state.phase)
        item { Text("Resultado de la revisión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        item { Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutcomeOption(form.outcome == CollectionOutcome.FULLY_COLLECTED, !frozen, "Cobro completo",
                "El importe confirmado cubre el total. El pago quedará registrado.") { actions.onOutcome(CollectionOutcome.FULLY_COLLECTED) }
            OutcomeOption(form.outcome == CollectionOutcome.NOT_FULLY_COLLECTED, !frozen, "Cobro incompleto",
                "El importe confirmado es menor. El pago seguirá pendiente.") { actions.onOutcome(CollectionOutcome.NOT_FULLY_COLLECTED) }
        } }
        item { OutlinedTextField(form.amount, actions.onAmount, Modifier.fillMaxWidth(), enabled = !frozen,
            label = { Text("Importe confirmado (S/)") }, placeholder = { Text("Ej. 87.00") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amountError != null,
            supportingText = { Text(amountHelp(amountError, review.expectedMinor), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }) }
        item { EvidenceField(form.evidence, actions.onEvidence, !frozen, "Describe cómo verificaste el importe cobrado") }
        item { ChaskiButton(if (state.phase == ReconciliationPhase.SENDING) "Enviando…" else "Revisar y resolver",
            onClick = actions.onContinue, enabled = !frozen && form.complete(review.expectedMinor)) }
        if (state.error in setOf(ReconciliationError.REVIEW_NOT_OPEN, ReconciliationError.NOT_ELIGIBLE, ReconciliationError.CONFLICT))
            item { ChaskiButton("Volver a revisiones", onClick = actions.onBackToList, variant = ChaskiButtonVariant.Outlined) }
    }
}

private fun amountHelp(error: AmountError?, expectedMinor: Long) = when (error) {
    AmountError.INVALID -> "Escribe un importe válido en soles, por ejemplo 87.00."
    AmountError.BELOW_TOTAL -> "Para «Cobro completo» el importe debe ser al menos ${formatSoles(expectedMinor)}. Si se cobró menos, elige «Cobro incompleto»."
    AmountError.COVERS_TOTAL -> "El importe cubre el total (${formatSoles(expectedMinor)}): elige «Cobro completo»."
    null -> "Introduce el importe cobrado según la evidencia. Esperado: ${formatSoles(expectedMinor)}."
}

@Composable private fun EvidenceField(value: String, onChange: (String) -> Unit, enabled: Boolean, placeholder: String) {
    val length = value.trim().length
    val invalid = value.isNotEmpty() && !CollectionReconciliationRules.evidenceValid(value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, minLines = 3, isError = invalid,
            label = { Text("Evidencia (obligatoria)") }, placeholder = { Text(placeholder) })
        Text(if (invalid) "La evidencia debe tener entre 5 y $EVIDENCE_MAX caracteres (sin contar espacios al inicio y al final)."
            else "$PRIVACY_HINT $length/$EVIDENCE_MAX", style = MaterialTheme.typography.bodySmall,
            color = if (invalid) ChaskiStatusRejectedFg else ChaskiTextMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.errorItem(error: ReconciliationError?, phase: ReconciliationPhase) {
    val text = when (error) {
        ReconciliationError.REJECTED -> "El servidor rechazó los datos. Revisa que el importe corresponda al resultado y que la evidencia sea válida."
        ReconciliationError.REVIEW_NOT_OPEN -> "Otro administrador ya resolvió esta revisión. Vuelve a la lista para ver su estado."
        ReconciliationError.NOT_ELIGIBLE -> "Este pedido ya no tiene una revisión de cobro pendiente. Vuelve a la lista para actualizarla."
        ReconciliationError.EXCESS_NOT_PENDING -> "Este excedente ya no está pendiente de devolución (ya se registró o no existe)."
        ReconciliationError.CONFLICT -> "Esta operación ya se registró con otros datos. Vuelve a abrir la revisión para ver su estado actual."
        ReconciliationError.PERMISSION -> "Tu cuenta ya no tiene permiso de administración."
        ReconciliationError.CONNECTION -> if (phase == ReconciliationPhase.EDITING) "No pudimos confirmar la operación. Vuelve a la lista " +
            "para ver el estado actual." else null
        null -> null
    } ?: return
    item { Notice(if (error == ReconciliationError.CONNECTION) Icons.Filled.WifiOff else Icons.Filled.Error, text, ChaskiStatusRejectedBg) }
}

@Composable private fun ConfirmResolveDialog(review: CollectionReview, c: ResolveConfirmation, state: CollectionReviewsUiState,
    actions: CollectionReviewsActions) {
    val sending = state.phase == ReconciliationPhase.SENDING
    val full = c.outcome == CollectionOutcome.FULLY_COLLECTED
    AlertDialog(onDismissRequest = { if (!sending && !state.uncertain) actions.onCancelConfirm() },
        title = { Text("¿Registrar cobro ${if (full) "completo" else "incompleto"} del pedido #${review.shortId}?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyValue("Resultado", if (full) "Cobro completo" else "Cobro incompleto", if (full) ChaskiStatusApprovedFg else ChaskiStatusObservedFg)
            KeyValue("Total esperado", formatSoles(c.expectedMinor))
            KeyValue("Declarado por Rider", formatSoles(c.declaredMinor))
            KeyValue("Confirmado", formatSoles(c.confirmedMinor))
            if (!full) KeyValue("Saldo pendiente", formatSoles(c.shortfallMinor), ChaskiStatusRejectedFg)
            if (full && c.excessMinor > 0) KeyValue("Excedente por devolver", formatSoles(c.excessMinor), ChaskiAdminAccent)
            Text(if (full) "El pago quedará registrado por el total del pedido." else "El pago seguirá pendiente: no se registra un pago parcial.")
            Text("La decisión queda registrada con tu cuenta, la evidencia y la fecha, y no se puede cambiar desde la app.",
                style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
            DialogStatus(sending, state.uncertain)
        } },
        confirmButton = { DialogConfirm(sending, state.uncertain, "Confirmar", actions) },
        dismissButton = { if (!state.uncertain) TextButton(onClick = actions.onCancelConfirm, enabled = !sending) { Text("Volver") }
            else TextButton(onClick = actions.onBackToList, enabled = !sending) { Text("Ver estado actual") } })
}

@Composable private fun DialogStatus(sending: Boolean, uncertain: Boolean) {
    if (uncertain) Text("No pudimos confirmar la operación. Es posible que sí se haya registrado. Reintentar consulta la misma " +
        "operación y no la duplica.", color = ChaskiStatusRejectedFg, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable private fun DialogConfirm(sending: Boolean, uncertain: Boolean, label: String, actions: CollectionReviewsActions) {
    TextButton(onClick = if (uncertain) actions.onRetry else actions.onConfirm, enabled = !sending) {
        Text(when { sending -> "Enviando…"; uncertain -> "Reintentar"; else -> label })
    }
}

@Composable private fun RefundForm(review: CollectionReview, state: CollectionReviewsUiState, actions: CollectionReviewsActions) {
    val excess = review.resolution!!.excessMinor
    val frozen = state.phase != ReconciliationPhase.EDITING || state.error != null && state.error != ReconciliationError.REJECTED
    val sending = state.phase == ReconciliationPhase.SENDING
    if (state.phase != ReconciliationPhase.EDITING) AlertDialog(onDismissRequest = { if (!sending && !state.uncertain) actions.onCancelConfirm() },
        title = { Text("¿Registrar la devolución de ${formatSoles(excess)} del pedido #${review.shortId}?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Confirmas que el excedente completo ya se devolvió fuera de Chaski Food. La app no envía dinero.")
            Text("Queda registrado con tu cuenta, la evidencia y la fecha.", style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
            DialogStatus(sending, state.uncertain)
        } },
        confirmButton = { DialogConfirm(sending, state.uncertain, "Registrar", actions) },
        dismissButton = { if (!state.uncertain) TextButton(onClick = actions.onCancelConfirm, enabled = !sending) { Text("Volver") }
            else TextButton(onClick = actions.onBackToList, enabled = !sending) { Text("Ver estado actual") } })
    LazyColumn(contentPadding = PaddingValues(ChaskiDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SummaryCard(review) }
        item { Notice(Icons.Filled.Info, "Esta acción solo registra una devolución ya hecha fuera de la app. No envía dinero ni opera " +
            "con Yape, Plin ni bancos.", ChaskiAdminCardBg) }
        errorItem(state.error, state.phase)
        item { KeyValue("Importe devuelto", "${formatSoles(excess)} (excedente completo)") }
        item { Text("No se admiten devoluciones parciales del excedente.", style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted) }
        item { EvidenceField(state.refundEvidence, actions.onRefundEvidence, !frozen, "Describe cómo se devolvió el excedente") }
        item { ChaskiButton(if (sending) "Enviando…" else "Registrar devolución", onClick = actions.onContinue,
            enabled = !frozen && CollectionReconciliationRules.evidenceValid(state.refundEvidence)) }
        if (state.error == ReconciliationError.EXCESS_NOT_PENDING || state.error == ReconciliationError.CONFLICT)
            item { ChaskiButton("Volver a revisiones", onClick = actions.onBackToList, variant = ChaskiButtonVariant.Outlined) }
    }
}

@Composable private fun ResultView(review: CollectionReview, result: ReconciliationResult, onBack: () -> Unit) {
    val (icon, color, title, description, rows) = resultUi(review, result)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Icon(icon, null, tint = color, modifier = Modifier.size(48.dp)) }
        item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        item { Text(description, textAlign = TextAlign.Center, color = ChaskiTextMuted) }
        item { Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { rows.forEach { (k, v, c) -> KeyValue(k, v, c) } }
        } }
        item { ChaskiButton("Volver a revisiones", onClick = onBack, variant = ChaskiButtonVariant.Outlined) }
    }
}

private data class ResultUi(val icon: ImageVector, val color: androidx.compose.ui.graphics.Color, val title: String, val description: String,
    val rows: List<Triple<String, String, androidx.compose.ui.graphics.Color>>)

private fun resultUi(review: CollectionReview, result: ReconciliationResult): ResultUi {
    val id = "Pedido #${review.shortId}."
    val replay = if ((result as? ReconciliationResult.Resolved)?.replayed == true || (result as? ReconciliationResult.Refunded)?.replayed == true)
        " Esta operación ya estaba registrada; se muestra su resultado original." else ""
    return when (result) {
        is ReconciliationResult.Refunded -> ResultUi(Icons.Filled.CheckCircle, ChaskiStatusApprovedFg, "Devolución registrada",
            "$id Se registró la devolución del excedente con tu cuenta, la evidencia y la fecha. La app no movió dinero.$replay",
            listOf(Triple("Excedente", formatSoles(result.excessMinor), ChaskiTextPrimary), Triple("Estado", "Devuelto", ChaskiStatusApprovedFg)))
        is ReconciliationResult.Resolved -> {
            val r = result.resolution
            val base = listOf(Triple("Total esperado", formatSoles(result.expectedMinor), ChaskiTextPrimary),
                Triple("Declarado por Rider", formatSoles(result.declaredMinor), ChaskiTextPrimary),
                Triple("Confirmado", formatSoles(r.confirmedMinor), ChaskiTextPrimary))
            when {
                r.outcome == CollectionOutcome.NOT_FULLY_COLLECTED -> ResultUi(Icons.Filled.Error, ChaskiStatusObservedFg, "Cobro incompleto registrado",
                    "$id El pago sigue pendiente: no se registra un pago parcial ni cuenta como venta. El saldo queda anotado para una gestión posterior.$replay",
                    base + Triple("Saldo pendiente", formatSoles(r.shortfallMinor), ChaskiStatusRejectedFg))
                r.excessStatus == ExcessStatus.PENDING_REFUND -> ResultUi(Icons.Filled.CheckCircle, ChaskiStatusApprovedFg,
                    "Cobro completo · excedente por devolver", "$id El pago quedó registrado por el total del pedido. El excedente no suma " +
                        "ventas y sigue pendiente de devolución; podrás registrarla desde la lista.$replay",
                    base + Triple("Excedente", "${formatSoles(r.excessMinor)} · pendiente", ChaskiAdminAccent))
                else -> ResultUi(Icons.Filled.CheckCircle, ChaskiStatusApprovedFg, "Cobro completo registrado",
                    "$id El pago quedó registrado y el pedido cuenta como venta una sola vez, por el subtotal de productos.$replay",
                    base + Triple("Excedente", formatSoles(r.excessMinor), ChaskiTextPrimary))
            }
        }
        else -> error("Only confirmed results are shown")
    }
}

@Composable private fun Notice(icon: ImageVector, text: String, background: androidx.compose.ui.graphics.Color) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, color = background, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = ChaskiTextPrimary); Text(text, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextPrimary) }
    }
}

@Composable private fun Message(icon: ImageVector, text: String, onReload: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Icon(icon, null, tint = ChaskiTextMuted, modifier = Modifier.size(40.dp))
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        onReload?.let { TextButton(onClick = it) { Text("Actualizar") } }
    }
}

// ---- Previews (synthetic data only)
private val previewOpen = CollectionReview("5c82d1a0" + "0".repeat(56), CollectionReviewKind.OPEN_REVIEW, "Local ficticio Centro", 8700, 4000,
    "CASH", 1_800_000_000_000, null)
private val previewExcess = CollectionReview("3a7f0e26" + "0".repeat(56), CollectionReviewKind.EXCESS_PENDING_REFUND, "Local ficticio Este",
    8700, 9000, "YAPE", 1_800_000_000_000, CollectionResolution(CollectionOutcome.FULLY_COLLECTED, 9000, 0, 300, ExcessStatus.PENDING_REFUND))
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 02 · listado truncado", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ListPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false,
    reviews = listOf(previewOpen, previewExcess), truncated = true)) }
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 03 · vacío", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun EmptyPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false)) }
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 04a · inicial", showBackground = true, widthDp = 360, heightDp = 900)
@Composable private fun InitialPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false, selected = previewOpen)) }
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 06 · confirmar incompleto", showBackground = true, widthDp = 360, heightDp = 900)
@Composable private fun ConfirmPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false, selected = previewOpen,
    form = ResolveForm(CollectionOutcome.NOT_FULLY_COLLECTED, "40.00", "Repartidor confirma el pago parcial del cliente."),
    phase = ReconciliationPhase.CONFIRMING)) }
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 10 · excedente", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ExcessResultPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false, selected = previewOpen,
    done = ReconciliationResult.Resolved(CollectionResolution(CollectionOutcome.FULLY_COLLECTED, 9000, 0, 300, ExcessStatus.PENDING_REFUND),
        8700, 9000, false))) }
@androidx.compose.ui.tooling.preview.Preview(name = "Conc 11 · devolución", showBackground = true, widthDp = 360, heightDp = 900)
@Composable private fun RefundPreview() = ChaskiTheme { CollectionReviewsContent(CollectionReviewsUiState(loading = false, selected = previewExcess)) }
