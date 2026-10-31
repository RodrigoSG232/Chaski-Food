package com.chaskifood.app.feature.checkout.data.local

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.serialization.json.*

/** Formato local versionado: conserva exactamente el payload canónico HU16-1, sin tokens. */
internal object OrderIntentCodec {
    fun encode(value: OrderIntent): String = OrderFingerprints.canonicalRequest(value)
    fun decode(payload: String): OrderIntent {
        val root = Json.parseToJsonElement(payload).jsonObject
        require(root.text("version") == "hu16-request-v1")
        val p = root.getValue("accepted").jsonObject
        val pickup = location(p.getValue("pickup").jsonObject)
        val d = p.getValue("delivery").jsonObject
        val delivery = OrderDeliverySnapshot(d.text("addressId"), d.text("label"), location(d.getValue("destination").jsonObject))
        val items = p.getValue("items").jsonArray.map { it.jsonObject.let { i ->
            OrderItem(i.text("productId"), i.text("categoryId"), i.text("name"), i.integer("quantity").let(Math::toIntExact), i.integer("unitPriceMinor"))
        } }
        val a = p.getValue("amounts").jsonObject
        val amounts = OrderAmounts(a.integer("subtotalMinor"), a.integer("deliveryFeeMinor"), a.integer("totalMinor"))
        val q = p.getValue("deliveryQuote").jsonObject
        val quoteId = q.text("quoteId")
        val quote = DeliveryQuote(bits(q.text("distanceBits")), amounts.deliveryFeeMinor, CartCurrency.valueOf(p.text("currency")),
            q.text("tariffVersion"), q.integer("quotedAtEpochMillis"), q.integer("expiresAtEpochMillis"),
            pickup.location, delivery.destination.location, quoteId)
        val intent = OrderIntent(root.text("intentId"), AcceptedPurchase(p.text("ownerUid"), p.text("businessId"), p.text("storeId"),
            p.text("businessName"), p.text("storeName"), p.nullableText("storePhone"), p.text("buyerName"), p.nullableText("buyerPhone"),
            pickup, delivery, items, amounts, AcceptedDeliveryQuote(quoteId, quote), CartCurrency.valueOf(p.text("currency")),
            OrderPaymentMethod.valueOf(p.text("paymentMethod"))))
        require(encode(intent) == payload) // Rechaza truncamientos, campos desconocidos y formatos ambiguos.
        return intent
    }
    private fun location(v: JsonObject): OrderLocationSnapshot {
        val c = v.getValue("location").jsonObject
        return OrderLocationSnapshot(v.text("addressText"), AddressCoordinates(bits(c.text("latitudeBits")), bits(c.text("longitudeBits"))),
            v.nullableText("reference"), v.nullableText("instructions"))
    }
    private fun bits(value: String): Double {
        require(value.matches(Regex("[0-9a-f]{16}")))
        // Decodificación de bits sin depender de parseUnsignedLong de APIs Android posteriores.
        val raw = value.fold(0L) { bits, digit -> (bits shl 4) or digit.digitToInt(16).toLong() }
        return java.lang.Double.longBitsToDouble(raw)
    }
    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.let { require(it.isString); it.content }
    private fun JsonObject.nullableText(key: String): String? = if (getValue(key) == JsonNull) null else text(key)
    private fun JsonObject.integer(key: String): Long = getValue(key).jsonPrimitive.let { require(!it.isString); it.long }
}
