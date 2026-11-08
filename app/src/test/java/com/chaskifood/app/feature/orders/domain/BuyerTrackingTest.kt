package com.chaskifood.app.feature.orders.domain

import org.junit.Assert.*
import org.junit.Test

class BuyerTrackingTest {
    private val now = 1_800_000_000_000L
    private val sample = BuyerTrackingPosition(-12.1, -77.1, 12.0, now, now)
    private val tracking = BuyerTracking("a".repeat(64), "IN_DELIVERY", "ON_THE_WAY", true, "Ana", null,
        sample, now + 600_000, now)

    @Test fun staleOrExpiredEtaIsHiddenWithoutRemovingLastLocation() {
        assertEquals(now + 600_000, tracking.visibleEta(now))
        assertNotNull(tracking.visibleEta(now + 120_000))
        assertNull(tracking.visibleEta(now + 120_001))
        assertEquals(sample, tracking.position)
        assertNull(tracking.copy(estimatedArrivalAtEpochMillis = now).visibleEta(now))
        assertNull(tracking.copy(estimatedArrivalAtEpochMillis = null).visibleEta(now))
    }

    @Test fun prePickupAndTerminalCannotCarryCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { tracking.copy(status = "READY_FOR_PICKUP", stage = "ACCEPTED") }
        assertThrows(IllegalArgumentException::class.java) { tracking.copy(status = "COMPLETED", stage = "COMPLETED") }
        assertThrows(IllegalArgumentException::class.java) { tracking.copy(assigned = false, riderName = null) }
        assertThrows(IllegalArgumentException::class.java) { tracking.copy(estimatedArrivalAtEpochMillis = now - 1) }
        assertThrows(IllegalArgumentException::class.java) { sample.copy(latitude = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { sample.copy(accuracyMeters = 501.0) }
        val done = tracking.copy(status = "COMPLETED", stage = "COMPLETED", position = null,
            estimatedArrivalAtEpochMillis = null, deliveryOutcome = "UNSUCCESSFUL")
        assertTrue(done.terminal); assertNull(done.visibleEta(now))
    }
}
