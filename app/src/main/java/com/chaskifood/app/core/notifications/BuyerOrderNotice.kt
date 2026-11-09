package com.chaskifood.app.core.notifications

/** Minimal push hint; the current order is fetched with the active account after navigation. */
internal enum class BuyerNoticeType(val title: String, val body: String) {
    ORDER_ACCEPTED("Pedido aceptado", "Consulta el estado actual de tu pedido."),
    ORDER_REJECTED("Pedido rechazado", "Consulta los detalles de tu pedido."),
    ORDER_READY_FOR_PICKUP("Pedido listo", "Tu pedido está listo para el recojo."),
    RIDER_ASSIGNED("Repartidor asignado", "Consulta el seguimiento de tu pedido."),
    ORDER_PICKED_UP("Pedido recogido", "Consulta el avance de la entrega."),
    ORDER_DELIVERED("Pedido entregado", "Consulta el estado final de tu pedido."),
    ORDER_DELIVERY_FAILED("Entrega no exitosa", "Consulta el estado final de tu pedido."),
}

internal data class BuyerOrderNotice(override val eventId: String, override val orderId: String,
    override val recipientUid: String, val type: BuyerNoticeType) : OrderNotice {
    companion object {
        fun parse(data: Map<String, String>): BuyerOrderNotice? {
            if (data.keys != setOf("type", "eventId", "orderId", "recipientUid")) return null
            val type = BuyerNoticeType.entries.singleOrNull { it.name == data["type"] } ?: return null
            val orderId = data["orderId"] ?: return null
            if (!orderId.matches(Regex("[0-9a-f]{64}"))) return null
            val eventId = data["eventId"] ?: return null
            if (!eventId.matches(Regex("buyer_notice_" + orderId + "_" + type.name.lowercase() + "_[1-9][0-9]*"))) return null
            val recipientUid = data["recipientUid"] ?: return null
            if (!recipientUid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return null
            return BuyerOrderNotice(eventId, orderId, recipientUid, type)
        }
    }
}
