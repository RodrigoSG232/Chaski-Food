package com.chaskifood.app.feature.checkout.domain

/** El reloj monotónico evita que un ajuste posterior del reloj del dispositivo prolongue la cotización. */
interface CheckoutClock {
    fun wallTimeMillis(): Long
    fun monotonicTimeMillis(): Long
}

/** Margen para desfases entre servidor y dispositivo; una cotización dudosa se vuelve a consultar. */
fun deliveryQuoteDeadlineOrNull(quote: DeliveryQuote, clock: CheckoutClock): Long? {
    val ttl = quote.expiresAtEpochMillis - quote.quotedAtEpochMillis
    if (ttl !in 60_000L..900_000L) return null
    val wallNow = clock.wallTimeMillis()
    if (quote.quotedAtEpochMillis - wallNow > 30_000L) return null
    val remaining = minOf(ttl, quote.expiresAtEpochMillis - wallNow) - 35_000L
    if (remaining <= 0L) return null
    return try { Math.addExact(clock.monotonicTimeMillis(), remaining) }
    catch (_: ArithmeticException) { null }
}
