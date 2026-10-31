package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.orders.domain.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class OrderConfirmationTest {
    private val vectors = JsonParser.parseReader(requireNotNull(javaClass.classLoader?.getResourceAsStream(
        "hu16/confirmation-v1.json")).reader(Charsets.UTF_8)).asJsonObject
    private val id = vectors["intentId"].asString
    private fun vector(name: String): JsonObject = vectors["validCases"].asJsonArray
        .first { it.asJsonObject["name"].asString == name }.asJsonObject
    private fun purchase(name: String = "base") = parse(vector(name)["purchase"].asJsonObject)
    private fun long(o: JsonObject, key: String) = o[key].asBigDecimal.longValueExact()
    private fun nullable(o: JsonObject, key: String) = o[key].let { if (it.isJsonNull) null else it.asString }
    private fun location(o: JsonObject): OrderLocationSnapshot {
        val c = o["location"].asJsonObject
        return OrderLocationSnapshot(o["addressText"].asString, AddressCoordinates(c["latitude"].asDouble, c["longitude"].asDouble),
            nullable(o, "reference"), nullable(o, "instructions"))
    }
    // Fixture decoder only: not a production HTTP/Room adapter. Exact numeric conversion rejects truncation.
    private fun parse(o: JsonObject): AcceptedPurchase {
        val items = o["items"].asJsonArray.map { entry -> entry.asJsonObject.let {
            OrderItem(it["productId"].asString, it["categoryId"].asString, it["name"].asString,
                Math.toIntExact(long(it, "quantity")), long(it, "unitPriceMinor")) } }
        val pickup = location(o["pickup"].asJsonObject)
        val d = o["delivery"].asJsonObject
        val delivery = OrderDeliverySnapshot(d["addressId"].asString, d["label"].asString, location(d["destination"].asJsonObject))
        val a = o["amounts"].asJsonObject
        val amounts = OrderAmounts(long(a, "subtotalMinor"), long(a, "deliveryFeeMinor"), long(a, "totalMinor"))
        val q = o["deliveryQuote"].asJsonObject
        return AcceptedPurchase(o["ownerUid"].asString, o["businessId"].asString, o["storeId"].asString,
            o["businessName"].asString, o["storeName"].asString, nullable(o, "storePhone"),
            o["buyerName"].asString, nullable(o, "buyerPhone"), pickup, delivery, items, amounts,
            AcceptedDeliveryQuote(q["quoteId"].asString, DeliveryQuote(q["distanceMeters"].asDouble, amounts.deliveryFeeMinor,
                CartCurrency.valueOf(o["currency"].asString), q["tariffVersion"].asString,
                long(q, "quotedAtEpochMillis"), long(q, "expiresAtEpochMillis"), pickup.location, delivery.destination.location)),
            CartCurrency.valueOf(o["currency"].asString), OrderPaymentMethod.valueOf(o["paymentMethod"].asString))
    }

    @Test fun sharedValidVectorsMatchCanonicalBytesAndAllHashes() {
        for (raw in vectors["validCases"].asJsonArray) {
            val v = raw.asJsonObject; val accepted = parse(v["purchase"].asJsonObject); val expected = v["expected"].asJsonObject
            val name = v["name"].asString
            assertEquals(name, expected["canonicalConsent"].asString, OrderFingerprints.canonicalConsent(accepted))
            assertEquals(name, expected["consentHash"].asString, OrderFingerprints.consent(accepted))
            assertEquals(name, expected["logisticsHash"].asString, OrderFingerprints.logistics(accepted))
            assertEquals(name, expected["requestHash"].asString, OrderIntent(id, accepted).requestHash)
            assertEquals(name, ReviewComparison(v["requiresAcceptance"].asBoolean, v["requiresNewRoute"].asBoolean),
                compareOrderReviews(purchase(), accepted))
        }
    }
    @Test fun sharedInvalidVectorsAreRejected() {
        for (raw in vectors["invalidCases"].asJsonArray) {
            val v = raw.asJsonObject
            try { parse(v["purchase"].asJsonObject); fail(v["name"].asString) }
            catch (_: IllegalArgumentException) { }
            catch (_: ArithmeticException) { }
        }
    }
    @Test fun acceptedItemsAreDefensivelyCopiedAndUnmodifiable() {
        val p = purchase(); val mutable = p.items.toMutableList()
        val copy = AcceptedPurchase(p.ownerUid, p.businessId, p.storeId, p.businessName, p.storeName, p.storePhone,
            p.buyerName, p.buyerPhone, p.pickup, p.delivery, mutable, p.amounts, p.deliveryQuote)
        val before = OrderFingerprints.consent(copy); mutable.clear()
        assertEquals(before, OrderFingerprints.consent(copy))
        assertThrows(UnsupportedOperationException::class.java) { (copy.items as MutableList).clear() }
    }
    @Test fun replaySuccessDoesNotDependOnTodaysQuoteOrCommercialEligibility() {
        val p = purchase(); val intent = OrderIntent(id, p)
        val result = OrderAttemptResult.Succeeded(OrderReceipt("order-demo", id, p, 1800000001000))
        val record = RecordedOrderAttempt(p.ownerUid, id, intent.requestHash, result)
        assertEquals(IntentResolution.Replay(result), resolveOrderIntent(intent, record))
    }
    @Test fun sameIntentWithChangedPayloadConflicts() {
        val original = OrderIntent(id, purchase())
        val recorded = RecordedOrderAttempt(original.ownerUid, id, original.requestHash,
            OrderAttemptResult.RejectedFinal(OrderRejection.REVIEW_REQUIRED))
        assertEquals(IntentResolution.Conflict, resolveOrderIntent(OrderIntent(id, purchase("price-up")), recorded))
    }
    @Test fun finalRejectionIsReplayedAndAnotherIntentIsDistinct() {
        val intent = OrderIntent(id, purchase()); val result = OrderAttemptResult.RejectedFinal(OrderRejection.QUOTE_EXPIRED)
        val recorded = RecordedOrderAttempt(intent.ownerUid, id, intent.requestHash, result)
        assertEquals(IntentResolution.Replay(result), resolveOrderIntent(intent, recorded))
        assertNotEquals(intent.requestHash, OrderIntent("22345678-1234-4234-8234-123456789abc", intent.accepted).requestHash)
    }
    @Test fun uncertaintyCannotBeStoredAsTerminalAndAbsenceDoesNotMeanFailure() {
        val intent = OrderIntent(id, purchase())
        for (reason in OrderUncertainty.entries) assertThrows(IllegalArgumentException::class.java) {
            RecordedOrderAttempt(intent.ownerUid, id, intent.requestHash, OrderAttemptResult.Unknown(reason))
        }
        assertEquals(IntentResolution.NewIntent, resolveOrderIntent(intent, null))
    }
    @Test fun receiptMustBelongToRecordedOwnerAndContent() {
        val intent = OrderIntent(id, purchase())
        val receipt = OrderAttemptResult.Succeeded(OrderReceipt("order-demo", id, intent.accepted, 1800000001000))
        assertThrows(IllegalArgumentException::class.java) { RecordedOrderAttempt("another-uid", id, intent.requestHash, receipt) }
        assertThrows(IllegalArgumentException::class.java) { RecordedOrderAttempt(intent.ownerUid, id, "0".repeat(64), receipt) }
    }
    @Test fun longOverflowAndUnsafeJsonValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { orderMinor(Long.MAX_VALUE) }
        assertThrows(ArithmeticException::class.java) { OrderItem("p", "c", "Product", Int.MAX_VALUE, MAX_ORDER_MINOR) }
        assertEquals(MAX_ORDER_MINOR, orderMinor(MAX_ORDER_MINOR))
    }
    @Test fun phoneAbsenceIsNotARejectionPolicy() {
        assertNull(purchase().buyerPhone)
        assertEquals("+51999999999", purchase("contact-present").buyerPhone)
    }
    @Test fun invalidIdentifiersAndUnpairedUnicodeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OrderIntent("not-uuid", purchase()) }
        assertThrows(IllegalArgumentException::class.java) { OrderItem("a/b", "c", "name", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { OrderItem("p", "c", 0xd800.toChar().toString(), 1, 1) }
    }
    @Test fun noOpDoesNotDemandAcceptanceAndNegativeZeroHasOneEncoding() {
        val p = purchase(); assertEquals(ReviewComparison(false, false), compareOrderReviews(p, p))
        val raw = vector("negative-zero")["purchase"].asJsonObject.deepCopy()
        val minus = parse(raw); raw["pickup"].asJsonObject["location"].asJsonObject.addProperty("latitude", 0.0)
        assertEquals(OrderFingerprints.consent(minus), OrderFingerprints.consent(parse(raw)))
    }
    @Test fun invalidNonFiniteGeographyAndQuoteAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OrderLocationSnapshot("Test", AddressCoordinates(Double.NaN, 0.0)) }
        val p = purchase(); val q = p.deliveryQuote.quote
        assertThrows(IllegalArgumentException::class.java) { q.copy(distanceMeters = Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { AcceptedDeliveryQuote("quote", q.copy(quotedAtEpochMillis = 0)) }
    }

    @Test fun recoveryNeverConvertsMissingEntryToFailureOrExposesAnotherOwner() {
        val intent = OrderIntent(id, purchase())
        assertEquals(OrderAttemptResult.Unknown(OrderUncertainty.NOT_OBSERVED), recoverOrderAttempt(intent.ownerUid, id, null))
        val result = OrderAttemptResult.RejectedFinal(OrderRejection.REVIEW_REQUIRED)
        val recorded = RecordedOrderAttempt(intent.ownerUid, id, intent.requestHash, result)
        assertEquals(result, recoverOrderAttempt(intent.ownerUid, id, recorded))
        assertThrows(IllegalArgumentException::class.java) { recoverOrderAttempt("another-uid", id, recorded) }
        assertEquals(IntentResolution.Conflict, resolveOrderIntent(intent,
            RecordedOrderAttempt("another-uid", id, intent.requestHash, result)))
    }
    @Test fun timestampRangeIsTechnicalAndDoesNotChangeMoneyRange() {
        val p = purchase()
        assertThrows(IllegalArgumentException::class.java) {
            AcceptedDeliveryQuote("quote", p.deliveryQuote.quote.copy(expiresAtEpochMillis = MAX_ORDER_EPOCH_MILLIS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrderReceipt("order", id, p, MAX_ORDER_EPOCH_MILLIS + 1)
        }
    }

    @Test fun nationalStoreContactAndEmptyAddressLabelPreserveOriginalValues() {
        val raw = vector("base")["purchase"].asJsonObject.deepCopy()
        raw.addProperty("storePhone", "987654321")
        raw["delivery"].asJsonObject.addProperty("label", "")
        val accepted = parse(raw)
        assertEquals("987654321", accepted.storePhone)
        assertEquals("", accepted.delivery.label)
        assertNull(accepted.buyerPhone)
        raw.addProperty("buyerPhone", "987654321")
        assertTrue(runCatching { parse(raw) }.isFailure)
    }
}
