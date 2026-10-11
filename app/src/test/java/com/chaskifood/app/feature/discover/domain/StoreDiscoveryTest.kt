package com.chaskifood.app.feature.discover.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreDiscoveryTest {
    private val customerAddress = AddressCoordinates(-12.1211, -77.0297)

    @Test
    fun `local comprable conserva informacion distintiva y distancia`() {
        val result = evaluateStoreEligibility(
            candidate(store = store(latitude = -12.1195, longitude = -77.0278)),
            customerAddress,
            DayOfWeekEnum.MONDAY,
            "12:00",
        )

        val available = (result as StoreEligibilityResult.Eligible).store
        assertEquals("store-1", available.id)
        assertEquals("Sabor Peruano", available.businessName)
        assertEquals("Criolla", available.businessCategory)
        assertEquals("Miraflores", available.name)
        assertEquals("Av. Larco 123", available.address)
        assertTrue(available.distanceKm in 0.2..0.4)
    }

    @Test
    fun `excluye locales inactivos suspendidos pausados o cerrados`() {
        assertExcluded(StoreExclusionReason.STORE_NOT_ENABLED, store(status = StoreStatus.INACTIVE))
        assertExcluded(StoreExclusionReason.STORE_NOT_ENABLED, store(status = StoreStatus.SUSPENDED))
        assertExcluded(
            StoreExclusionReason.STORE_NOT_OPERATIONAL,
            store(operationalStatus = OperationalStatus.PAUSED),
        )
        assertExcluded(
            StoreExclusionReason.STORE_NOT_OPERATIONAL,
            store(operationalStatus = OperationalStatus.CLOSED),
        )
    }

    @Test
    fun `excluye local fuera de horario`() {
        assertExcluded(
            StoreExclusionReason.OUTSIDE_OPERATING_HOURS,
            store(operatingHours = hours(DayOfWeekEnum.MONDAY, "08:00", "10:00")),
            currentTime = "10:00",
        )
    }

    @Test
    fun `acepta horario nocturno iniciado el dia anterior`() {
        val result = evaluateStoreEligibility(
            candidate(store(operatingHours = hours(DayOfWeekEnum.SUNDAY, "23:00", "03:00"))),
            customerAddress,
            DayOfWeekEnum.MONDAY,
            "02:00",
        )

        assertTrue(result is StoreEligibilityResult.Eligible)
    }

    @Test
    fun `excluye local fuera de su radio de cobertura`() {
        assertExcluded(
            StoreExclusionReason.OUTSIDE_COVERAGE,
            store(latitude = -12.0464, longitude = -77.0428, deliveryRadiusKm = 5.0),
        )
    }

    @Test
    fun `el borde del radio se considera cubierto`() {
        val distance = distanceInKilometers(customerAddress, AddressCoordinates(-12.1195, -77.0278))
        val result = evaluateStoreEligibility(
            candidate(store(latitude = -12.1195, longitude = -77.0278, deliveryRadiusKm = distance)),
            customerAddress,
            DayOfWeekEnum.MONDAY,
            "12:00",
        )

        assertTrue(result is StoreEligibilityResult.Eligible)
    }

    @Test
    fun `rechaza coordenadas o cobertura invalidas`() {
        val invalidAddress = evaluateStoreEligibility(
            candidate(store()),
            AddressCoordinates(91.0, 0.0),
            DayOfWeekEnum.MONDAY,
            "12:00",
        )
        assertEquals(
            StoreExclusionReason.INVALID_ADDRESS,
            (invalidAddress as StoreEligibilityResult.Excluded).reason,
        )
        assertExcluded(StoreExclusionReason.INVALID_STORE_LOCATION, store(latitude = Double.NaN))
        assertExcluded(StoreExclusionReason.INVALID_COVERAGE, store(deliveryRadiusKm = 0.0))
    }

    @Test
    fun `solo devuelve comprables y los ordena por distancia`() {
        val stores = discoverAvailableStores(
            candidates = listOf(
                candidate(store(id = "far", latitude = -12.13, longitude = -77.03, deliveryRadiusKm = 10.0)),
                candidate(store(id = "closed", operationalStatus = OperationalStatus.CLOSED)),
                candidate(store(id = "near", latitude = -12.1209, longitude = -77.0295)),
            ),
            selectedAddress = customerAddress,
            currentDay = DayOfWeekEnum.MONDAY,
            currentTime = "12:00",
        )

        assertEquals(listOf("near", "far"), stores.map { it.id })
    }

    private fun assertExcluded(
        expected: StoreExclusionReason,
        store: BusinessStore,
        currentTime: String = "12:00",
    ) {
        val result = evaluateStoreEligibility(
            candidate(store),
            customerAddress,
            DayOfWeekEnum.MONDAY,
            currentTime,
        )
        assertEquals(expected, (result as StoreEligibilityResult.Excluded).reason)
    }

    private fun candidate(store: BusinessStore) = StoreDiscoveryCandidate(
        store = store,
        businessName = "Sabor Peruano",
        businessCategory = "Criolla",
    )

    private fun store(
        id: String = "store-1",
        latitude: Double = customerAddress.latitude,
        longitude: Double = customerAddress.longitude,
        deliveryRadiusKm: Double = 5.0,
        status: StoreStatus = StoreStatus.ACTIVE,
        operationalStatus: OperationalStatus = OperationalStatus.OPEN,
        operatingHours: List<DayOperatingHours> = hours(DayOfWeekEnum.MONDAY, "08:00", "22:00"),
    ) = BusinessStore(
        id = id,
        businessId = "business-1",
        name = "Miraflores",
        address = "Av. Larco 123",
        latitude = latitude,
        longitude = longitude,
        deliveryRadiusKm = deliveryRadiusKm,
        phone = "999888777",
        status = status,
        operationalStatus = operationalStatus,
        operatingHours = operatingHours,
    )

    private fun hours(day: DayOfWeekEnum, opening: String, closing: String) =
        DayOfWeekEnum.entries.map { current ->
            DayOperatingHours(
                dayOfWeek = current,
                openTime = if (current == day) opening else "08:00",
                closeTime = if (current == day) closing else "08:00",
                enabled = current == day,
            )
        }
}
