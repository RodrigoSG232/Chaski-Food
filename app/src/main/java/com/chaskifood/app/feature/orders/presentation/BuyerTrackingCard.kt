package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun BuyerTrackingCard(state: BuyerTrackingUiState, onRetry: () -> Unit) {
    val tracking = state.tracking
    var mapFailed by remember { mutableStateOf(false) }
    // Anchor to server time so changing the phone clock cannot make old positions look live.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var elapsed by remember(state.receivedAt) { mutableLongStateOf(state.receivedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L) }
    LaunchedEffect(tracking, state.receivedAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (tracking != null && !tracking.terminal) {
                elapsed = state.receivedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L
                delay(10_000)
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seguimiento de la entrega", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.loading && tracking == null) Text("Consultando seguimiento…")
            state.message?.let { Text(it); TextButton(onClick = onRetry) { Text("Reintentar seguimiento") } }
            if (tracking != null) {
                Text(when (tracking.stage) {
                    "SEARCHING_RIDER" -> "Buscando repartidor"
                    "ACCEPTED" -> "Repartidor asignado"
                    "AT_MERCHANT" -> "El repartidor llegó al local"
                    "ON_THE_WAY" -> "El repartidor está en camino"
                    "AT_CUSTOMER" -> "El repartidor llegó al destino"
                    else -> buyerOrderStatusUi(tracking.status, true, tracking.deliveryOutcome).label
                })
                tracking.riderName?.let { Text("Repartidor: " + it) }
                if (!tracking.terminal && !tracking.assigned) Text("Todavía no hay un repartidor asignado.")
                if (!tracking.terminal && tracking.status != "IN_DELIVERY")
                    Text("La ubicación estará disponible después del recojo.")
                if (tracking.status == "IN_DELIVERY") {
                    val now = tracking.serverTimeEpochMillis + elapsed
                    val eta = if (state.message == null) tracking.visibleEta(now) else null
                    Text(if (eta != null) "Llegada estimada: " + formatOrderDateTime(eta) else "ETA no disponible")
                    tracking.position?.let { point ->
                        Text("Última ubicación: " + formatOrderDateTime(point.recordedAtEpochMillis))
                        if (tracking.locationStale(now) || state.message != null)
                            Text("Ubicación sin actualizar. Se conserva la última posición conocida.")
                        DeliveryTrackingMap(point, Modifier.fillMaxWidth().height(230.dp).semantics {
                            contentDescription = "Mapa de la última ubicación del repartidor"
                        }, onFailure = { mapFailed = it })
                        if (mapFailed) Text("No se pudo cargar el mapa. La ubicación recibida se conserva.")
                        Text(String.format(Locale.ROOT, "Coordenadas: %.5f, %.5f · Precisión: %.0f m",
                            point.latitude, point.longitude, point.accuracyMeters), style = MaterialTheme.typography.bodySmall)
                    } ?: Text("Esperando la primera ubicación del repartidor.")
                }
            }
        }
    }
}
