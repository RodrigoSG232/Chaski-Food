package com.chaskifood.app.feature.discover.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreStatus
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class StoreDiscoveryCandidate(
    val store: BusinessStore,
    val businessName: String,
    val businessCategory: String,
)

/** Información suficiente para distinguir y seleccionar un local comprable. */
data class DiscoverableStore(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessCategory: String,
    val name: String,
    val address: String,
    val phone: String,
    val distanceKm: Double,
    val imageUrl: String? = null,
)

enum class StoreExclusionReason {
    INVALID_ADDRESS,
    INVALID_STORE_LOCATION,
    INVALID_COVERAGE,
    STORE_NOT_ENABLED,
    STORE_NOT_OPERATIONAL,
    OUTSIDE_OPERATING_HOURS,
    OUTSIDE_COVERAGE,
}

sealed interface StoreEligibilityResult {
    data class Eligible(val store: DiscoverableStore) : StoreEligibilityResult
    data class Excluded(val reason: StoreExclusionReason) : StoreEligibilityResult
}

/** Evalúa un local contra la dirección seleccionada y la hora local de Lima recibida. */
fun evaluateStoreEligibility(
    candidate: StoreDiscoveryCandidate,
    selectedAddress: AddressCoordinates,
    currentDay: DayOfWeekEnum,
    currentTime: String,
): StoreEligibilityResult {
    val store = candidate.store
    if (!selectedAddress.isValid) return excluded(StoreExclusionReason.INVALID_ADDRESS)
    val storeLocation = AddressCoordinates(store.latitude, store.longitude)
    if (!storeLocation.isValid) return excluded(StoreExclusionReason.INVALID_STORE_LOCATION)
    if (!store.deliveryRadiusKm.isFinite() || store.deliveryRadiusKm <= 0) {
        return excluded(StoreExclusionReason.INVALID_COVERAGE)
    }
    if (store.status != StoreStatus.ACTIVE) return excluded(StoreExclusionReason.STORE_NOT_ENABLED)
    if (store.operationalStatus != OperationalStatus.OPEN) {
        return excluded(StoreExclusionReason.STORE_NOT_OPERATIONAL)
    }
    if (!store.isAvailableForOrders(currentTime, currentDay)) {
        return excluded(StoreExclusionReason.OUTSIDE_OPERATING_HOURS)
    }

    val distanceKm = distanceInKilometers(selectedAddress, storeLocation)
    if (distanceKm > store.deliveryRadiusKm) return excluded(StoreExclusionReason.OUTSIDE_COVERAGE)

    return StoreEligibilityResult.Eligible(
        DiscoverableStore(
            id = store.id,
            businessId = store.businessId,
            businessName = candidate.businessName,
            businessCategory = candidate.businessCategory,
            name = store.name,
            address = store.address,
            phone = store.phone,
            distanceKm = distanceKm,
            imageUrl = store.imageUrl,
        ),
    )
}

fun discoverAvailableStores(
    candidates: List<StoreDiscoveryCandidate>,
    selectedAddress: AddressCoordinates,
    currentDay: DayOfWeekEnum,
    currentTime: String,
): List<DiscoverableStore> = candidates.mapNotNull { candidate ->
    (evaluateStoreEligibility(candidate, selectedAddress, currentDay, currentTime)
        as? StoreEligibilityResult.Eligible)?.store
}.sortedWith(compareBy(DiscoverableStore::distanceKm, DiscoverableStore::businessName, DiscoverableStore::name))

internal fun distanceInKilometers(
    first: AddressCoordinates,
    second: AddressCoordinates,
): Double {
    val earthRadiusKm = 6_371.0088
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(firstLatitude) * cos(secondLatitude) *
        sin(longitudeDelta / 2).let { it * it }
    return earthRadiusKm * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

private fun excluded(reason: StoreExclusionReason) = StoreEligibilityResult.Excluded(reason)
