package com.chaskifood.app.feature.catalog.domain

import java.math.BigDecimal
import java.util.Locale

data class ProductPriceInput(val value: Double? = null, val error: String? = null)

/** Solo admite un separador decimal; no interpreta separadores de miles ni redondea la entrada. */
fun parseProductPrice(input: String): ProductPriceInput {
    val text = input.trim()
    if (text.isEmpty()) return ProductPriceInput(error = "Ingresa el precio del producto.")
    if (!Regex("(?:[0-9]+(?:[.,][0-9]{1,2})?|[.,][0-9]{1,2})").matches(text)) {
        return ProductPriceInput(error = "Usa punto o coma y hasta dos decimales, sin separadores de miles.")
    }
    val decimal = text.replace(',', '.').toBigDecimalOrNull()
        ?: return ProductPriceInput(error = "Ingresa un precio válido.")
    if (decimal.signum() <= 0) return ProductPriceInput(error = "El precio debe ser mayor a 0.")
    val value = decimal.toDouble()
    if (!value.isFinite() || BigDecimal.valueOf(value).compareTo(decimal) != 0) {
        return ProductPriceInput(error = "El precio es demasiado grande.")
    }
    return ProductPriceInput(value = value)
}

fun validProductPrice(price: Double): Boolean = price.isFinite() && price > 0 &&
    BigDecimal.valueOf(price).stripTrailingZeros().scale() <= 2

fun formatProductPrice(price: Double): String = String.format(Locale.ROOT, "%.2f", price)
