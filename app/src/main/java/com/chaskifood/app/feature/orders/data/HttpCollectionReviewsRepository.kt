package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.orders.domain.*
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.Response
import retrofit2.http.*

/** Wire format of foodApi E5.4a (`/api/v1/admin/collection-reviews`). */
@Serializable internal data class CollectionReviewsResponse(val contractVersion: Int, val items: List<CollectionReviewResponse>,
    val truncated: Boolean = false)
@Serializable internal data class CollectionReviewResponse(val orderId: String, val shortId: String, val kind: String, val storeId: String,
    val storeName: String? = null, val expectedMinor: Long, val declaredMinor: Long, val method: String, val currency: String,
    val openedAt: String? = null, val resolution: CollectionResolutionResponse? = null)
@Serializable internal data class CollectionResolutionResponse(val outcome: String, val confirmedMinor: Long, val shortfallMinor: Long,
    val excessMinor: Long, val excessStatus: String)
/** RESOLVE and RECORD_EXCESS_REFUND share one endpoint; fields absent for an action default to null. */
@Serializable internal data class ReconciliationResponse(val contractVersion: Int, val orderId: String, val action: String,
    val outcome: String? = null, val paymentStatus: String? = null, val expectedMinor: Long? = null, val declaredMinor: Long? = null,
    val confirmedMinor: Long? = null, val shortfallMinor: Long? = null, val excessMinor: Long? = null, val excessStatus: String? = null,
    val replayed: Boolean = false)

internal interface CollectionReviewsApi {
    @GET("admin/collection-reviews")
    suspend fun reviews(@Header("Authorization") authorization: String): Response<CollectionReviewsResponse>
    @POST("admin/collection-reviews/{orderId}/resolve")
    suspend fun resolve(@Path("orderId") orderId: String, @Header("Authorization") authorization: String,
        @Body body: JsonObject): Response<ReconciliationResponse>
}

/** Admin token from the existing access control (claim `admin`); foodApi re-checks admin + verified email on every call. */
internal class HttpCollectionReviewsRepository @Inject constructor(private val api: CollectionReviewsApi,
    private val access: FirebaseAccessControl) : CollectionReviewsRepository {
    private suspend fun bearer(forceRefresh: Boolean): String? {
        val user = runCatching { access.requireAdmin(forceRefresh) }.getOrNull() ?: return null
        return user.getIdToken(false).await().token?.let { "Bearer $it" }
    }

    override suspend fun reviews(): CollectionReviewsResult = try { withTimeoutOrNull(15_000) {
        val token = bearer(false) ?: return@withTimeoutOrNull CollectionReviewsResult.AccessDenied
        val response = api.reviews(token)
        when (response.code()) {
            200 -> { val body = requireNotNull(response.body()); require(body.contractVersion == 1)
                CollectionReviewsResult.Loaded(body.items.map { it.toDomain() }, body.truncated) }
            401 -> CollectionReviewsResult.AuthenticationRequired
            403 -> CollectionReviewsResult.AccessDenied
            else -> CollectionReviewsResult.Unavailable
        }
    } ?: CollectionReviewsResult.Unavailable } catch (e: CancellationException) { throw e } catch (_: Exception) { CollectionReviewsResult.Unavailable }

    override suspend fun resolve(orderId: String, command: ResolveCollectionCommand) = post(orderId, buildJsonObject {
        put("action", "RESOLVE"); put("operationId", command.operationId); put("outcome", command.outcome.name)
        put("confirmedAmountMinor", command.confirmedMinor); put("evidence", command.evidence)
    }) { body ->
        require(body.action == "RESOLVE")
        ReconciliationResult.Resolved(CollectionResolution(CollectionOutcome.valueOf(requireNotNull(body.outcome)),
            requireNotNull(body.confirmedMinor), requireNotNull(body.shortfallMinor), requireNotNull(body.excessMinor),
            ExcessStatus.valueOf(requireNotNull(body.excessStatus))), requireNotNull(body.expectedMinor),
            requireNotNull(body.declaredMinor), body.replayed)
    }

    override suspend fun recordRefund(orderId: String, command: RecordExcessRefundCommand) = post(orderId, buildJsonObject {
        put("action", "RECORD_EXCESS_REFUND"); put("operationId", command.operationId)
        put("refundedAmountMinor", command.refundedMinor); put("evidence", command.evidence)
    }) { body ->
        require(body.action == "RECORD_EXCESS_REFUND" && body.excessStatus == "REFUNDED")
        ReconciliationResult.Refunded(requireNotNull(body.excessMinor), body.replayed)
    }

    private suspend fun post(orderId: String, json: JsonObject, ok: (ReconciliationResponse) -> ReconciliationResult): ReconciliationResult {
        if (!orderId.matches(Regex("[0-9a-f]{64}"))) return ReconciliationResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            // Mutations confirm the claim with a fresh token, as the other admin operations do.
            val token = bearer(true) ?: return@withTimeoutOrNull ReconciliationResult.AccessDenied
            val response = api.resolve(orderId, token, json)
            when (response.code()) {
                200 -> { val body = requireNotNull(response.body()); require(body.contractVersion == 1 && body.orderId == orderId); ok(body) }
                400, 422 -> ReconciliationResult.Invalid
                401 -> ReconciliationResult.AuthenticationRequired
                403 -> ReconciliationResult.AccessDenied
                404 -> ReconciliationResult.NotFound
                409 -> when (errorCode(response.errorBody()?.string())) {
                    "REVIEW_NOT_OPEN" -> ReconciliationResult.ReviewNotOpen
                    "REVIEW_NOT_ELIGIBLE" -> ReconciliationResult.ReviewNotEligible
                    "EXCESS_NOT_PENDING" -> ReconciliationResult.ExcessNotPending
                    "IDEMPOTENCY_CONFLICT" -> ReconciliationResult.OperationConflict
                    else -> ReconciliationResult.ReviewNotOpen
                }
                else -> ReconciliationResult.Unavailable
            }
        } ?: ReconciliationResult.Unavailable } catch (e: CancellationException) { throw e } catch (_: Exception) { ReconciliationResult.Unavailable }
    }

    private fun errorCode(body: String?): String? = runCatching {
        Json.parseToJsonElement(requireNotNull(body)).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
}

internal fun CollectionReviewResponse.toDomain(): CollectionReview {
    require(currency == "PEN")
    return CollectionReview(orderId, CollectionReviewKind.valueOf(kind), storeName, expectedMinor, declaredMinor, method,
        openedAt?.let { Instant.parse(it).toEpochMilli() },
        resolution?.let { CollectionResolution(CollectionOutcome.valueOf(it.outcome), it.confirmedMinor, it.shortfallMinor,
            it.excessMinor, ExcessStatus.valueOf(it.excessStatus)) })
}
