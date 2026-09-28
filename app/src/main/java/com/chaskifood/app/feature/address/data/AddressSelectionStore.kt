package com.chaskifood.app.feature.address.data

import kotlinx.coroutines.flow.Flow

/** Guarda solo IDs por cuenta; no replica domicilios ni credenciales. */
interface AddressSelectionStore {
    fun observe(ownerUid: String): Flow<String?>
    suspend fun select(ownerUid: String, addressId: String?)

    /** Revalida y actualiza atómicamente sin sobrescribir una selección concurrente válida. */
    suspend fun reconcile(
        ownerUid: String,
        availableAddressIds: Set<String>,
        defaultAddressId: String?,
    ): String?
}
