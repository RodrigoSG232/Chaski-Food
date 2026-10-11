package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressRepository
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
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.discoverAvailableStores
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAvailableStoresRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val addressRepository: AddressRepository,
) : AvailableStoresRepository {

    override suspend fun getAvailableStores() = try {
        val addressState = addressRepository.observeAddressBook().first { state ->
            state !is AddressBookState.Loading
        }
        val book = when (addressState) {
            is AddressBookState.Ready -> addressState.book
            is AddressBookState.Error -> return ApiResult.Failure(addressState.message)
            AddressBookState.SignedOut -> return ApiResult.Failure("Inicia sesión para consultar locales disponibles.")
            is AddressBookState.Loading -> error("Estado de dirección inesperado.")
        }
        val selectedId = book.selectedAddressId
            ?: return ApiResult.Failure("Selecciona una dirección de entrega para buscar locales.")
        val selectedAddress = book.addresses.singleOrNull { it.id == selectedId }
            ?: return ApiResult.Failure("La dirección seleccionada ya no está disponible. Selecciona otra.")

        val snapshot = firestore.collection("stores")
            .whereEqualTo("status", StoreStatus.ACTIVE.name)
            .get(Source.SERVER)
            .await()
        val candidates = snapshot.documents.mapNotNull { document ->
            document.toDiscoveryCandidate()
        }
        ApiResult.Success(
            discoverAvailableStores(
                candidates = candidates,
                selectedAddress = selectedAddress.location,
                currentDay = getCurrentDayOfWeek(),
                currentTime = getCurrentFormattedTime(),
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ApiResult.Failure(error.localizedMessage ?: "No se pudieron consultar los locales disponibles.", error)
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
