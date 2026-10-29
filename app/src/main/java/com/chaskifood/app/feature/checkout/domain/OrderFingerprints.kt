package com.chaskifood.app.feature.checkout.domain

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.orders.domain.OrderLocationSnapshot
import java.security.MessageDigest

/** Protocolo hu16-v1. Sin Gson/Retrofit/Firebase; solo campos explícitos y enteros seguros.
 * Los Double geográficos se codifican como IEEE-754 big-endian hexadecimal para evitar
 * diferencias de formateo JSON entre JVM y JS. -0 se normaliza. Nunca se usa para dinero.
 */
object OrderFingerprints {
    fun consent(value: AcceptedPurchase): String = sha256(canonicalConsent(value))
    fun logistics(value: AcceptedPurchase): String = sha256(canonicalLogistics(value))
    fun request(value: OrderIntent): String = sha256(canonicalRequest(value))
    fun canonicalRequest(value: OrderIntent): String = canonical(mapOf(
        "version" to "hu16-request-v1", "intentId" to value.intentId, "accepted" to purchase(value.accepted)))
    fun canonicalConsent(value: AcceptedPurchase): String = canonical(mapOf(
        "version" to "hu16-consent-v1", "accepted" to purchase(value)))
    fun canonicalLogistics(value: AcceptedPurchase): String = canonical(mapOf(
        "version" to "hu16-logistics-v1", "ownerUid" to value.ownerUid, "storeId" to value.storeId,
        "addressId" to value.delivery.addressId, "origin" to coordinates(value.pickup.location),
        "destination" to coordinates(value.delivery.destination.location)))

    private fun purchase(v: AcceptedPurchase): Map<String, Any?> = mapOf(
        "ownerUid" to v.ownerUid, "businessId" to v.businessId, "storeId" to v.storeId,
        "businessName" to v.businessName, "storeName" to v.storeName, "storePhone" to v.storePhone,
        "buyerName" to v.buyerName, "buyerPhone" to v.buyerPhone,
        "pickup" to location(v.pickup), "delivery" to mapOf("addressId" to v.delivery.addressId,
            "label" to v.delivery.label, "destination" to location(v.delivery.destination)),
        "items" to v.items.map { mapOf("productId" to it.productId, "categoryId" to it.categoryId,
            "name" to it.name, "quantity" to it.quantity, "unitPriceMinor" to it.unitPriceMinor) },
        "amounts" to mapOf("subtotalMinor" to v.amounts.subtotalMinor,
            "deliveryFeeMinor" to v.amounts.deliveryFeeMinor, "totalMinor" to v.amounts.totalMinor),
        "currency" to v.currency.name, "paymentMethod" to v.paymentMethod.name,
        "deliveryQuote" to v.deliveryQuote.let { mapOf("quoteId" to it.quoteId,
            "distanceBits" to bits(it.quote.distanceMeters), "tariffVersion" to it.quote.tariffVersion,
            "quotedAtEpochMillis" to it.quote.quotedAtEpochMillis, "expiresAtEpochMillis" to it.quote.expiresAtEpochMillis) })
    private fun location(v: OrderLocationSnapshot): Map<String, Any?> = mapOf("addressText" to v.addressText,
        "location" to coordinates(v.location), "reference" to v.reference, "instructions" to v.instructions)
    private fun coordinates(v: AddressCoordinates) = mapOf("latitudeBits" to bits(v.latitude), "longitudeBits" to bits(v.longitude))
    private fun bits(v: Double) = java.lang.Long.toHexString(java.lang.Double.doubleToLongBits(if (v == 0.0) 0.0 else v)).padStart(16, '0')
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Int, is Long -> value.toString()
        is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Map<*, *> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") {
            quote(it.key as String) + ":" + canonical(it.value)
        }
        else -> error("Tipo ajeno al protocolo canónico")
    }
    private fun quote(value: String): String = buildString {
        append(34.toChar())
        for (c in value) when (c.code) {
            34, 92 -> { append(92.toChar()); append(c) }
            8, 9, 10, 12, 13 -> {
                append(92.toChar())
                append(mapOf(8 to 'b', 9 to 't', 10 to 'n', 12 to 'f', 13 to 'r').getValue(c.code))
            }
            else -> if (c.code < 32) {
                append(92.toChar()); append('u'); append(c.code.toString(16).padStart(4, '0'))
            } else append(c)
        }
        append(34.toChar())
    }
}
