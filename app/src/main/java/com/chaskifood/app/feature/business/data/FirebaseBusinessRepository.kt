package com.chaskifood.app.feature.business.data

import com.chaskifood.app.feature.business.data.remote.CommercialMutation
import com.chaskifood.app.feature.business.data.remote.CommercialBusiness
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.AuditLog
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessSuspensionResult
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import kotlinx.coroutines.CancellationException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseBusinessRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val access: FirebaseAccessControl,
    private val commercial: CommercialOperations,
) : BusinessRepository {
    private val collectionRef by lazy { firestore.collection("business_requests") }
    private val auditLogsRef by lazy { firestore.collection("audit_logs") }
    private val storesRef by lazy { firestore.collection("stores") }

    override fun getBusinessRequest(ownerUid: String): Flow<ApiResult<BusinessRequest?>> = callbackFlow {
        val targetUid = try { access.requireUser().uid } catch (error: Exception) {
            trySend(ApiResult.Failure("Inicia sesión para consultar tu negocio.", error))
            close()
            return@callbackFlow
        }
        if (ownerUid.isNotBlank() && ownerUid != targetUid) {
            trySend(ApiResult.Failure("No tienes permisos para consultar este negocio."))
            close()
            return@callbackFlow
        }
        val listener = collectionRef.document(targetUid).addSnapshotListener { doc, error ->
            if (error != null) {
                trySend(ApiResult.Failure("No se pudo consultar el negocio.", error))
            } else {
                trySend(ApiResult.Success(doc?.takeIf { it.exists() }?.let { snapshot ->
                    val status = BusinessStatus.entries.find { it.name == snapshot.getString("status") }
                    if (status == null) {
                        trySend(ApiResult.Failure("El negocio tiene un estado no reconocido."))
                        return@addSnapshotListener
                    }
                    BusinessRequest(
                        id = snapshot.id, ownerUid = snapshot.getString("ownerUid").orEmpty(),
                        businessName = snapshot.getString("businessName").orEmpty(), ruc = snapshot.getString("ruc").orEmpty(),
                        legalAddress = snapshot.getString("legalAddress").orEmpty(), phone = snapshot.getString("phone").orEmpty(),
                        email = snapshot.getString("email").orEmpty(), category = snapshot.getString("category") ?: "Restaurante",
                        status = status, observations = snapshot.getString("observations"),
                        suspensionReason = snapshot.getString("suspensionReason"), reviewedBy = snapshot.getString("reviewedBy"),
                        reviewedAt = snapshot.getTimestamp("reviewedAt")?.toDate()?.time,
                    )
                }))
            }
        }

        awaitClose { listener.remove() }
    }

    override fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>> = callbackFlow {
        try { access.requireAdmin(forceRefresh = false) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { trySend(ApiResult.Failure("No tienes permisos administrativos.")); close(); return@callbackFlow }
        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar solicitudes.", error))
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                if (!doc.exists()) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                val statusStr = data["status"] as? String ?: "PENDING_REVIEW"
                BusinessRequest(
                    id = doc.id,
                    ownerUid = data["ownerUid"] as? String ?: doc.id,
                    businessName = data["businessName"] as? String ?: "",
                    ruc = data["ruc"] as? String ?: "",
                    legalAddress = data["legalAddress"] as? String ?: "",
                    phone = data["phone"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    category = data["category"] as? String ?: "Restaurante",
                    status = try { BusinessStatus.valueOf(statusStr) } catch (e: Exception) { BusinessStatus.PENDING_REVIEW },
                    observations = data["observations"] as? String,
                    suspensionReason = data["suspensionReason"] as? String,
                    reviewedBy = data["reviewedBy"] as? String,
                    reviewedAt = (data["reviewedAt"] as? Timestamp)?.toDate()?.time,
                )
            } ?: emptyList()

            trySend(ApiResult.Success(list))
        }

        awaitClose { listener.remove() }
    }

    override suspend fun submitBusinessRequest(request: BusinessRequest) = saveRequest(request, false)
    override suspend fun resubmitBusinessRequest(request: BusinessRequest) = saveRequest(request, true)

    private suspend fun saveRequest(request: BusinessRequest, resubmission: Boolean): ApiResult<BusinessRequest> = operation {
        val user = access.requireUser()
        require(request.ownerUid == user.uid && (request.id.isBlank() || request.id == user.uid)) { "Propietario inválido." }
        require(request.businessName.isNotBlank() && request.legalAddress.isNotBlank() &&
            request.ruc.matches(Regex("[0-9]{11}")) && request.phone.isNotBlank() && request.email.isNotBlank()) {
            "Completa los datos obligatorios del negocio."
        }
        commercial.execute(CommercialMutation(if (resubmission) "RESUBMIT" else "REGISTER", business = CommercialBusiness(
            request.businessName.trim(), request.ruc, request.legalAddress.trim(), request.phone.trim(), request.email.trim(), request.category,
        )), user.uid, user)
        request.copy(id = user.uid, ownerUid = user.uid, status = BusinessStatus.PENDING_REVIEW,
            observations = null, reviewedBy = null, reviewedAt = null, suspensionReason = null)
    }

    override suspend fun evaluateBusinessRequest(requestId: String, status: BusinessStatus,
        observations: String?, reviewerEmail: String): ApiResult<Unit> = operation {
        require(status in setOf(BusinessStatus.APPROVED, BusinessStatus.OBSERVED, BusinessStatus.REJECTED))
        require(status == BusinessStatus.APPROVED || !observations.isNullOrBlank()) { "Indica el motivo de la decisión." }
        if (status == BusinessStatus.APPROVED) {
            val admin = access.requireAdmin()
            commercial.execute(CommercialMutation("APPROVE", businessId = requestId), admin.uid, admin)
        } else {
            auditedTransition(requestId, BusinessStatus.PENDING_REVIEW, status,
                if (status == BusinessStatus.OBSERVED) "OBSERVAR_NEGOCIO" else "RECHAZAR_NEGOCIO",
                observations?.trim(), mapOf("observations" to observations?.trim()))
        }
    }

    override suspend fun suspendBusiness(requestId: String, reason: String, adminEmail: String,
        businessName: String, ruc: String): ApiResult<BusinessSuspensionResult> = operation {
        require(reason.isNotBlank()) { "Indica el motivo de suspensión." }
        val admin = access.requireAdmin()
        // La consulta obtiene los IDs; la transacción vuelve a leer cada local antes de escribir.
        val listedStores = storesRef.whereEqualTo("businessId", requestId).get(Source.SERVER).await()
        check(!listedStores.metadata.isFromCache && !listedStores.metadata.hasPendingWrites()) {
            "No se pudieron verificar los locales del negocio en el servidor."
        }
        val storeReferences = listedStores.documents.map { it.reference }
        val businessReference = collectionRef.document(requestId)
        val businessAudit = auditLogsRef.document()
        val suspensionTask = firestore.runTransaction { transaction ->
            val business = transaction.get(businessReference)
            check(business.exists() && business.getString("status") == BusinessStatus.APPROVED.name) {
                "El estado del negocio cambió. Actualiza la lista antes de continuar."
            }
            val stores = storeReferences.map(transaction::get)
            check(stores.all { it.exists() && it.getString("businessId") == requestId }) {
                "Los locales del negocio cambiaron. Actualiza la lista antes de continuar."
            }
            val activeStores = stores.filter { it.getString("status") == StoreStatus.ACTIVE.name }
            // Límite conservador: cada local activo exige su actualización y auditoría
            // dentro del máximo de lecturas de documentos impuesto por las reglas.
            check(activeStores.size <= 8) {
                "Hay demasiados locales activos para suspenderlos en una sola operación segura."
            }
            check(access.requireUser().uid == admin.uid) { "La sesión cambió." }

            transaction.update(businessReference, mapOf(
                "status" to BusinessStatus.SUSPENDED.name,
                "suspensionReason" to reason.trim(),
                "reviewedBy" to (admin.email ?: admin.uid),
                "reviewedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditId" to businessAudit.id,
            ))
            transaction.set(businessAudit, mapOf(
                "adminUid" to admin.uid, "adminEmail" to (admin.email ?: admin.uid),
                "action" to "SUSPENDER_NEGOCIO", "targetBusinessId" to requestId,
                "targetBusinessName" to business.getString("businessName").orEmpty(),
                "targetRuc" to business.getString("ruc").orEmpty(), "details" to reason.trim(),
                "previousStatus" to BusinessStatus.APPROVED.name,
                "newStatus" to BusinessStatus.SUSPENDED.name,
                "timestamp" to FieldValue.serverTimestamp(),
            ))
            activeStores.forEach { store ->
                val audit = auditLogsRef.document()
                transaction.update(store.reference, mapOf(
                    "status" to StoreStatus.INACTIVE.name,
                    "lastAuditId" to audit.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ))
                transaction.set(audit, mapOf(
                    "adminUid" to admin.uid, "adminEmail" to (admin.email ?: admin.uid),
                    "action" to "INACTIVAR_LOCAL_POR_SUSPENSION_NEGOCIO",
                    "targetStoreId" to store.id, "targetBusinessId" to requestId,
                    "targetBusinessName" to store.getString("name").orEmpty(),
                    "targetRuc" to "", "details" to reason.trim(),
                    "previousStatus" to StoreStatus.ACTIVE.name,
                    "newStatus" to StoreStatus.INACTIVE.name,
                    "timestamp" to FieldValue.serverTimestamp(),
                ))
            }
        }
        try {
            suspensionTask.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseFirestoreException) {
            if (error.code in setOf(
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    FirebaseFirestoreException.Code.UNKNOWN,
                    FirebaseFirestoreException.Code.INTERNAL,
                )) {
                throw IllegalStateException(
                    "No se pudo confirmar si la suspensión se aplicó. Actualiza el negocio y sus locales antes de repetir la operación.",
                    error,
                )
            }
            throw error
        }

        // Una creación posterior a la consulta inicial no formó parte de la transacción.
        // La transacción ya fue confirmada: un problema aquí no revierte la suspensión.
        try {
            val remaining = storesRef.whereEqualTo("businessId", requestId)
                .get(Source.SERVER).await()
            when {
                remaining.metadata.isFromCache || remaining.metadata.hasPendingWrites() ->
                    BusinessSuspensionResult.COMMITTED_UNVERIFIED
                remaining.documents.any { it.getString("status") == StoreStatus.ACTIVE.name } ->
                    BusinessSuspensionResult.COMMITTED_ACTIVE_STORES
                else -> BusinessSuspensionResult.VERIFIED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BusinessSuspensionResult.COMMITTED_UNVERIFIED
        }
    }

    override suspend fun reactivateBusiness(requestId: String, adminEmail: String,
        businessName: String, ruc: String): ApiResult<Unit> = operation {
        auditedTransition(requestId, BusinessStatus.SUSPENDED, BusinessStatus.APPROVED,
            "REACTIVAR_NEGOCIO", "Negocio reactivado.", mapOf("suspensionReason" to null))
    }

    private suspend fun auditedTransition(id: String, previous: BusinessStatus, target: BusinessStatus,
        action: String, details: String?, extra: Map<String, Any?>) {
        val admin = access.requireAdmin()
        val reference = collectionRef.document(id)
        val audit = auditLogsRef.document()
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
            check(existing.exists() && existing.getString("status") == previous.name) {
                "El estado cambió. Actualiza la lista antes de continuar."
            }
            check(access.requireUser().uid == admin.uid) { "La sesión cambió." }
            transaction.update(reference, extra + mapOf(
                "status" to target.name, "reviewedBy" to (admin.email ?: admin.uid),
                "reviewedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditId" to audit.id,
            ))
            transaction.set(audit, mapOf(
                "adminUid" to admin.uid, "adminEmail" to (admin.email ?: admin.uid), "action" to action,
                "targetBusinessId" to id, "targetBusinessName" to existing.getString("businessName").orEmpty(),
                "targetRuc" to existing.getString("ruc").orEmpty(), "details" to details,
                "previousStatus" to previous.name, "newStatus" to target.name,
                "timestamp" to FieldValue.serverTimestamp(),
            ))
        }.await()
    }

    private suspend fun <T> operation(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { ApiResult.Failure(error.message ?: "No se pudo completar la operación.", error) }

    override fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>> = callbackFlow {
        try { access.requireAdmin(forceRefresh = false) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { trySend(ApiResult.Failure("No tienes permisos administrativos.")); close(); return@callbackFlow }
        val listener = auditLogsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar auditoría.", error))
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                if (!doc.exists()) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                AuditLog(
                    id = doc.id,
                    adminEmail = data["adminEmail"] as? String ?: "",
                    action = data["action"] as? String ?: "",
                    targetBusinessName = data["targetBusinessName"] as? String ?: "",
                    targetRuc = data["targetRuc"] as? String ?: "",
                    details = data["details"] as? String,
                    timestamp = (data["timestamp"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                )
            }?.sortedByDescending { it.timestamp } ?: emptyList()

            trySend(ApiResult.Success(list))
        }

        awaitClose { listener.remove() }
    }

}
