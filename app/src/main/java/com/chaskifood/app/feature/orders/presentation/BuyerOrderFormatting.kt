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
import com.chaskifood.app.feature.orders.domain.OrderPaymentStatus
import com.chaskifood.app.feature.orders.domain.StoreOrderRejectionReason
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

/**
 * Texto del pago en efectivo (HU18). Un pago registrado se muestra tal cual; un pedido cancelado o rechazado
 * sin pago registrado no tiene cobro pendiente. No se infiere ningún reembolso.
 */
internal fun buyerPaymentText(status: String, paymentStatus: OrderPaymentStatus): String = when {
    paymentStatus == OrderPaymentStatus.PAID -> "Efectivo registrado · simulación"
    status == "CANCELLED" || status == "REJECTED" -> "Sin cobro: el pedido no se entregó"
    else -> "Efectivo pendiente hasta la entrega"
}

/** HU19: motivo del rechazo en palabras del comprador; un motivo ausente o desconocido nunca se inventa. */
internal fun buyerRejectionReasonText(reason: StoreOrderRejectionReason?): String = when (reason) {
    StoreOrderRejectionReason.STORE_BUSY -> "El local tiene alta demanda en este momento."
    StoreOrderRejectionReason.PRODUCT_UNAVAILABLE -> "Uno o más productos no están disponibles."
    StoreOrderRejectionReason.CLOSING_SOON -> "El local está próximo a cerrar."
    StoreOrderRejectionReason.OTHER -> "Otro motivo"
    null -> "Motivo del rechazo no disponible."
}

/** Un estado desconocido nunca rompe la pantalla: se muestra como «Estado en actualización» (N1). */
internal fun buyerOrderStatusUi(status: String, known: Boolean, outcome: String? = null): BuyerOrderStatusUi = when (if (known) status else "") {
    "PENDING_CONFIRMATION" -> BuyerOrderStatusUi("Pendiente de confirmación", Icons.Filled.HourglassEmpty, Neutral, NeutralText)
    "PREPARING" -> BuyerOrderStatusUi("En preparación", Icons.Filled.SoupKitchen, Neutral, NeutralText)
    "READY_FOR_PICKUP" -> BuyerOrderStatusUi("Listo para recoger", Icons.Filled.Inventory2, Neutral, NeutralText)
    "IN_DELIVERY" -> BuyerOrderStatusUi("En camino", Icons.Filled.DeliveryDining, Neutral, NeutralText)
    "COMPLETED" -> when (outcome) {
        "UNSUCCESSFUL" -> BuyerOrderStatusUi("Entrega no exitosa", Icons.Filled.Block, StoppedBg, StoppedText)
        "SUCCESS" -> BuyerOrderStatusUi("Entregado", Icons.Filled.Check, DoneBg, DoneText)
        else -> BuyerOrderStatusUi("Finalizado", Icons.Filled.Check, Neutral, NeutralText)
    }
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

private val clockTime = DateTimeFormatter.ofPattern("HH:mm", peru)

/** Hora de reloj de una estimación («12:50»); siempre aproximada, nunca una promesa. */
internal fun formatOrderClockTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    clockTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** HU20: misma regla que foodApi, solo como vista previa: max(hora vigente, ahora) + minutos. */
internal fun extendedPreviewEpochMillis(currentReadyAtEpochMillis: Long, nowEpochMillis: Long, additionalMinutes: Int): Long =
    maxOf(currentReadyAtEpochMillis, nowEpochMillis) + additionalMinutes * 60_000L

/** HU20: tarjeta del responsable; una hora vencida no significa que el pedido esté listo. */
internal fun storePreparationEstimateText(estimatedReadyAtEpochMillis: Long, nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()): String {
    val time = formatOrderClockTime(estimatedReadyAtEpochMillis, zone)
    return if (nowEpochMillis > estimatedReadyAtEpochMillis) "Hora estimada vencida ($time) · sigue en preparación"
    else "Listo aprox. a las $time"
}

/** HU20: textos del comprador mientras el pedido sigue en preparación. */
internal data class BuyerPreparationEstimateText(val headline: String, val detail: String?)

internal fun buyerPreparationEstimateText(estimatedReadyAtEpochMillis: Long, preparationExtendedMinutes: Int, nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()): BuyerPreparationEstimateText {
    val time = formatOrderClockTime(estimatedReadyAtEpochMillis, zone)
    // Una hora vencida no significa que el pedido esté listo: solo el estado READY_FOR_PICKUP lo indica.
    val headline = if (nowEpochMillis > estimatedReadyAtEpochMillis)
        "La hora estimada ($time) ya pasó. El local sigue preparando tu pedido."
    else "Hora estimada de preparación: $time (aprox.)"
    val detail = if (preparationExtendedMinutes > 0) "El local amplió el tiempo en $preparationExtendedMinutes min." else null
    return BuyerPreparationEstimateText(headline, detail)
}

/** «2× Lomo saltado · 1× Chicha morada · +1 más» con los datos históricos del resumen. */
internal fun itemPreviewText(summary: BuyerOrderSummary): String {
    val shown = summary.itemPreview.joinToString(" · ") { "${it.quantity}× ${it.name}" }
    return if (summary.hiddenLineCount > 0) "$shown · +${summary.hiddenLineCount} más" else shown
}
