package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.Address
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.chaskifood.app.feature.address.domain.AddressValidator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

/** Un documento incorrecto produce un error, nunca una dirección inventada o descartada. */
internal object AddressDocumentMapper {
    private val fields = setOf(
        "label", "addressText", "location", "reference", "instructions", "createdAt", "updatedAt",
    )

    fun fromDocument(id: String, data: Map<String, Any?>): Address {
        requireDocumentId(id)
        require(data.keys == fields) { "El documento de dirección tiene un esquema no válido." }
        val location = data["location"] as? Map<*, *>
            ?: invalidDocument()
        require(location.keys == setOf("lat", "lng")) { "La ubicación tiene un esquema no válido." }
        val draft = AddressDraft(
            label = data["label"] as? String ?: invalidDocument(),
            addressText = data["addressText"] as? String ?: invalidDocument(),
            location = AddressCoordinates(
                latitude = (location["lat"] as? Number)?.toDouble() ?: invalidDocument(),
                longitude = (location["lng"] as? Number)?.toDouble() ?: invalidDocument(),
            ),
            reference = data["reference"] as? String ?: invalidDocument(),
            instructions = data["instructions"] as? String ?: invalidDocument(),
            isLocationConfirmed = true,
        )
        val validated = AddressValidator.validate(draft)
        require(validated.isValid && validated.normalizedDraft == draft) {
            "La dirección almacenada no cumple las validaciones."
        }
        val createdAt = data["createdAt"] as? Timestamp ?: invalidDocument()
        val updatedAt = data["updatedAt"] as? Timestamp ?: invalidDocument()
        require(updatedAt >= createdAt) { "Las fechas de la dirección no son válidas." }
        return Address(
            id = id,
            label = draft.label,
            addressText = draft.addressText,
            location = requireNotNull(draft.location),
            reference = draft.reference,
            instructions = draft.instructions,
            createdAtEpochMillis = createdAt.toDate().time,
            updatedAtEpochMillis = updatedAt.toDate().time,
        )
    }

    fun createFields(draft: AddressDraft): Map<String, Any> = editableFields(draft) + mapOf(
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Usar con update, nunca con set que sustituya y elimine createdAt. */
    fun updateFields(draft: AddressDraft): Map<String, Any> = editableFields(draft) +
        ("updatedAt" to FieldValue.serverTimestamp())

    fun editableFields(draft: AddressDraft): Map<String, Any> {
        val result = AddressValidator.validate(draft)
        require(result.isValid) { "Revisa los campos de la dirección antes de guardar." }
        val normalized = result.normalizedDraft
        val location = requireNotNull(normalized.location)
        return mapOf(
            "label" to normalized.label,
            "addressText" to normalized.addressText,
            "location" to mapOf("lat" to location.latitude, "lng" to location.longitude),
            "reference" to normalized.reference,
            "instructions" to normalized.instructions,
        )
    }

    private fun invalidDocument(): Nothing =
        throw IllegalArgumentException("El documento de dirección tiene tipos no válidos.")
}

/** Evita interpretar un ID suministrado como otra ruta de Firestore. */
internal fun requireDocumentId(id: String) {
    require(
        id.isNotBlank() && '/' !in id && id != "." && id != ".." &&
            !(id.startsWith("__") && id.endsWith("__")) && id.toByteArray(Charsets.UTF_8).size <= 1_500,
    ) { "El identificador no es válido." }
}
