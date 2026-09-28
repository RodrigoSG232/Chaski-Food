package com.chaskifood.app.feature.address.domain

/** Coordenadas independientes de los SDK de ubicación, mapas y Firestore. */
data class AddressCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * Datos editables; no permite elegir propietario, ID ni fechas del servidor.
 * El formulario debe invalidar la confirmación si cambia el punto geográfico.
 * null significa que falta ubicación; (0, 0) sí es una ubicación válida.
 */
data class AddressDraft(
    val label: String = "",
    val addressText: String = "",
    val location: AddressCoordinates? = null,
    val reference: String = "",
    val instructions: String = "",
    val isLocationConfirmed: Boolean = false,
) {
    /** Cambiar/mover el punto requiere una nueva confirmación explícita. */
    fun withLocation(location: AddressCoordinates?): AddressDraft =
        copy(location = location, isLocationConfirmed = false)

    fun confirmLocation(): AddressDraft =
        copy(isLocationConfirmed = location?.isValid == true)
}

/**
 * Dirección confirmada por el servidor. El propietario está en AddressBook.
 * Data convierte los timestamps a milisegundos para lectura, sin sustituir
 * createdAt al editar ni utilizar la hora del dispositivo al guardar.
 */
data class Address(
    val id: String,
    val label: String,
    val addressText: String,
    val location: AddressCoordinates,
    val reference: String,
    val instructions: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    /** Editar una dirección guardada conserva el punto ya confirmado. */
    fun toDraft(): AddressDraft = AddressDraft(
        label = label,
        addressText = addressText,
        location = location,
        reference = reference,
        instructions = instructions,
        isLocationConfirmed = true,
    )
}

/**
 * Vista de direcciones de una sola cuenta, tras una lectura remota confirmada.
 * Un error de red o la ausencia de sesión no se representan como lista vacía.
 * selectedAddressId es local al dispositivo; defaultAddressId pertenece a la cuenta.
 */
data class AddressBook(
    val ownerUid: String,
    val addresses: List<Address>,
    val defaultAddressId: String?,
    val selectedAddressId: String?,
)
