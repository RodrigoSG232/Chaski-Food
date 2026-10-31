package com.chaskifood.app.feature.checkout.data.remote

import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.OrderLocationSnapshot
import kotlinx.serialization.json.*

/** Solo convierte el contrato REST HU16-4. El dinero permanece como Long. */
internal object OrderWireCodec {
    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    private fun location(v: OrderLocationSnapshot) = buildJsonObject {
        put("addressText", v.addressText)
        put("location", buildJsonObject { put("latitude", v.location.latitude); put("longitude", v.location.longitude) })
        put("reference", nullable(v.reference)); put("instructions", nullable(v.instructions))
    }
    fun accepted(a: AcceptedPurchase): JsonObject = buildJsonObject {
        put("ownerUid", a.ownerUid); put("businessId", a.businessId); put("storeId", a.storeId)
        put("businessName", a.businessName); put("storeName", a.storeName); put("storePhone", nullable(a.storePhone))
        put("buyerName", a.buyerName); put("buyerPhone", nullable(a.buyerPhone)); put("pickup", location(a.pickup))
        put("delivery", buildJsonObject { put("addressId", a.delivery.addressId); put("label", a.delivery.label)
            put("destination", location(a.delivery.destination)) })
        put("items", buildJsonArray { a.items.forEach { i -> add(buildJsonObject {
            put("productId", i.productId); put("categoryId", i.categoryId); put("name", i.name)
            put("quantity", i.quantity); put("unitPriceMinor", i.unitPriceMinor)
        }) } })
        put("amounts", buildJsonObject { put("subtotalMinor", a.amounts.subtotalMinor)
            put("deliveryFeeMinor", a.amounts.deliveryFeeMinor); put("totalMinor", a.amounts.totalMinor) })
        put("currency", a.currency.name); put("paymentMethod", a.paymentMethod.name)
        put("deliveryQuote", buildJsonObject { val q = a.deliveryQuote.quote
            put("quoteId", a.deliveryQuote.quoteId); put("distanceMeters", q.distanceMeters)
            put("tariffVersion", q.tariffVersion); put("quotedAtEpochMillis", q.quotedAtEpochMillis)
            put("expiresAtEpochMillis", q.expiresAtEpochMillis) })
    }
    fun submission(intent: OrderIntent): JsonObject {
        val a = accepted(intent.accepted)
        return buildJsonObject {
            put("contractVersion", 1); put("intentId", intent.intentId); put("storeId", intent.accepted.storeId)
            put("addressId", intent.accepted.delivery.addressId); put("quoteId", intent.accepted.deliveryQuote.quoteId)
            put("paymentMethod", intent.accepted.paymentMethod.name)
            put("expected", JsonObject(a.filterKeys { it !in setOf("ownerUid", "storeId", "paymentMethod") }.mapValues { (key, value) ->
                if (key == "delivery") JsonObject(value.jsonObject.filterKeys { it != "addressId" })
                else if (key == "deliveryQuote") JsonObject(value.jsonObject.filterKeys { it != "quoteId" }) else value
            }))
        }
    }
    /** Numeros de geometría admiten 1250 y 1250.0; los importes exigen enteros JSON. */
    fun sameEvidence(actual: JsonElement, expected: JsonElement): Boolean = when {
        actual is JsonObject && expected is JsonObject -> actual.keys == expected.keys &&
            expected.all { (key, value) -> sameEvidence(actual.getValue(key), value) }
        actual is JsonArray && expected is JsonArray -> actual.size == expected.size &&
            actual.indices.all { sameEvidence(actual[it], expected[it]) }
        actual is JsonPrimitive && expected is JsonPrimitive -> when {
            actual == JsonNull || expected == JsonNull -> actual == expected
            expected.isString -> actual.isString && actual.content == expected.content
            actual.isString -> false
            expected.longOrNull != null -> actual.longOrNull == expected.longOrNull
            else -> actual.doubleOrNull?.let { it.isFinite() && it == expected.doubleOrNull } == true
        }
        else -> false
    }
    fun text(v: JsonObject, key: String): String = v.getValue(key).jsonPrimitive.let { require(it.isString); it.content }
    fun integer(v: JsonObject, key: String): Long = v.getValue(key).jsonPrimitive.let { require(!it.isString); it.long }
}
