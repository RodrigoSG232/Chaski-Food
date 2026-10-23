package com.chaskifood.app.feature.business.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.business.domain.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseStoreAppealRepository @Inject constructor(
    private val db: FirebaseFirestore, private val access: FirebaseAccessControl,
) : StoreAppealRepository {
    override fun observeAppeals(admin: Boolean): Flow<ApiResult<List<StoreAppeal>>> = callbackFlow {
        try {
            val user = if (admin) access.requireAdmin() else access.requireUser()
            val collection = db.collection("store_appeals")
            val query: Query = if (admin) collection else collection.whereEqualTo("ownerUid", user.uid)
            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) trySend(ApiResult.Failure("No se pudieron cargar las apelaciones. Reintenta.", error))
                else trySend(ApiResult.Success(snapshot?.documents.orEmpty().map { d ->
                    StoreAppeal(d.id, d.getString("storeId").orEmpty(), d.getString("businessId").orEmpty(),
                        d.getString("ownerUid").orEmpty(), d.getString("storeName").orEmpty(),
                        d.getString("suspensionId").orEmpty(), d.getString("suspensionReason").orEmpty(),
                        d.getString("explanation").orEmpty(), d.getString("status").orEmpty(),
                        d.getString("response").orEmpty(), d.getTimestamp("createdAt")?.toDate()?.time ?: 0)
                }.sortedByDescending { it.createdAt }))
            }
            awaitClose { listener.remove() }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { trySend(ApiResult.Failure(e.message, e)); close() }
    }

    override suspend fun submit(storeId: String, explanation: String) = operation {
        val text = validateAppealText(explanation)
        val user = access.requireUser()
        db.runTransaction { tx ->
            val store = tx.get(db.collection("stores").document(storeId))
            val businessId = checkNotNull(store.getString("businessId"))
            val business = tx.get(db.collection("business_requests").document(businessId))
            check(business.getString("ownerUid") == user.uid) { "Solo el dueño puede apelar." }
            check(business.getString("status") == "APPROVED") { "El negocio debe estar habilitado." }
            check(store.getString("status") == "SUSPENDED") { "El local ya no está suspendido." }
            val suspensionId = checkNotNull(store.getString("lastAuditId")) { "Esta suspensión requiere revisión administrativa." }
            val ref = db.collection("store_appeals").document(storeId + "_" + suspensionId)
            check(!tx.get(ref).exists()) { "Ya enviaste una apelación para esta suspensión. Consulta su resultado." }
            check(access.requireUser().uid == user.uid) { "La sesión cambió." }
            tx.set(ref, mapOf("storeId" to storeId, "businessId" to businessId, "ownerUid" to user.uid,
                "storeName" to store.getString("name").orEmpty(), "suspensionId" to suspensionId,
                "suspensionReason" to store.getString("suspensionReason").orEmpty(), "explanation" to text,
                "status" to "PENDING", "response" to "", "reviewedBy" to null, "reviewedAt" to null,
                "createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }

    override suspend fun resolve(id: String, accepted: Boolean, response: String) = operation {
        val text = validateAppealText(response)
        val admin = access.requireAdmin()
        val appealRef = db.collection("store_appeals").document(id)
        val auditRef = db.collection("audit_logs").document()
        db.runTransaction { tx ->
            val appeal = tx.get(appealRef)
            check(appeal.getString("status") == "PENDING") { "La apelación ya fue resuelta." }
            val storeId = checkNotNull(appeal.getString("storeId"))
            val businessId = checkNotNull(appeal.getString("businessId"))
            val storeRef = db.collection("stores").document(storeId)
            val store = tx.get(storeRef)
            val business = tx.get(db.collection("business_requests").document(businessId))
            if (accepted) {
                check(business.getString("status") == "APPROVED") { "Primero debe habilitarse el negocio." }
                check(store.getString("status") == "SUSPENDED" && store.getString("lastAuditId") == appeal.getString("suspensionId")) {
                    "La suspensión cambió. Rechaza esta apelación indicando que ya no corresponde."
                }
            }
            check(access.requireUser().uid == admin.uid) { "La sesión cambió." }
            val status = if (accepted) "ACCEPTED" else "REJECTED"
            tx.update(appealRef, mapOf("status" to status, "response" to text, "reviewedBy" to admin.uid,
                "reviewedAt" to FieldValue.serverTimestamp(), "decisionAuditId" to auditRef.id))
            if (accepted) tx.update(storeRef, mapOf("status" to "ACTIVE", "suspensionReason" to null,
                "lastAuditId" to auditRef.id, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(auditRef, mapOf("adminUid" to admin.uid, "adminEmail" to (admin.email ?: admin.uid),
                "action" to if (accepted) "APROBAR_APELACION_LOCAL" else "RECHAZAR_APELACION_LOCAL",
                "targetAppealId" to id, "targetStoreId" to storeId, "targetBusinessId" to businessId,
                "targetBusinessName" to appeal.getString("storeName"), "targetRuc" to "", "details" to text,
                "previousStatus" to store.getString("status"), "newStatus" to if (accepted) "ACTIVE" else store.getString("status"),
                "timestamp" to FieldValue.serverTimestamp()))
            tx.set(db.collection("store_notices").document(auditRef.id), mapOf(
                "ownerUid" to appeal.getString("ownerUid"), "storeId" to storeId, "businessId" to businessId,
                "title" to "${appeal.getString("storeName")}: apelación ${if (accepted) "aceptada" else "rechazada"}",
                "message" to text, "read" to false, "createdAt" to FieldValue.serverTimestamp(), "auditId" to auditRef.id))
        }.await()
    }

    private suspend fun operation(block: suspend () -> Unit): ApiResult<Unit> = try {
        block(); ApiResult.Success(Unit)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { ApiResult.Failure(e.message ?: "No se pudo completar la operación.", e) }
}
