package com.chaskifood.app.core.notifications

import org.junit.Assert.*
import org.junit.Test

class BuyerOrderNoticeTest {
    private val orderId = "a".repeat(64)

    @Test fun acceptsOnlyKnownMinimalBuyerHints() {
        for (type in BuyerNoticeType.entries) {
            val data = mapOf("type" to type.name, "eventId" to "buyer_notice_${orderId}_${type.name.lowercase()}_3",
                "orderId" to orderId, "recipientUid" to "buyer_1")
            assertEquals(type, BuyerOrderNotice.parse(data)?.type)
            assertNull(BuyerOrderNotice.parse(data + ("address" to "private")))
            assertNull(BuyerOrderNotice.parse(data + ("recipientUid" to "")))
            assertNull(BuyerOrderNotice.parse(data + ("eventId" to "buyer_notice_${orderId}_${type.name.lowercase()}_0")))
        }
        assertNull(BuyerOrderNotice.parse(mapOf("type" to "ORDER_UNKNOWN", "eventId" to "x",
            "orderId" to orderId, "recipientUid" to "buyer_1")))
    }
}
