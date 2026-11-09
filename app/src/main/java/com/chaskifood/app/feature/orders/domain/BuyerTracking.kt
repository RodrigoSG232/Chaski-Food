package com.chaskifood.app.feature.orders.domain

data class BuyerTrackingPosition(
    val latitude: Double, val longitude: Double, val accuracyMeters: Double,
    val recordedAtEpochMillis: Long, val receivedAtEpochMillis: Long,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        require(accuracyMeters.isFinite() && accuracyMeters in 0.0..500.0)
        require(recordedAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS && receivedAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
    }
}

data class BuyerTracking(
    val orderId: String, val status: String, val stage: String, val assigned: Boolean,
    val riderName: String?, val deliveryOutcome: String?, val position: BuyerTrackingPosition?,
    val estimatedArrivalAtEpochMillis: Long?, val serverTimeEpochMillis: Long,
) {
    val terminal: Boolean get() = status in setOf("COMPLETED", "CANCELLED", "REJECTED")
    init {
        orderId(orderId)
        require(serverTimeEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        require(deliveryOutcome == null || (status == "COMPLETED" && deliveryOutcome in setOf("SUCCESS", "UNSUCCESSFUL")))
        require(assigned == !riderName.isNullOrBlank())
        require(position == null || (assigned && status == "IN_DELIVERY"))
        require(estimatedArrivalAtEpochMillis == null || (position != null &&
            estimatedArrivalAtEpochMillis in position.recordedAtEpochMillis..MAX_ORDER_EPOCH_MILLIS))
        val allowedStages = when (status) {
            "READY_FOR_PICKUP" -> if (assigned) setOf("ACCEPTED", "AT_MERCHANT") else setOf("SEARCHING_RIDER")
            "IN_DELIVERY" -> setOf("ON_THE_WAY", "AT_CUSTOMER")
            "PENDING_CONFIRMATION", "PREPARING", "COMPLETED", "CANCELLED", "REJECTED" -> setOf(status)
            else -> emptySet()
        }
        require(stage in allowedStages)
    }

    /** Stale is a presentation threshold, not a reason to discard the last position. */
    fun locationStale(nowEpochMillis: Long): Boolean =
        position?.let { nowEpochMillis - it.recordedAtEpochMillis > 120_000 } ?: true

    fun visibleEta(nowEpochMillis: Long): Long? = estimatedArrivalAtEpochMillis?.takeIf {
        !terminal && !locationStale(nowEpochMillis) && it > nowEpochMillis
    }
}

sealed interface BuyerTrackingResult {
    data class Loaded(val tracking: BuyerTracking) : BuyerTrackingResult
    data object NotFound : BuyerTrackingResult
    data object AuthenticationRequired : BuyerTrackingResult
    data object StaleSession : BuyerTrackingResult
    data object Unavailable : BuyerTrackingResult
}
