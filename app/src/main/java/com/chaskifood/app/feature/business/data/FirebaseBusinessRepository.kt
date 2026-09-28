package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.AuditLog
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseBusinessRepository @Inject constructor() : BusinessRepository {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val collectionRef by lazy { firestore.collection("business_requests") }
    private val auditLogsRef by lazy { firestore.collection("audit_logs") }

    private val currentAuthUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun getBusinessRequest(ownerUid: String): Flow<ApiResult<BusinessRequest?>> = callbackFlow {
        val targetUid = ownerUid.ifBlank { currentAuthUid }

        if (targetUid.isBlank()) {
            trySend(ApiResult.Success(null))
            awaitClose { }
            return@callbackFlow
        }

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ApiResult.Failure(error.localizedMessage ?: "Error al consultar estado de negocio.", error))
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val doc = snapshot.documents.firstOrNull {
                    it.id == targetUid || it.getString("ownerUid") == targetUid
                }

                if (doc != null && doc.exists()) {
                    val data = doc.data ?: emptyMap<String, Any>()
                    val statusStr = data["status"] as? String ?: "PENDING_REVIEW"
                    val request = BusinessRequest(
                        id = doc.id,
                        ownerUid = data["ownerUid"] as? String ?: targetUid,
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
                    trySend(ApiResult.Success(request))
                } else {
                    trySend(ApiResult.Success(null))
                }
            } else {
                trySend(ApiResult.Success(null))
            }
        }

        awaitClose { listener.remove() }
    }

    override fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>> = callbackFlow {
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

    override suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> {
        return try {
            val targetUid = request.ownerUid.ifBlank { currentAuthUid }
            val docId = targetUid.ifBlank { "biz_${System.currentTimeMillis()}" }
            val payload = mapOf(
                "ownerUid" to docId,
                "businessName" to request.businessName,
                "ruc" to request.ruc,
                "legalAddress" to request.legalAddress,
                "phone" to request.phone,
                "email" to request.email,
                "category" to request.category,
                "status" to BusinessStatus.PENDING_REVIEW.name,
                "observations" to null,
                "suspensionReason" to null,
                "updatedAt" to Timestamp.now(),
            )

            collectionRef.document(docId).set(payload).await()
            val created = request.copy(
                id = docId,
                ownerUid = docId,
                status = BusinessStatus.PENDING_REVIEW,
                observations = null,
            )
            ApiResult.Success(created)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al guardar solicitud en Firestore.", e)
        }
    }

    override suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> {
        return submitBusinessRequest(request)
    }

    override suspend fun evaluateBusinessRequest(
        requestId: String,
        status: BusinessStatus,
        observations: String?,
        reviewerEmail: String,
    ): ApiResult<Unit> {
        return try {
            val updates = mapOf(
                "status" to status.name,
                "observations" to observations,
                "reviewedBy" to reviewerEmail,
                "reviewedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
            )
            collectionRef.document(requestId).update(updates).await()

            val actionName = when (status) {
                BusinessStatus.APPROVED -> "APROBAR_NEGOCIO"
                BusinessStatus.OBSERVED -> "OBSERVAR_NEGOCIO"
                BusinessStatus.REJECTED -> "RECHAZAR_NEGOCIO"
                else -> "EVALUAR_NEGOCIO"
            }

            val docSnapshot = collectionRef.document(requestId).get().await()
            val bizName = docSnapshot.getString("businessName") ?: ""
            val bizRuc = docSnapshot.getString("ruc") ?: ""

            recordAuditLog(
                AuditLog(
                    adminEmail = reviewerEmail,
                    action = actionName,
                    targetBusinessName = bizName,
                    targetRuc = bizRuc,
                    details = observations,
                ),
            )

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al evaluar la solicitud de negocio.", e)
        }
    }

    override suspend fun suspendBusiness(
        requestId: String,
        reason: String,
        adminEmail: String,
        businessName: String,
        ruc: String,
    ): ApiResult<Unit> {
        return try {
            val updates = mapOf(
                "status" to BusinessStatus.SUSPENDED.name,
                "suspensionReason" to reason,
                "reviewedBy" to adminEmail,
                "reviewedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
            )
            collectionRef.document(requestId).update(updates).await()

            recordAuditLog(
                AuditLog(
                    adminEmail = adminEmail,
                    action = "SUSPENDER_NEGOCIO",
                    targetBusinessName = businessName,
                    targetRuc = ruc,
                    details = "Motivo: $reason",
                ),
            )

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al suspender el negocio.", e)
        }
    }

    override suspend fun reactivateBusiness(
        requestId: String,
        adminEmail: String,
        businessName: String,
        ruc: String,
    ): ApiResult<Unit> {
        return try {
            val updates = mapOf(
                "status" to BusinessStatus.APPROVED.name,
                "suspensionReason" to null,
                "reviewedBy" to adminEmail,
                "reviewedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
            )
            collectionRef.document(requestId).update(updates).await()

            recordAuditLog(
                AuditLog(
                    adminEmail = adminEmail,
                    action = "REACTIVAR_NEGOCIO",
                    targetBusinessName = businessName,
                    targetRuc = ruc,
                    details = "Negocio reactivado y habilitado para ventas.",
                ),
            )

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al reactivar el negocio.", e)
        }
    }

    override fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>> = callbackFlow {
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

    override suspend fun recordAuditLog(log: AuditLog): ApiResult<Unit> {
        return try {
            val docId = "audit_${System.currentTimeMillis()}"
            val payload = mapOf(
                "adminEmail" to log.adminEmail,
                "action" to log.action,
                "targetBusinessName" to log.targetBusinessName,
                "targetRuc" to log.targetRuc,
                "details" to log.details,
                "timestamp" to Timestamp.now(),
            )
            auditLogsRef.document(docId).set(payload).await()
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al registrar auditoría.", e)
        }
    }
}