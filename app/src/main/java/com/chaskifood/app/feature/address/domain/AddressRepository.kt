package com.chaskifood.app.feature.address.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para la cuenta autenticada; la UI no suministra un UID propietario.
 * Data debe capturar/revalidar la sesión por operación,
 * cancelar listeners al cambiar de cuenta y no entregar respuestas de la anterior.
 * La presentación también debe descartar borradores/resultados al cambiar sesión.
 * Lecturas fallidas emiten Error, escrituras fallidas devuelven Failure; nunca vacío ficticio.
 * Las cancelaciones no se absorben.
 */
interface AddressRepository {
    /**
     * Observa direcciones, predeterminada remota y selección local por UID.
     * Ready requiere datos confirmados; no presentar caché/escrituras pendientes
     * como confirmación remota. Loading/SignedOut descartan datos de la cuenta anterior.
     */
    fun observeAddressBook(): Flow<AddressBookState>

    /**
     * Validar/normalizar el borrador antes de escribir. operationId es un identificador
     * opaco estable para la misma alta: reutilizarlo al reintentar con idénticos datos,
     * rechazar su reutilización con otro contenido y no recrear una dirección borrada.
     * Primera alta y predeterminada se confirman atómicamente. Usar fechas del servidor.
     * Altas posteriores conservan predeterminada y selección válida.
     */
    suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address>

    /** Conservar ID/createdAt, actualizar updatedAt y no modificar pedidos históricos. */
    suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address>

    /**
     * Revalidar estado remoto. Al borrar la predeterminada con otras direcciones,
     * exigir reemplazo propio explícito y aplicarlo atómicamente con el borrado.
     * Última dirección: predeterminada y selección quedan sin ID. Reemplazo obsoleto
     * o inválido produce Failure, no una elección silenciosa de otro domicilio.
     */
    suspend fun deleteAddress(
        addressId: String,
        replacementDefaultAddressId: String? = null,
    ): ApiResult<Unit>

    /** Cambia la preferencia remota, pero conserva una selección local válida. */
    suspend fun setDefaultAddress(addressId: String): ApiResult<Unit>

    /** Comprueba propiedad/existencia y guarda solo el ID local por UID, no la preferencia. */
    suspend fun selectAddress(addressId: String): ApiResult<Unit>
}
