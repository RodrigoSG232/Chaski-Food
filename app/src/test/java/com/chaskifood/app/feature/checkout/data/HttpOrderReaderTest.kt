package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.data.local.OrderIntentCodec
import com.chaskifood.app.feature.checkout.data.remote.*
import com.chaskifood.app.feature.checkout.domain.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HttpOrderReaderTest {
    private fun intent(): OrderIntent {
        val v = JsonParser.parseReader(requireNotNull(javaClass.classLoader?.getResourceAsStream("hu16/confirmation-v1.json")).reader()).asJsonObject
        val a = JsonParser.parseString(v["validCases"].asJsonArray[0].asJsonObject["expected"].asJsonObject["canonicalConsent"].asString).asJsonObject["accepted"]
        return OrderIntentCodec.decode("{\"accepted\":$a,\"intentId\":\"${v["intentId"].asString}\",\"version\":\"hu16-request-v1\"}")
    }
    private val tokens = object : BuyerTokenSource {
        override suspend fun token(uid: String, generation: Long) = "fixture-token"
        override fun isCurrent(uid: String, generation: Long) = true
    }
    private fun api(block: suspend (Boolean, JsonObject?) -> Response<JsonObject>) = object : OrderApi {
        override suspend fun confirm(authorization: String, body: JsonObject): Response<JsonObject> {
            assertEquals("Bearer fixture-token", authorization); return block(true, body)
        }
        override suspend fun recover(authorization: String, intentId: String): Response<JsonObject> {
            assertEquals("Bearer fixture-token", authorization); assertEquals(intent().intentId, intentId); return block(false, null)
        }
    }
    private fun success(i: OrderIntent) = buildJsonObject {
        put("contractVersion", 1); put("intentId", i.intentId); put("outcome", "SUCCEEDED")
        put("receipt", buildJsonObject { put("orderId", "order-ficticio"); put("intentId", i.intentId)
            put("status", "PENDING_CONFIRMATION"); put("createdAtEpochMillis", 1800000001000L); put("accepted", OrderWireCodec.accepted(i.accepted)) })
    }
    @Test fun createdAndRecoveredValidateOriginalEvidenceAndAuthorization() = runTest {
        val i = intent()
        for (status in listOf(200,201)) {
            val reader = HttpOrderReader(api { send, body ->
                if (send) assertEquals(OrderWireCodec.submission(i),body)
                Response.success(status,success(i))
            }, tokens)
            val result = reader.execute(i,0,status==201) as OrderHttpResult.Outcome
            assertTrue(result.result is OrderAttemptResult.Succeeded)
        }
    }
    @Test fun requestUsesExactBackendKeysAndNeverSendsUidOrSession() {
        val body = OrderWireCodec.submission(intent())
        assertEquals(setOf("contractVersion","intentId","storeId","addressId","quoteId","paymentMethod","expected"),body.keys)
        assertFalse(body.getValue("expected").jsonObject.containsKey("ownerUid"))
        assertFalse(body.toString().contains("generation")); assertFalse(body.toString().contains("requestHash"))
        assertFalse(body.getValue("expected").jsonObject.getValue("deliveryQuote").jsonObject.containsKey("quoteId"))
    }
    @Test fun unknown404IsNotRejectionAndConflictRemainsDistinct() = runTest {
        val i=intent()
        val missing=buildJsonObject { put("contractVersion",1);put("intentId",i.intentId);put("outcome","UNKNOWN")
            put("reason","NOT_OBSERVED");put("error",buildJsonObject { put("code","ATTEMPT_NOT_OBSERVED") }) }
        val reader=HttpOrderReader(api { _,_->Response.error(404,missing.toString().toResponseBody()) },tokens)
        assertEquals(OrderHttpResult.Outcome(OrderAttemptResult.Unknown(OrderUncertainty.NOT_OBSERVED)),reader.execute(i,0,false))
        val conflict=buildJsonObject { put("contractVersion",1);put("intentId",i.intentId);put("error",buildJsonObject { put("code","INTENT_PAYLOAD_MISMATCH") }) }
        assertEquals(OrderHttpResult.Conflict,HttpOrderReader(api { _,_->Response.error(409,conflict.toString().toResponseBody()) },tokens).execute(i,0,true))
    }
    @Test fun terminalRejectionRequiresKnownIntentAndReason() = runTest {
        val i=intent()
        val body=buildJsonObject {put("contractVersion",1);put("intentId",i.intentId);put("outcome","REJECTED_FINAL");put("reason","QUOTE_EXPIRED")}
        assertEquals(OrderHttpResult.Outcome(OrderAttemptResult.RejectedFinal(OrderRejection.QUOTE_EXPIRED)),
            HttpOrderReader(api{_,_->Response.error(409,body.toString().toResponseBody())},tokens).execute(i,0,true))
    }
    @Test fun malformedResponsesAndAuthenticationNeverCreateSuccessOrFinalFailure() = runTest {
        for(code in listOf(401,403,404,500,503)) {
            val result=HttpOrderReader(api{_,_->Response.error(code,"{}".toResponseBody())},tokens).execute(intent(),0,true)
            assertTrue((result as OrderHttpResult.Outcome).result is OrderAttemptResult.Unknown)
        }
        val i=intent(); val good=success(i)
        for(bad in listOf(good.toString().replace(i.ownerUid,"other-uid"),good.toString().replace("PENDING_CONFIRMATION","DELIVERED"),
            good.toString().replace("\"totalMinor\":","\"unknownAmount\":"))) {
            assertTrue((HttpOrderReader(api{_,_->Response.success(Json.parseToJsonElement(bad).jsonObject)},tokens)
                .execute(i,0,false) as OrderHttpResult.Outcome).result is OrderAttemptResult.Unknown)
        }
    }
    @Test fun timeoutAndConnectivityRemainRecoverableAndCancellationPropagates() = runTest {
        assertEquals(OrderHttpResult.Outcome(OrderAttemptResult.Unknown(OrderUncertainty.TIMEOUT)),
            HttpOrderReader(api{_,_->delay(30000);Response.success(success(intent()))},tokens).execute(intent(),0,true))
        assertEquals(OrderHttpResult.Outcome(OrderAttemptResult.Unknown(OrderUncertainty.CONNECTIVITY)),
            HttpOrderReader(api{_,_->throw java.io.IOException()},tokens).execute(intent(),0,true))
        val job=async { HttpOrderReader(api{_,_->awaitCancellation()},tokens).execute(intent(),0,true) }
        runCurrent();job.cancel();runCurrent();assertTrue(job.isCancelled)
    }
    @Test fun numericGeometryAcceptsIntegerNotationButMoneyNeverAcceptsFractionsOrStrings() {
        assertTrue(OrderWireCodec.sameEvidence(JsonPrimitive(1250),JsonPrimitive(1250.0)))
        assertFalse(OrderWireCodec.sameEvidence(JsonPrimitive("1000"),JsonPrimitive(1000L)))
        assertFalse(OrderWireCodec.sameEvidence(JsonPrimitive(1000.5),JsonPrimitive(1000L)))
    }
}
