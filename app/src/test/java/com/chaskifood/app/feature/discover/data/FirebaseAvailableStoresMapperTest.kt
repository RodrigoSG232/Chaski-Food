package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.feature.business.domain.DEFAULT_DELIVERY_RADIUS_KM
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseAvailableStoresMapperTest {

    @Test
    fun `convierte un documento de local en candidato de descubrimiento`() {
        val candidate = storeDiscoveryCandidate("store-1", validFields())!!

        assertEquals("store-1", candidate.store.id)
        assertEquals("business-1", candidate.store.businessId)
        assertEquals("Local Centro", candidate.store.name)
        assertEquals(-12.12, candidate.store.latitude, 0.0)
        assertEquals(7.5, candidate.store.deliveryRadiusKm, 0.0)
        assertEquals(StoreStatus.ACTIVE, candidate.store.status)
        assertEquals(OperationalStatus.OPEN, candidate.store.operationalStatus)
        assertEquals(DayOfWeekEnum.MONDAY, candidate.store.operatingHours.first().dayOfWeek)
    }

    @Test
    fun `local antiguo sin radio usa cobertura predeterminada`() {
        val fields = validFields().toMutableMap().apply { remove("deliveryRadiusKm") }

        val candidate = storeDiscoveryCandidate("store-1", fields)!!

        assertEquals(DEFAULT_DELIVERY_RADIUS_KM, candidate.store.deliveryRadiusKm, 0.0)
    }

    @Test
    fun `descarta documentos incompletos o con estados desconocidos`() {
        val withoutCoordinates = validFields().toMutableMap().apply { remove("latitude") }
        val invalidStatus = validFields().toMutableMap().apply { this["status"] = "UNKNOWN" }
        val malformedHours = validFields().toMutableMap().apply {
            this["operatingHours"] = listOf(mapOf("dayOfWeek" to "MONDAY"))
        }

        assertNull(storeDiscoveryCandidate("store-1", withoutCoordinates))
        assertNull(storeDiscoveryCandidate("store-1", invalidStatus))
        assertNull(storeDiscoveryCandidate("store-1", malformedHours))
    }

    private fun validFields(): Map<String, Any> = mapOf(
        "businessId" to "business-1",
        "name" to "Local Centro",
        "address" to "Av. Principal 123",
        "latitude" to -12.12,
        "longitude" to -77.03,
        "deliveryRadiusKm" to 7.5,
        "phone" to "999888777",
        "status" to "ACTIVE",
        "operationalStatus" to "OPEN",
        "operatingHours" to DayOfWeekEnum.entries.map { day ->
            mapOf(
                "dayOfWeek" to day.name,
                "openTime" to "08:00",
                "closeTime" to "22:00",
                "enabled" to true,
            )
        },
    )
}
