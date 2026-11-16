package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.core.navigation.AccessNavigationPolicy
import com.chaskifood.app.core.navigation.ChaskiDestinations
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.orders.presentation.buyerExcessText
import com.chaskifood.app.feature.orders.presentation.buyerPaymentText
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** E5.4b: wire mapping of the real E5.4a payloads, buyer texts (Conc 14) and admin-only navigation. */
class CollectionReviewsMappingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val list = """{"contractVersion":1,"truncated":true,"items":[
      {"orderId":"${"a".repeat(64)}","shortId":"aaaaaaaa","kind":"OPEN_REVIEW","storeId":"s","storeName":"Local ficticio",
       "expectedMinor":8700,"declaredMinor":4000,"method":"CASH","currency":"PEN","openedAt":"2026-11-15T14:05:00.000Z","resolution":null},
      {"orderId":"${"b".repeat(64)}","shortId":"bbbbbbbb","kind":"EXCESS_PENDING_REFUND","storeId":"s","storeName":null,
       "expectedMinor":8700,"declaredMinor":9000,"method":"YAPE","currency":"PEN","openedAt":null,
       "resolution":{"outcome":"FULLY_COLLECTED","confirmedMinor":9000,"shortfallMinor":0,"excessMinor":300,"excessStatus":"PENDING_REFUND"}}]}"""

    @Test fun listMapsKindsAmountsAndTruncated() {
        val body = json.decodeFromString(CollectionReviewsResponse.serializer(), list)
        assertTrue(body.truncated)
        val (open, excess) = body.items.map { it.toDomain() }
        assertEquals(CollectionReviewKind.OPEN_REVIEW, open.kind); assertEquals(8700L, open.expectedMinor); assertEquals(4000L, open.declaredMinor)
        assertEquals("AAAAAAAA", open.shortId); assertNotNull(open.openedAtEpochMillis); assertNull(open.resolution)
        assertEquals(300L, excess.resolution!!.excessMinor); assertNull(excess.storeName)
        val empty = json.decodeFromString(CollectionReviewsResponse.serializer(), """{"contractVersion":1,"items":[],"truncated":false}""")
        assertTrue(empty.items.isEmpty())
    }
    @Test fun inconsistentRowsAreRejected() {
        val row = json.decodeFromString(CollectionReviewsResponse.serializer(), list).items[0]
        assertThrows(IllegalArgumentException::class.java) { row.copy(currency = "USD").toDomain() }
        assertThrows(IllegalArgumentException::class.java) { row.copy(kind = "EXCESS_PENDING_REFUND").toDomain() } // excess without resolution
        assertThrows(IllegalArgumentException::class.java) { row.copy(orderId = "x").toDomain() }
    }
    @Test fun reconciliationResponsesOfBothActionsDecode() {
        val resolved = json.decodeFromString(ReconciliationResponse.serializer(), """{"contractVersion":1,"orderId":"${"a".repeat(64)}",
          "action":"RESOLVE","outcome":"NOT_FULLY_COLLECTED","paymentStatus":"PENDING","reviewStatus":"RESOLVED","expectedMinor":8700,
          "declaredMinor":4000,"confirmedMinor":4000,"shortfallMinor":4700,"excessMinor":0,"excessStatus":"NONE","replayed":true}""")
        assertEquals(4700L, resolved.shortfallMinor); assertTrue(resolved.replayed)
        val refund = json.decodeFromString(ReconciliationResponse.serializer(), """{"contractVersion":1,"orderId":"${"b".repeat(64)}",
          "action":"RECORD_EXCESS_REFUND","excessMinor":300,"excessStatus":"REFUNDED","paymentStatus":"PAID","replayed":false}""")
        assertEquals(300L, refund.excessMinor); assertNull(refund.outcome)
    }

    @Test fun buyerTextsFollowTheBackendProjection() {
        assertEquals("Pago registrado contra entrega", buyerPaymentText("COMPLETED", OrderPaymentStatus.PAID, BuyerPaymentSource.RIDER))
        assertEquals("Pago en revisión", buyerPaymentText("COMPLETED", OrderPaymentStatus.PENDING, inReview = true))
        assertEquals("Pago incompleto · saldo pendiente", buyerPaymentText("COMPLETED", OrderPaymentStatus.PENDING, incomplete = true))
        // Historical simulated payment and older orders keep their texts.
        assertEquals("Efectivo registrado · simulación", buyerPaymentText("COMPLETED", OrderPaymentStatus.PAID, BuyerPaymentSource.SIMULATED))
        assertEquals("Efectivo pendiente hasta la entrega", buyerPaymentText("IN_DELIVERY", OrderPaymentStatus.PENDING))
        assertEquals("Excedente de S/ 3.00 pendiente de devolución", buyerExcessText(BuyerPaymentExcess(300, BuyerExcessStatus.PENDING_REFUND)))
        assertEquals("Excedente de S/ 3.00 devuelto", buyerExcessText(BuyerPaymentExcess(300, BuyerExcessStatus.REFUNDED)))
        assertFalse(buyerExcessText(BuyerPaymentExcess(300, BuyerExcessStatus.REFUNDED))!!.contains("pendiente"))
        assertNull(buyerExcessText(null))
    }
    @Test fun buyerDetailWireFieldsAreAdditiveAndTolerant() {
        val base = """"orderId":"o","createdAtEpochMillis":1,"status":"COMPLETED","phase":"HISTORICAL","statusKnown":true,"storeName":"L",
          "businessName":"B","itemCount":1,"totalMinor":8700,"currency":"PEN","storePhone":null,"pickupAddressText":"x",
          "delivery":{"label":null,"addressText":"y","reference":null,"instructions":null},"items":[],"subtotalMinor":8400,
          "deliveryFeeMinor":300,"paymentMethod":"CASH_ON_DELIVERY","statusHistory":[],"itemPreview":[],"itemLineCount":1"""
        val legacy = json.decodeFromString(BuyerOrderDetailResponse.serializer(), "{$base}")
        assertNull(legacy.paymentExcess); assertNull(legacy.paymentReview)
        val paid = json.decodeFromString(BuyerOrderDetailResponse.serializer(),
            "{$base,\"paymentStatus\":\"PAID\",\"paymentSource\":\"RIDER\",\"paymentReview\":null,\"paymentExcess\":{\"amountMinor\":300,\"status\":\"PENDING_REFUND\"}}")
        assertEquals(300L, paid.paymentExcess!!.amountMinor)
    }

    @Test fun reconciliationRouteIsAdminOnly() {
        assertTrue(ChaskiDestinations.ADMIN_COLLECTION_REVIEWS == "admin_collection_reviews")
        assertFalse(AccessNavigationPolicy.allows(null, ChaskiDestinations.ADMIN_COLLECTION_REVIEWS))
        assertFalse(AccessNavigationPolicy.allows(com.chaskifood.app.core.access.domain.AccessResolution.SignedOut,
            ChaskiDestinations.ADMIN_COLLECTION_REVIEWS))
    }
}
