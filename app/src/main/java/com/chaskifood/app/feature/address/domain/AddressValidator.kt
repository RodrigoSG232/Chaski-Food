package com.chaskifood.app.feature.address.domain

object AddressLimits {
    const val LABEL = 40
    const val ADDRESS_TEXT = 250
    const val REFERENCE = 250
    const val INSTRUCTIONS = 500
}

enum class AddressField {
    LABEL,
    ADDRESS_TEXT,
    LOCATION,
    REFERENCE,
    INSTRUCTIONS,
}

/** La presentación traduce estos motivos a recursos de texto, no el dominio. */
enum class AddressValidationError {
    REQUIRED,
    TOO_LONG,
    INVALID_COORDINATES,
    LOCATION_NOT_CONFIRMED,
}

data class AddressValidationResult(
    val normalizedDraft: AddressDraft,
    val errors: Map<AddressField, AddressValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object AddressValidator {
    /**
     * Recorta únicamente los extremos de la dirección obligatoria; no trunca datos.
     * String.length cuenta unidades UTF-16: coincide con Rules.size() en las pruebas
     * locales de ASCII, tildes, emojis y caracteres combinados del 2026-09-28.
     * No equivale al número de símbolos que la persona percibe en pantalla.
     */
    fun validate(draft: AddressDraft): AddressValidationResult {
        val normalized = draft.copy(addressText = draft.addressText.trim())
        val errors = buildMap {
            if (normalized.label.length > AddressLimits.LABEL) {
                put(AddressField.LABEL, AddressValidationError.TOO_LONG)
            }
            if (normalized.addressText.isEmpty()) {
                put(AddressField.ADDRESS_TEXT, AddressValidationError.REQUIRED)
            } else if (normalized.addressText.length > AddressLimits.ADDRESS_TEXT) {
                put(AddressField.ADDRESS_TEXT, AddressValidationError.TOO_LONG)
            }
            when {
                normalized.location == null ->
                    put(AddressField.LOCATION, AddressValidationError.REQUIRED)
                !normalized.location.isValid ->
                    put(AddressField.LOCATION, AddressValidationError.INVALID_COORDINATES)
                !normalized.isLocationConfirmed ->
                    put(AddressField.LOCATION, AddressValidationError.LOCATION_NOT_CONFIRMED)
            }
            if (normalized.reference.length > AddressLimits.REFERENCE) {
                put(AddressField.REFERENCE, AddressValidationError.TOO_LONG)
            }
            if (normalized.instructions.length > AddressLimits.INSTRUCTIONS) {
                put(AddressField.INSTRUCTIONS, AddressValidationError.TOO_LONG)
            }
        }
        return AddressValidationResult(normalizedDraft = normalized, errors = errors)
    }
}
