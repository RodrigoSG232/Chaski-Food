package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.AuditLog
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import kotlinx.coroutines.CancellationException
import com.google.firebase.firestore.FieldValue
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
) : BusinessRepository {
    private val collectionRef by lazy { firestore.collection("business_requests") }
    private val auditLogsRef by lazy { firestore.collection("audit_logs") }

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
        try { access.requireAdmin() } catch (cancelled: CancellationException) { throw cancelled }
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
        val reference = collectionRef.document(user.uid)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
            check(access.requireUser().uid == user.uid) { "La sesión cambió." }
            if (resubmission) {
                check(existing.getString("ownerUid") == user.uid && existing.getString("status") == "OBSERVED") {
                    "Solo puedes reenviar una solicitud observada."
                }
            } else check(!existing.exists()) { "Ya tienes una solicitud registrada. Consulta su estado." }
            val fields = mapOf(
                "ownerUid" to user.uid, "businessName" to request.businessName.trim(), "ruc" to request.ruc,
                "legalAddress" to request.legalAddress.trim(), "phone" to request.phone.trim(),
                "email" to request.email.trim(), "category" to request.category,
                "status" to BusinessStatus.PENDING_REVIEW.name, "observations" to null,
                "suspensionReason" to null, "reviewedBy" to null, "reviewedAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (resubmission) transaction.update(reference, fields)
            else transaction.set(reference, fields + ("createdAt" to FieldValue.serverTimestamp()))
        }.await()
        request.copy(id = user.uid, ownerUid = user.uid, status = BusinessStatus.PENDING_REVIEW,
            observations = null, reviewedBy = null, reviewedAt = null, suspensionReason = null)
    }

    override suspend fun evaluateBusinessRequest(requestId: String, status: BusinessStatus,
        observations: String?, reviewerEmail: String): ApiResult<Unit> = operation {
        require(status in setOf(BusinessStatus.APPROVED, BusinessStatus.OBSERVED, BusinessStatus.REJECTED))
        require(status == BusinessStatus.APPROVED || !observations.isNullOrBlank()) { "Indica el motivo de la decisión." }
        auditedTransition(requestId, BusinessStatus.PENDING_REVIEW, status,
            when (status) {
                BusinessStatus.APPROVED -> "APROBAR_NEGOCIO"
                BusinessStatus.OBSERVED -> "OBSERVAR_NEGOCIO"
                else -> "RECHAZAR_NEGOCIO"
            }, observations?.trim(), mapOf("observations" to observations?.trim()))
    }

    override suspend fun suspendBusiness(requestId: String, reason: String, adminEmail: String,
        businessName: String, ruc: String): ApiResult<Unit> = operation {
        require(reason.isNotBlank()) { "Indica el motivo de suspensión." }
        auditedTransition(requestId, BusinessStatus.APPROVED, BusinessStatus.SUSPENDED,
            "SUSPENDER_NEGOCIO", reason.trim(), mapOf("suspensionReason" to reason.trim()))
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
        try { access.requireAdmin() } catch (cancelled: CancellationException) { throw cancelled }
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
