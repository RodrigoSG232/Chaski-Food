package com.chaskifood.app.feature.business.domain

interface BusinessPhoneNormalizer {
    fun toInternational(dialCode: String, nationalNumber: String): String?
}

internal val businessPhoneRegions = mapOf(
    "+51" to "PE", "+52" to "MX", "+57" to "CO", "+56" to "CL", "+54" to "AR",
    "+593" to "EC", "+591" to "BO", "+58" to "VE", "+1" to "US", "+34" to "ES", "+55" to "BR",
)

data class BusinessContactPhone(val dialCode: String, val nationalNumber: String)

/** Los registros anteriores sin prefijo se mantienen como números nacionales de Perú. */
fun splitBusinessContactPhone(phone: String): BusinessContactPhone {
    val compact = phone.trim().replace(Regex("[\\s()\\-]"), "")
    val prefix = businessPhoneRegions.keys.sortedByDescending { it.length }
        .firstOrNull { compact.startsWith(it) }
    return if (prefix != null) BusinessContactPhone(prefix, compact.removePrefix(prefix))
    else BusinessContactPhone("+51", compact)
}
