package com.chaskifood.app.core.notifications

/** A push is only a hint. No address, contact, products or prices are consumed from it. */
data class NewOrderNotice(val eventId: String, val orderId: String, val businessId: String, val storeId: String, val recipientUid: String) {
    companion object {
        fun parse(data: Map<String,String>): NewOrderNotice? {
            if(data.keys != setOf("type","eventId","orderId","businessId","storeId","recipientUid") || data["type"]!="NEW_ORDER") return null
            val order=data["orderId"] ?: return null
            if(!order.matches(Regex("[0-9a-f]{64}")) || data["eventId"]!="new_order_$order")return null
            val ids=listOf("businessId","storeId","recipientUid")
            if(ids.any { data[it]?.matches(Regex("[A-Za-z0-9_-]{1,128}"))!=true })return null
            return NewOrderNotice(data.getValue("eventId"),order,data.getValue("businessId"),data.getValue("storeId"),data.getValue("recipientUid"))
        }
    }
}
/** Bounded in-memory visual deduplication only; never an authorization source. */
internal class NoticeDeduplicator {
    private val seen=linkedSetOf<String>()
    @Synchronized fun accept(uid:String,eventId:String):Boolean {
        val key="$uid:$eventId"
        if(!seen.add(key))return false
        if(seen.size>128)seen.remove(seen.first())
        return true
    }
    @Synchronized fun clear(){seen.clear()}
}
