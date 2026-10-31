package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.data.remote.*
import com.chaskifood.app.feature.checkout.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

internal sealed interface OrderHttpResult {
    data class Outcome(val result: OrderAttemptResult) : OrderHttpResult
    data object Conflict : OrderHttpResult
}

internal class HttpOrderReader @Inject constructor(private val api: OrderApi, private val tokens: BuyerTokenSource) {
    suspend fun execute(intent: OrderIntent, generation: Long, send: Boolean): OrderHttpResult = try {
        withTimeoutOrNull(20_000) {
            val token = tokens.token(intent.ownerUid, generation)
            val response = if (send) api.confirm("Bearer $token", OrderWireCodec.submission(intent))
                else api.recover("Bearer $token", intent.intentId)
            val body = response.body() ?: response.errorBody()?.string()?.let { Json.parseToJsonElement(it).jsonObject }
                ?: return@withTimeoutOrNull unknown()
            require(OrderWireCodec.integer(body, "contractVersion") == 1L)
            val id = body["intentId"]?.jsonPrimitive?.content
            if (id != null) require(id == intent.intentId)
            val code = body["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            if (response.code() == 409 && id == intent.intentId && code == "INTENT_PAYLOAD_MISMATCH")
                return@withTimeoutOrNull OrderHttpResult.Conflict
            when (body["outcome"]?.jsonPrimitive?.content) {
                "SUCCEEDED" -> {
                    require(response.code() in setOf(200, 201) && id == intent.intentId)
                    val r = body.getValue("receipt").jsonObject
                    require(OrderWireCodec.text(r, "intentId") == intent.intentId)
                    require(OrderWireCodec.text(r, "status") == "PENDING_CONFIRMATION")
                    // Exact comparison of accepted evidence, including UID, prices and snapshots.
                    require(OrderWireCodec.sameEvidence(r.getValue("accepted"), OrderWireCodec.accepted(intent.accepted)))
                    OrderHttpResult.Outcome(OrderAttemptResult.Succeeded(OrderReceipt(OrderWireCodec.text(r,"orderId"),
                        intent.intentId, intent.accepted, OrderWireCodec.integer(r,"createdAtEpochMillis"))))
                }
                "REJECTED_FINAL" -> {
                    require(id == intent.intentId && response.code() in setOf(200,403,409,422))
                    OrderHttpResult.Outcome(OrderAttemptResult.RejectedFinal(OrderRejection.valueOf(OrderWireCodec.text(body,"reason"))))
                }
                "UNKNOWN" -> {
                    require(response.code() in setOf(404,503))
                    val reason = OrderUncertainty.valueOf(OrderWireCodec.text(body,"reason"))
                    if (response.code() == 404) require(id == intent.intentId && code == "ATTEMPT_NOT_OBSERVED" && reason == OrderUncertainty.NOT_OBSERVED)
                    OrderHttpResult.Outcome(OrderAttemptResult.Unknown(reason))
                }
                else -> unknown()
            }
        } ?: unknown(OrderUncertainty.TIMEOUT)
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: java.net.SocketTimeoutException) { unknown(OrderUncertainty.TIMEOUT) }
      catch (_: java.io.IOException) { unknown(OrderUncertainty.CONNECTIVITY) }
      catch (_: Exception) { unknown() }

    private fun unknown(reason: OrderUncertainty = OrderUncertainty.SERVER_UNAVAILABLE) =
        OrderHttpResult.Outcome(OrderAttemptResult.Unknown(reason))
}
