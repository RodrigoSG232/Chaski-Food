package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
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
                    if (!doc.exists()) return@mapNotNull null
                    val data = doc.data ?: return@mapNotNull null
                    val statusStr = data["status"] as? String ?: "ACTIVE"
                    BusinessStore(
                        id = doc.id,
                        businessId = data["businessId"] as? String ?: "",
                        name = data["name"] as? String ?: "",
                        address = data["address"] as? String ?: "",
                        latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                        phone = data["phone"] as? String ?: "",
                        status = try { StoreStatus.valueOf(statusStr) } catch (e: Exception) { StoreStatus.ACTIVE },
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    )
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

            val data = mapOf(
                "businessId" to newStore.businessId,
                "name" to newStore.name,
                "address" to newStore.address,
                "latitude" to newStore.latitude,
                "longitude" to newStore.longitude,
                "phone" to newStore.phone,
                "status" to newStore.status.name,
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

            val data = mapOf(
                "businessId" to store.businessId,
                "name" to store.name,
                "address" to store.address,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "phone" to store.phone,
                "status" to store.status.name,
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

        val listener = managersRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar permisos de responsable.", error))
                return@addSnapshotListener
            }

            val managerDoc = snapshot?.documents?.firstOrNull { doc ->
                val email = doc.getString("email") ?: ""
                doc.id == managerEmailOrUid || email.equals(managerEmailOrUid, ignoreCase = true)
            }

            if (managerDoc == null || !managerDoc.exists()) {
                trySend(ApiResult.Success(emptyList()))
                return@addSnapshotListener
            }

            @Suppress("UNCHECKED_CAST")
            val assignedStoreIds = (managerDoc.get("assignedStoreIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            if (assignedStoreIds.isEmpty()) {
                trySend(ApiResult.Success(emptyList()))
                return@addSnapshotListener
            }

            storesRef.whereIn(FieldPath.documentId(), assignedStoreIds)
                .get()
                .addOnSuccessListener { storesSnapshot ->
                    val stores = storesSnapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val statusStr = data["status"] as? String ?: "ACTIVE"
                        BusinessStore(
                            id = doc.id,
                            businessId = data["businessId"] as? String ?: "",
                            name = data["name"] as? String ?: "",
                            address = data["address"] as? String ?: "",
                            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                            phone = data["phone"] as? String ?: "",
                            status = try { StoreStatus.valueOf(statusStr) } catch (e: Exception) { StoreStatus.ACTIVE },
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        )
                    }
                    trySend(ApiResult.Success(stores))
                }
                .addOnFailureListener { e ->
                    trySend(ApiResult.Failure(e.localizedMessage ?: "Error al cargar locales del responsable.", e))
                }
        }

        awaitClose { listener.remove() }
    }
}
