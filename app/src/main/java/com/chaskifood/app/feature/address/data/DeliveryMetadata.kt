package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.AddressDraft
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal data class DeliveryMetadata(val count: Long, val defaultId: String?) {
    fun fields(operationId: String): Map<String, Any?> = mapOf(
        "addressCount" to count,
        "defaultAddressId" to defaultId,
        "lastOperationId" to operationId,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun afterCreate(addressId: String): DeliveryMetadata {
        check(count < Long.MAX_VALUE) { "El contador no es válido." }
        return DeliveryMetadata(count + 1, defaultId ?: addressId)
    }

    fun afterDelete(addressId: String, replacementId: String?): DeliveryMetadata {
        check(count > 0) { "No hay direcciones para eliminar." }
        val nextDefault = if (addressId == defaultId) {
            if (count == 1L) {
                require(replacementId == null) { "La última dirección no admite reemplazo." }
                null
            } else {
                require(replacementId != null && replacementId != addressId) { "Elige una dirección de reemplazo." }
                requireDocumentId(replacementId)
                replacementId
            }
        } else {
            require(replacementId == null) { "Solo se reemplaza una dirección predeterminada." }
            check(count > 1) { "El contador no coincide con la predeterminada." }
            defaultId
        }
        return DeliveryMetadata(count - 1, nextDefault)
    }

    companion object {
        fun fromDocument(data: Map<String, Any?>?): DeliveryMetadata {
            if (data == null) return DeliveryMetadata(0, null)
            require(data.keys == setOf("addressCount", "defaultAddressId", "lastOperationId", "updatedAt")) {
                "La preferencia requiere revisión de esquema; no se migrará automáticamente."
            }
            val count = data["addressCount"] as? Long ?: throw IllegalArgumentException("Contador no válido.")
            val defaultId = data["defaultAddressId"]
            require(count >= 0 && (defaultId == null || defaultId is String)) { "Preferencia no válida." }
            require((count == 0L) == (defaultId == null)) { "Predeterminada y contador no coinciden." }
            (defaultId as? String)?.let(::requireDocumentId)
            requireDocumentId(data["lastOperationId"] as? String ?: throw IllegalArgumentException("Operación no válida."))
            require(data["updatedAt"] is Timestamp) { "Fecha de preferencia no válida." }
            return DeliveryMetadata(count, defaultId as? String)
        }
    }
}

/** Huella de campos normalizados, con longitudes delimitadas; nunca guarda otra copia del domicilio. */
internal fun addressPayloadHash(draft: AddressDraft): String {
    // Validación y normalización idénticas a los campos que se escribirán.
    val fields = AddressDocumentMapper.editableFields(draft)
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { stream ->
        listOf("label", "addressText", "reference", "instructions").forEach { stream.writeUTF(fields[it] as String) }
        val location = fields["location"] as Map<*, *>
        stream.writeDouble(location["lat"] as Double)
        stream.writeDouble(location["lng"] as Double)
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
