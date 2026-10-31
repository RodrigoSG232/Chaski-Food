package com.chaskifood.app.feature.orders.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chaskifood.app.feature.orders.domain.BuyerOrderSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentación de un estado de pedido (HU17). Los colores siguen los frames «HU17 /» aprobados en Pencil. */
internal data class BuyerOrderStatusUi(val label: String, val icon: ImageVector, val background: Color, val content: Color,
    val outlined: Boolean = false)

private val Neutral = Color(0xFFEEEEEE)
private val NeutralText = Color(0xFF424242)
private val DoneBg = Color(0xFFE8F5E9)
private val DoneText = Color(0xFF1B5E20)
private val StoppedBg = Color(0xFFFDECEC)
private val StoppedText = Color(0xFFB71C1C)

/** Un estado desconocido nunca rompe la pantalla: se muestra como «Estado en actualización» (N1). */
internal fun buyerOrderStatusUi(status: String, known: Boolean): BuyerOrderStatusUi = when (if (known) status else "") {
    "PENDING_CONFIRMATION" -> BuyerOrderStatusUi("Pendiente de confirmación", Icons.Filled.HourglassEmpty, Neutral, NeutralText)
    "PREPARING" -> BuyerOrderStatusUi("En preparación", Icons.Filled.SoupKitchen, Neutral, NeutralText)
    "READY_FOR_PICKUP" -> BuyerOrderStatusUi("Listo para recoger", Icons.Filled.Inventory2, Neutral, NeutralText)
    "IN_DELIVERY" -> BuyerOrderStatusUi("En camino", Icons.Filled.DeliveryDining, Neutral, NeutralText)
    "COMPLETED" -> BuyerOrderStatusUi("Entregado", Icons.Filled.Check, DoneBg, DoneText)
    "CANCELLED" -> BuyerOrderStatusUi("Cancelado", Icons.Filled.Close, StoppedBg, StoppedText)
    "REJECTED" -> BuyerOrderStatusUi("Rechazado", Icons.Filled.Block, StoppedBg, StoppedText)
    else -> BuyerOrderStatusUi("Estado en actualización", Icons.Filled.Refresh, Color.White, NeutralText, outlined = true)
}

/** Solo para mostrar: las consultas y la navegación usan siempre el ID completo de 64 caracteres. */
internal fun shortOrderId(orderId: String): String = orderId.take(8).uppercase(Locale.ROOT)

private val peru: Locale = Locale.forLanguageTag("es-PE")
private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", peru)
private val shortDateTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", peru)

internal fun formatOrderDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone)).replace(".", "")

internal fun formatOrderShortDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    shortDateTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone)).replace(".", "")

/** «2× Lomo saltado · 1× Chicha morada · +1 más» con los datos históricos del resumen. */
internal fun itemPreviewText(summary: BuyerOrderSummary): String {
    val shown = summary.itemPreview.joinToString(" · ") { "${it.quantity}× ${it.name}" }
    return if (summary.hiddenLineCount > 0) "$shown · +${summary.hiddenLineCount} más" else shown
}
