package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

@Serializable data class AccessFactsDto(val uid: String, val revision: Long, val exclusivity: String,
    val ownership: OwnershipDto?, val assignments: List<AssignmentDto>, val stores: List<StoreDto>)
@Serializable data class OwnershipDto(val businessId: String, val ownerUid: String, val status: String, val suspensionReason: String?)
@Serializable data class AssignmentDto(val id: String, val businessId: String, val email: String,
    val assignedStoreIds: List<String>, val bindingState: String, val boundUid: String?)
@Serializable data class StoreDto(val storeId: String, val businessId: String, val status: String,
    val operationalStatus: String, val businessStatus: String)
@Serializable private data class AccessErrorDto(val error: String)
interface AccessFactsApi {
    @GET("access/facts") suspend fun facts(@Header("Authorization") authorization: String): Response<AccessFactsDto>
}
enum class AccessCheckProblem { AUTHENTICATION_REQUIRED, FORBIDDEN, RECONCILIATION_REQUIRED, PREPARATION_REQUIRED,
    EMAIL_VERIFICATION_REQUIRED, CONFLICT, INVALID_RESPONSE, UNAVAILABLE, ENVIRONMENT_UNAVAILABLE, EXPIRED }
data class AccessFactsResult(val facts: AccessFacts, val revision: Long? = null, val problem: AccessCheckProblem? = null)
interface AccessFactsReader { suspend fun read(identity: AccessIdentityState): AccessFactsResult }

/** Solo respuesta completa autoritativa: ningun error se transforma en cliente. */
class RemoteAccessFacts(private val api: AccessFactsApi, private val credentials: AccessCredentialSource,
    private val json: Json, private val enabled: Boolean) : AccessFactsReader {
    override suspend fun read(identity: AccessIdentityState): AccessFactsResult {
        if (!enabled) return AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.ENVIRONMENT_UNAVAILABLE)
        val session = (identity.identity as? AccessIdentity.Authenticated)?.session
            ?: return AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.AUTHENTICATION_REQUIRED)
        return try {
            val response = api.facts("Bearer ${credentials.token(identity)}")
            if (response.isSuccessful) {
                val dto = checkNotNull(response.body())
                AccessFactsResult(map(dto, identity.identity as AccessIdentity.Authenticated), dto.revision)
            } else {
                val code = try { json.decodeFromString<AccessErrorDto>(response.errorBody()?.string().orEmpty()).error } catch (_: Exception) { "" }
                val problem = when {
                    response.code() == 401 -> AccessCheckProblem.AUTHENTICATION_REQUIRED
                    response.code() == 409 && code == "AUTHORIZATION_CONFLICT" -> AccessCheckProblem.CONFLICT
                    response.code() == 409 && code == "PREPARATION_REQUIRED" -> AccessCheckProblem.PREPARATION_REQUIRED
                    response.code() == 409 && code in setOf("RECONCILIATION_REQUIRED", "BINDING_OWNED_BY_ANOTHER_UID") -> AccessCheckProblem.RECONCILIATION_REQUIRED
                    response.code() == 403 && code == "EMAIL_VERIFICATION_REQUIRED" -> AccessCheckProblem.EMAIL_VERIFICATION_REQUIRED
                    response.code() == 403 -> AccessCheckProblem.FORBIDDEN
                    else -> AccessCheckProblem.UNAVAILABLE
                }
                AccessFactsResult(if (problem == AccessCheckProblem.CONFLICT) AccessFacts.ConfirmedConflict(session)
                    else if (problem in setOf(AccessCheckProblem.RECONCILIATION_REQUIRED, AccessCheckProblem.PREPARATION_REQUIRED,
                        AccessCheckProblem.EMAIL_VERIFICATION_REQUIRED)) AccessFacts.Unverifiable else AccessFacts.Failed, problem = problem)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.INVALID_RESPONSE) }
        catch (_: IllegalStateException) { AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.INVALID_RESPONSE) }
        catch (_: Exception) { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
    }
    internal fun map(dto: AccessFactsDto, identity: AccessIdentity.Authenticated): AccessFacts.Checked {
        require(dto.uid == identity.session.uid && dto.revision in 0..9_007_199_254_740_991L && dto.exclusivity == "VERIFIED")
        val owner = dto.ownership?.let {
            require(it.businessId == identity.session.uid && it.ownerUid == identity.session.uid)
            AccessOwnership(it.businessId, it.ownerUid, AccessBusinessStatus.valueOf(it.status), it.suspensionReason)
        }
        require(dto.assignments.map { it.id }.distinct().size == dto.assignments.size)
        val assignments = dto.assignments.flatMap { a ->
            require(a.businessId.isNotBlank() && a.id == "${a.businessId}_${a.email}" && a.bindingState == "VERIFIED" &&
                a.boundUid == identity.session.uid && identity.email?.trim()?.lowercase(java.util.Locale.ROOT) == a.email &&
                identity.verification == EmailVerification.VERIFIED && a.assignedStoreIds.isNotEmpty() &&
                a.assignedStoreIds.distinct().size == a.assignedStoreIds.size)
            a.assignedStoreIds.map { id -> AccessAssignment(a.businessId, id, a.boundUid, a.email, AccessBinding.VERIFIED) }
        }
        val stores = dto.stores.map {
            require(it.businessId.isNotBlank() && it.storeId.isNotBlank())
            AccessStore(it.businessId, it.storeId, AccessBusinessStatus.valueOf(it.businessStatus),
                AccessStoreStatus.valueOf(it.status), AccessOperationalStatus.valueOf(it.operationalStatus))
        }
        require(stores.map { it.storeId }.distinct().size == stores.size)
        require(assignments.all { a -> stores.any { it.businessId == a.businessId && it.storeId == a.storeId } })
        require(stores.all { s -> (owner?.status == AccessBusinessStatus.APPROVED && s.businessId == owner.businessId && s.businessStatus == owner.status) ||
            assignments.any { it.businessId == s.businessId && it.storeId == s.storeId } })
        require(owner?.status != AccessBusinessStatus.SUSPENDED || stores.isEmpty())
        return AccessFacts.Checked(identity.session, owner, assignments, stores, AccessExclusivity.VERIFIED)
    }
}
