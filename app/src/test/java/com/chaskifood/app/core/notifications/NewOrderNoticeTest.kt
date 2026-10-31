package com.chaskifood.app.core.notifications

import org.junit.Assert.*
import org.junit.Test

class NewOrderNoticeTest {
    private fun data()=mapOf("type" to "NEW_ORDER","eventId" to "new_order_${"a".repeat(64)}","orderId" to "a".repeat(64),
        "businessId" to "business","storeId" to "store","recipientUid" to "manager")
    @Test fun onlyMinimalMatchingNewOrderReferencesAreAccepted(){
        assertNotNull(NewOrderNotice.parse(data()))
        assertNull(NewOrderNotice.parse(data()+("phone" to "private")))
        assertNull(NewOrderNotice.parse(data()+("type" to "PREPARING")))
        assertNull(NewOrderNotice.parse(data()+("eventId" to "foreign")))
        assertNull(NewOrderNotice.parse(data()+("storeId" to "../other")))
        assertNull(NewOrderNotice.parse(data()-"recipientUid"))
    }
    @Test fun duplicateHintsAreScopedToUidAndCanBeCleared(){
        val dedup=NoticeDeduplicator()
        assertTrue(dedup.accept("A","event"));assertFalse(dedup.accept("A","event"))
        assertTrue(dedup.accept("B","event"));dedup.clear();assertTrue(dedup.accept("A","event"))
    }
    @Test fun visualDeduplicationHasBoundedMemory(){
        val dedup=NoticeDeduplicator();repeat(129){assertTrue(dedup.accept("A","event-$it"))}
        assertTrue(dedup.accept("A","event-0"));assertFalse(dedup.accept("A","event-128"))
    }
}
