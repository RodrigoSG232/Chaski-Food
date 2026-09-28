package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.business.domain.defaultWeeklyOperatingHours
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : StoreRepository {

    private val storesRef by lazy { firestore.collection("stores") }
    private val managersRef by lazy { firestore.collection("store_managers") }

    override fun getStoresByBusiness(businessId: String): Flow<ApiResult<List<BusinessStore>>> = callbackFlow {
        if (businessId.isBlank()) {
            trySend(ApiResult.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = storesRef.whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar locales.", error))
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toBusinessStore()
                } ?: emptyList()

                trySend(ApiResult.Success(list))
            }

        awaitClose { listener.remove() }
    }

    override suspend fun createStore(store: BusinessStore): ApiResult<BusinessStore> {
        return try {
            val docRef = storesRef.document()
            val storeId = docRef.id
            val newStore = store.copy(id = storeId)

            val hoursData = newStore.operatingHours.map { h ->
                mapOf(
                    "dayOfWeek" to h.dayOfWeek.name,
                    "openTime" to h.openTime,
                    "closeTime" to h.closeTime,
                    "enabled" to h.enabled,
                )
            }

            val data = mapOf(
                "businessId" to newStore.businessId,
                "name" to newStore.name,
                "address" to newStore.address,
                "latitude" to newStore.latitude,
                "longitude" to newStore.longitude,
                "phone" to newStore.phone,
                "status" to newStore.status.name,
                "operationalStatus" to newStore.operationalStatus.name,
                "pauseReason" to (newStore.pauseReason ?: ""),
                "operatingHours" to hoursData,
                "createdAt" to newStore.createdAt,
            )

            docRef.set(data).await()
            ApiResult.Success(newStore)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al registrar el local.", e)
        }
    }

    override suspend fun updateStore(store: BusinessStore): ApiResult<BusinessStore> {
        return try {
            if (store.id.isBlank()) {
                return ApiResult.Failure("El ID del local no puede estar vacío.")
            }

            val hoursData = store.operatingHours.map { h ->
                mapOf(
                    "dayOfWeek" to h.dayOfWeek.name,
                    "openTime" to h.openTime,
                    "closeTime" to h.closeTime,
                    "enabled" to h.enabled,
                )
            }

            val data = mapOf(
                "businessId" to store.businessId,
                "name" to store.name,
                "address" to store.address,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "phone" to store.phone,
                "status" to store.status.name,
                "operationalStatus" to store.operationalStatus.name,
                "pauseReason" to (store.pauseReason ?: ""),
                "operatingHours" to hoursData,
            )

            storesRef.document(store.id).update(data).await()
            ApiResult.Success(store)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar la información del local.", e)
        }
    }

    override fun getManagersByBusiness(businessId: String): Flow<ApiResult<List<StoreManager>>> = callbackFlow {
        if (businessId.isBlank()) {
            trySend(ApiResult.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = managersRef.whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar responsables.", error))
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    if (!doc.exists()) return@mapNotNull null
                    val data = doc.data ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val assignedIds = (data["assignedStoreIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    StoreManager(
                        id = doc.id,
                        businessId = data["businessId"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        fullName = data["fullName"] as? String ?: "",
                        phone = data["phone"] as? String ?: "",
                        assignedStoreIds = assignedIds,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    )
                } ?: emptyList()

                trySend(ApiResult.Success(list))
            }

        awaitClose { listener.remove() }
    }

    override suspend fun assignManager(manager: StoreManager): ApiResult<StoreManager> {
        return try {
            val docRef = if (manager.id.isNotBlank()) managersRef.document(manager.id) else managersRef.document()
            val managerId = docRef.id
            val updatedManager = manager.copy(id = managerId)

            val data = mapOf(
                "businessId" to updatedManager.businessId,
                "email" to updatedManager.email,
                "fullName" to updatedManager.fullName,
                "phone" to updatedManager.phone,
                "assignedStoreIds" to updatedManager.assignedStoreIds,
                "createdAt" to updatedManager.createdAt,
            )

            docRef.set(data).await()
            ApiResult.Success(updatedManager)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al asignar el responsable.", e)
        }
    }

    override fun getStoresForManager(managerEmailOrUid: String): Flow<ApiResult<List<BusinessStore>>> = callbackFlow {
        if (managerEmailOrUid.isBlank()) {
            trySend(ApiResult.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        var storeListener: ListenerRegistration? = null

        val managerListener = managersRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar permisos de responsable.", error))
                return@addSnapshotListener
            }

            val managerDoc = snapshot?.documents?.firstOrNull { doc ->
                val email = doc.getString("email") ?: ""
                doc.id == managerEmailOrUid || email.equals(managerEmailOrUid, ignoreCase = true)
            }

            @Suppress("UNCHECKED_CAST")
            val assignedStoreIds = (managerDoc?.get("assignedStoreIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            storeListener?.remove()

            if (assignedStoreIds.isNotEmpty()) {
                storeListener = storesRef.whereIn(FieldPath.documentId(), assignedStoreIds)
                    .addSnapshotListener { storesSnapshot, storesError ->
                        if (storesError != null) {
                            trySend(ApiResult.Failure(storesError.localizedMessage ?: "Error al cargar locales.", storesError))
                            return@addSnapshotListener
                        }
                        val stores = storesSnapshot?.documents?.mapNotNull { it.toBusinessStore() } ?: emptyList()
                        trySend(ApiResult.Success(stores))
                    }
            } else {
                // Si no hay asignación previa en store_managers, escucha en tiempo real todos los locales para que el usuario pueda operarlos
                storeListener = storesRef.addSnapshotListener { storesSnapshot, storesError ->
                    if (storesError != null) {
                        trySend(ApiResult.Failure(storesError.localizedMessage ?: "Error al cargar locales.", storesError))
                        return@addSnapshotListener
                    }
                    val stores = storesSnapshot?.documents?.mapNotNull { it.toBusinessStore() } ?: emptyList()
                    trySend(ApiResult.Success(stores))
                }
            }
        }

        awaitClose {
            managerListener.remove()
            storeListener?.remove()
        }
    }

    override suspend fun updateOperationalStatus(
        storeId: String,
        status: OperationalStatus,
        pauseReason: String?,
    ): ApiResult<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "operationalStatus" to status.name,
                "pauseReason" to (pauseReason ?: ""),
            )
            storesRef.document(storeId).update(data).await()
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar el estado operativo del local.", e)
        }
    }

    override suspend fun updateOperatingHours(
        storeId: String,
        hours: List<DayOperatingHours>,
    ): ApiResult<Unit> {
        return try {
            val hoursData = hours.map { h ->
                mapOf(
                    "dayOfWeek" to h.dayOfWeek.name,
                    "openTime" to h.openTime,
                    "closeTime" to h.closeTime,
                    "enabled" to h.enabled,
                )
            }
            storesRef.document(storeId).update("operatingHours", hoursData).await()
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar los horarios del local.", e)
        }
    }

    private fun DocumentSnapshot.toBusinessStore(): BusinessStore? {
        if (!exists()) return null
        val data = data ?: return null
        val statusStr = data["status"] as? String ?: "ACTIVE"
        val opStatusStr = data["operationalStatus"] as? String ?: "OPEN"
        val rawHours = data["operatingHours"] as? List<*>

        val parsedHours = rawHours?.mapNotNull { h ->
            val map = h as? Map<*, *> ?: return@mapNotNull null
            val dayName = map["dayOfWeek"] as? String ?: return@mapNotNull null
            val dayEnum = try { DayOfWeekEnum.valueOf(dayName) } catch (e: Exception) { DayOfWeekEnum.MONDAY }
            DayOperatingHours(
                dayOfWeek = dayEnum,
                openTime = map["openTime"] as? String ?: "08:00",
                closeTime = map["closeTime"] as? String ?: "22:00",
                enabled = map["enabled"] as? Boolean ?: true,
            )
        } ?: defaultWeeklyOperatingHours()

        return BusinessStore(
            id = id,
            businessId = data["businessId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            address = data["address"] as? String ?: "",
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            phone = data["phone"] as? String ?: "",
            status = try { StoreStatus.valueOf(statusStr) } catch (e: Exception) { StoreStatus.ACTIVE },
            operationalStatus = try { OperationalStatus.valueOf(opStatusStr) } catch (e: Exception) { OperationalStatus.OPEN },
            pauseReason = data["pauseReason"] as? String,
            operatingHours = parsedHours,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        )
    }
}