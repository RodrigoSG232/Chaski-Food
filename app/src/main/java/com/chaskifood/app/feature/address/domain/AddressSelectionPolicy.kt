package com.chaskifood.app.feature.address.domain

object
AddressSelectionPolicy {
    /**
     * Usar solo IDs propios de una lectura confirmada, nunca una lista vacía por error.
     * Conserva la selección vigente; después intenta la predeterminada. Si ambas
     * faltan, requiere elegir/agregar: nunca selecciona el primer elemento por azar.
     * Quien llama debe informar al usuario si cambia su destino y persistir el ID por UID.
     */
    fun resolve(
        availableAddressIds: Set<String>,
        selectedAddressId: String?,
        defaultAddressId: String?,
    ): String? = when {
        !selectedAddressId.isNullOrBlank() && selectedAddressId in availableAddressIds ->
            selectedAddressId
        !defaultAddressId.isNullOrBlank() && defaultAddressId in availableAddressIds ->
            defaultAddressId
        else -> null
    }
}
