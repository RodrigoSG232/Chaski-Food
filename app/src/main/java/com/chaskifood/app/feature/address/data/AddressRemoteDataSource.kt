package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.Address
import com.chaskifood.app.feature.address.domain.AddressDraft
import kotlinx.coroutines.flow.Flow

data class RemoteAddressBook(val addresses: List<Address>, val defaultAddressId: String?)

/** Puerto de Data: Firebase real en producción, dobles únicamente en pruebas. */
interface AddressRemoteDataSource {
    fun changes(ownerUid: String): Flow<Unit>
    suspend fun read(ownerUid: String): RemoteAddressBook
    suspend fun create(ownerUid: String, draft: AddressDraft, operationId: String, checkSession: () -> Unit): Address
    suspend fun update(ownerUid: String, addressId: String, draft: AddressDraft, checkSession: () -> Unit): Address
    suspend fun delete(ownerUid: String, addressId: String, replacementId: String?, checkSession: () -> Unit)
    suspend fun setDefault(ownerUid: String, addressId: String, checkSession: () -> Unit)
}
