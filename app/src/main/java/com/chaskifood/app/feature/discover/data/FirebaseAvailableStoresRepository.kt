@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DEFAULT_DELIVERY_RADIUS_KM
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.business.domain.getCurrentDayOfWeek
import com.chaskifood.app.feature.business.domain.getCurrentFormattedTime
import com.chaskifood.app.feature.business.domain.validOperatingHours
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.discover.domain.SelectedDeliveryAddress
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.discoverAvailableStores
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAvailableStoresRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val addressRepository: AddressRepository,
) : AvailableStoresRepository {

    override fun observeAvailableStores(): Flow<AvailableStoresState> =
        addressRepository.observeAddressBook().flatMapLatest { addressState ->
            when (addressState) {
                AddressBookState.SignedOut -> flowOf(AvailableStoresState.SignedOut)
                is AddressBookState.Loading -> flowOf(AvailableStoresState.Loading)
                is AddressBookState.Error -> flowOf(AvailableStoresState.Error(addressState.message))
                is AddressBookState.Ready -> observeStores(addressState)
            }
        }

    private fun observeStores(addressState: AddressBookState.Ready): Flow<AvailableStoresState> {
        val book = addressState.book
        val selectedId = book.selectedAddressId
            ?: return flowOf(AvailableStoresState.AddressRequired)
        val selectedAddress = book.addresses.singleOrNull { it.id == selectedId }
            ?: return flowOf(AvailableStoresState.AddressRequired)
        val addressInfo = SelectedDeliveryAddress(
            id = selectedAddress.id,
            label = selectedAddress.label,
            addressText = selectedAddress.addressText,
        )

        return combine(observeStoreCandidates(), minuteTicker()) { result, _ ->
            when (result) {
                is StoreCandidatesResult.Error -> AvailableStoresState.Error(result.message)
                is StoreCandidatesResult.Success -> AvailableStoresState.Ready(
                    selectedAddress = addressInfo,
                    stores = discoverAvailableStores(
                        candidates = result.candidates,
                        selectedAddress = selectedAddress.location,
                        currentDay = getCurrentDayOfWeek(),
                        currentTime = getCurrentFormattedTime(),
                    ),
                )
            }
        }
    }

    private fun observeStoreCandidates(): Flow<StoreCandidatesResult> = callbackFlow {
        val registration = firestore.collection("stores")
            .whereEqualTo("status", StoreStatus.ACTIVE.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(StoreCandidatesResult.Error("No se pudieron consultar los locales disponibles."))
                    return@addSnapshotListener
                }
                try {
                    val candidates = snapshot?.documents.orEmpty().mapNotNull { document ->
                        document.toDiscoveryCandidate()
                    }
                    trySend(StoreCandidatesResult.Success(candidates))
                } catch (_: Exception) {
                    trySend(StoreCandidatesResult.Error("Se encontraron datos de locales inválidos."))
                }
            }
        awaitClose { registration.remove() }
    }
}

private sealed interface StoreCandidatesResult {
    data class Success(val candidates: List<StoreDiscoveryCandidate>) : StoreCandidatesResult
    data class Error(val message: String) : StoreCandidatesResult
}

/** Reevalúa el horario al cambiar de minuto, aunque Firestore no haya cambiado. */
private fun minuteTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        val millisUntilNextMinute = 60_000L - System.currentTimeMillis() % 60_000L
        delay(millisUntilNextMinute)
    }
}

internal fun DocumentSnapshot.toDiscoveryCandidate(): StoreDiscoveryCandidate? {
    if (!exists()) return null
    val data = data ?: return null
    return storeDiscoveryCandidate(id, data)
}

internal fun storeDiscoveryCandidate(
    id: String,
    data: Map<String, Any>,
): StoreDiscoveryCandidate? {
    val businessId = data["businessId"] as? String ?: return null
    val name = data["name"] as? String ?: return null
    val address = data["address"] as? String ?: return null
    val latitude = (data["latitude"] as? Number)?.toDouble() ?: return null
    val longitude = (data["longitude"] as? Number)?.toDouble() ?: return null
    val radius = (data["deliveryRadiusKm"] as? Number)?.toDouble() ?: DEFAULT_DELIVERY_RADIUS_KM
    val status = (data["status"] as? String)?.let { value ->
        StoreStatus.entries.find { it.name == value }
    } ?: return null
    val operationalStatus = (data["operationalStatus"] as? String)?.let { value ->
        OperationalStatus.entries.find { it.name == value }
    } ?: return null
    val rawHours = data["operatingHours"] as? List<*> ?: return null
    val hours = rawHours.mapNotNull { it.toOperatingHours() }
    if (hours.size != rawHours.size || !validOperatingHours(hours)) return null

    return StoreDiscoveryCandidate(
        store = BusinessStore(
            id = id,
            businessId = businessId,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            deliveryRadiusKm = radius,
            phone = data["phone"] as? String ?: "",
            status = status,
            operationalStatus = operationalStatus,
            operatingHours = hours,
        ),
        // Los documentos públicos de locales no exponen actualmente la ficha privada del negocio.
        businessName = name,
        businessCategory = "Restaurante",
    )
}

private fun Any?.toOperatingHours(): DayOperatingHours? {
    val fields = this as? Map<*, *> ?: return null
    val day = (fields["dayOfWeek"] as? String)?.let { value ->
        DayOfWeekEnum.entries.find { it.name == value }
    } ?: return null
    return DayOperatingHours(
        dayOfWeek = day,
        openTime = fields["openTime"] as? String ?: return null,
        closeTime = fields["closeTime"] as? String ?: return null,
        enabled = fields["enabled"] as? Boolean ?: return null,
    )
}
