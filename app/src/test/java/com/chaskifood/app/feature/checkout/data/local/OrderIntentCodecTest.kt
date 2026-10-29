package com.chaskifood.app.feature.checkout.data.local

import com.chaskifood.app.feature.checkout.domain.OrderFingerprints
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class OrderIntentCodecTest {
    private val vectors = JsonParser.parseReader(requireNotNull(javaClass.classLoader?.getResourceAsStream(
        "hu16/confirmation-v1.json")).reader(Charsets.UTF_8)).asJsonObject
    private fun payloads(): List<String> = vectors["validCases"].asJsonArray.map { v ->
        val accepted = JsonParser.parseString(v.asJsonObject["expected"].asJsonObject["canonicalConsent"].asString)
            .asJsonObject["accepted"].toString()
        "{\"accepted\":$accepted,\"intentId\":\"${vectors["intentId"].asString}\",\"version\":\"hu16-request-v1\"}"
    }
    @Test fun roundTripPreservesAllThirteenSharedVectorsAndHashes() {
        for ((index, payload) in payloads().withIndex()) {
            val intent = OrderIntentCodec.decode(payload)
            assertEquals(payload, OrderIntentCodec.encode(intent))
            assertEquals(vectors["validCases"].asJsonArray[index].asJsonObject["expected"].asJsonObject["requestHash"].asString,
                intent.requestHash)
            assertEquals(intent.accepted.deliveryQuote.quoteId, intent.accepted.deliveryQuote.quote.quoteId)
        }
    }
    @Test fun corruptedVersionMoneyUnknownFieldsAndNonCanonicalPayloadAreRejected() {
        val good = payloads().first()
        for (bad in listOf(good.replace("hu16-request-v1", "hu16-request-v2"), good + " ",
            good.replace("2200", "2201"), good.replace("\"quantity\":2", "\"quantity\":2.5"),
            good.replace("\"version\":", "\"unknown\":null,\"version\":"))) {
            assertTrue(runCatching { OrderIntentCodec.decode(bad) }.isFailure)
        }
    }
    @Test fun canonicalPayloadContainsNoAuthOrSessionMaterial() {
        val payload = payloads().first()
        assertFalse(payload.contains("token", true)); assertFalse(payload.contains("sessionGeneration"))
        val intent = OrderIntentCodec.decode(payload)
        assertEquals(OrderFingerprints.canonicalRequest(intent), payload)
        assertNull(intent.accepted.buyerPhone)
    }
}
