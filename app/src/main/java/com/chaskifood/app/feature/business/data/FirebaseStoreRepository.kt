package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.business.domain.validOperatingHours
import kotlinx.coroutines.CancellationException
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
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
    private val access: FirebaseAccessControl,
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
            access.requireBusinessOwner(store.businessId)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al registrar el local.", e)
        }
    }

    override suspend fun updateStore(store: BusinessStore): ApiResult<BusinessStore> {
        return try {
            if (store.id.isBlank()) {
                return ApiResult.Failure("El ID del local no puede estar vacío.")
            }

            access.requireBusinessOwner(store.businessId)
            val existing = storesRef.document(store.id).get().await()
            check(existing.getString("businessId") == store.businessId) { "Local no autorizado." }
            val data = mapOf(
                "name" to store.name,
                "address" to store.address,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "phone" to store.phone,
                "status" to if (existing.getString("status") == "SUSPENDED") "SUSPENDED" else store.status.name,
            )

            storesRef.document(store.id).update(data).await()
            ApiResult.Success(store)
        } catch (cancelled: CancellationException) {
            throw cancelled
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
            access.requireBusinessOwner(manager.businessId)
            require(manager.assignedStoreIds.isNotEmpty()) { "Selecciona al menos un local." }
            for (id in manager.assignedStoreIds) {
                check(storesRef.document(id).get().await().getString("businessId") == manager.businessId) { "Local ajeno al negocio." }
            }
            if (manager.id.isNotBlank()) {
                check(managersRef.document(manager.id).get().await().getString("businessId") == manager.businessId) { "Responsable ajeno al negocio." }
            }
            val email = manager.email.trim().lowercase()
            require(email.contains('@') && !email.contains('/')) { "Correo de responsable inválido." }
            val docRef = managersRef.document(manager.businessId + "_" + email)
            val managerId = docRef.id
            val updatedManager = manager.copy(id = managerId)

            val data = mapOf(
                "businessId" to updatedManager.businessId,
                "email" to updatedManager.email.trim().lowercase(),
                "fullName" to updatedManager.fullName,
                "phone" to updatedManager.phone,
                "assignedStoreIds" to updatedManager.assignedStoreIds,
                "createdAt" to updatedManager.createdAt,
            )

            val batch = firestore.batch().set(docRef, data)
            if (manager.id.isNotBlank() && manager.id != managerId) batch.delete(managersRef.document(manager.id))
            batch.commit().await()
            ApiResult.Success(updatedManager)
        } catch (cancelled: CancellationException) {
            throw cancelled
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

        val user = try {
            access.requireUser().also { user ->
                if (!user.isEmailVerified) {
                    user.reload().await()
                    if (user.isEmailVerified) user.getIdToken(true).await()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trySend(ApiResult.Failure("No se pudo comprobar tu cuenta. Vuelve a intentarlo.", error))
            close()
            return@callbackFlow
        }
        val email = user.email?.lowercase()
        if (email.isNullOrBlank() || !user.isEmailVerified || !email.equals(managerEmailOrUid, ignoreCase = true)) {
            trySend(ApiResult.Failure("Verifica tu correo para consultar los locales asignados."))
            close()
            return@callbackFlow
        }
        val storeListeners = mutableListOf<ListenerRegistration>()
        var generation = 0
        val managerListener = managersRef.whereEqualTo("email", email).addSnapshotListener { snapshot, error ->
            if (error != null) { trySend(ApiResult.Failure("No se pudieron consultar tus asignaciones.", error)); return@addSnapshotListener }
            storeListeners.forEach { it.remove() }; storeListeners.clear()
            val currentGeneration = ++generation
            val ids = snapshot?.documents.orEmpty().flatMap { doc ->
                (doc.get("assignedStoreIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
            }.distinct()
            if (ids.isEmpty()) { trySend(ApiResult.Success(emptyList())); return@addSnapshotListener }
            val chunks = ids.chunked(10)
            val results = mutableMapOf<Int, List<BusinessStore>>()
            chunks.forEachIndexed { index, chunk ->
                storeListeners += storesRef.whereIn(FieldPath.documentId(), chunk).addSnapshotListener { stores, failure ->
                    if (currentGeneration != generation) return@addSnapshotListener
                    if (failure != null) trySend(ApiResult.Failure("No se pudieron cargar tus locales.", failure))
                    else {
                        results[index] = stores?.documents?.mapNotNull { it.toBusinessStore() }.orEmpty()
                        if (results.size == chunks.size) trySend(ApiResult.Success(results.toSortedMap().values.flatten()))
                    }
                }
            }
        }
        awaitClose { managerListener.remove(); storeListeners.forEach { it.remove() } }
    }

    override suspend fun updateOperationalStatus(
        storeId: String,
        status: OperationalStatus,
        pauseReason: String?,
    ): ApiResult<Unit> {
        return try {
            access.requireStoreOperator(storeId)
            val data = mutableMapOf<String, Any>(
                "operationalStatus" to status.name,
                "pauseReason" to (pauseReason ?: ""),
            )
            storesRef.document(storeId).update(data).await()
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar el estado operativo del local.", e)
        }
    }

    override suspend fun updateOperatingHours(
        storeId: String,
        hours: List<DayOperatingHours>,
    ): ApiResult<Unit> {
        return try {
            require(validOperatingHours(hours)) { "Usa siete días distintos y horas HH:mm válidas; apertura y cierre deben diferir." }
            access.requireStoreOperator(storeId)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al actualizar los horarios del local.", e)
        }
    }

    private fun DocumentSnapshot.toBusinessStore(): BusinessStore? {
        if (!exists()) return null
        val data = data ?: return null
        val statusStr = data["status"] as? String ?: "INACTIVE"
        val opStatusStr = data["operationalStatus"] as? String ?: "CLOSED"
        val rawHours = data["operatingHours"] as? List<*>

        val parsedHours = rawHours?.mapNotNull { h ->
            val map = h as? Map<*, *> ?: return@mapNotNull null
            val dayName = map["dayOfWeek"] as? String ?: return@mapNotNull null
            val dayEnum = DayOfWeekEnum.entries.find { it.name == dayName } ?: return@mapNotNull null
            DayOperatingHours(
                dayOfWeek = dayEnum,
                openTime = map["openTime"] as? String ?: "08:00",
                closeTime = map["closeTime"] as? String ?: "22:00",
                enabled = map["enabled"] as? Boolean ?: true,
            )
        } ?: emptyList()

        return BusinessStore(
            id = id,
            businessId = data["businessId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            address = data["address"] as? String ?: "",
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            phone = data["phone"] as? String ?: "",
            status = StoreStatus.entries.find { it.name == statusStr } ?: StoreStatus.INACTIVE,
            operationalStatus = OperationalStatus.entries.find { it.name == opStatusStr } ?: OperationalStatus.CLOSED,
            pauseReason = data["pauseReason"] as? String,
            operatingHours = parsedHours,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            suspensionReason = data["suspensionReason"] as? String,
        )
    }

    override suspend fun setAdministrativeStatus(storeId: String, status: StoreStatus, reason: String?): ApiResult<Unit> {
        return try {
            val admin = access.requireAdmin()
            require(status == StoreStatus.SUSPENDED || status == StoreStatus.ACTIVE)
            require(status != StoreStatus.SUSPENDED || !reason.isNullOrBlank()) { "Indica el motivo de suspensión." }
            val reference = storesRef.document(storeId)
            val audit = firestore.collection("audit_logs").document()
            firestore.runTransaction { transaction ->
                val existing = transaction.get(reference)
                check(existing.exists()) { "El local ya no existe." }
                val previous = existing.getString("status")
                check(if (status == StoreStatus.ACTIVE) previous == "SUSPENDED" else previous in listOf("ACTIVE", "INACTIVE")) {
                    "El estado del local cambió. Actualiza la lista."
                }
                check(access.requireUser().uid == admin.uid) { "La sesión cambió." }
                transaction.update(reference, mapOf(
                    "status" to status.name, "suspensionReason" to if (status == StoreStatus.SUSPENDED) reason?.trim() else null,
                    "lastAuditId" to audit.id, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ))
                transaction.set(audit, mapOf(
                    "adminUid" to admin.uid, "adminEmail" to (admin.email ?: admin.uid),
                    "action" to if (status == StoreStatus.SUSPENDED) "SUSPENDER_LOCAL" else "REACTIVAR_LOCAL",
                    "targetStoreId" to storeId, "targetBusinessId" to existing.getString("businessId"),
                    "targetBusinessName" to existing.getString("name"), "targetRuc" to "", "details" to reason?.trim(),
                    "previousStatus" to previous, "newStatus" to status.name,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ))
            }.await()
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { ApiResult.Failure(error.message ?: "No se pudo actualizar el local.", error) }
    }
}
