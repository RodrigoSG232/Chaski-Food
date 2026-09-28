package com.chaskifood.app.feature.address.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chaskifood.app.feature.address.domain.AddressSelectionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreAddressSelectionStore @Inject constructor(
    @param:AddressPreferences private val dataStore: DataStore<Preferences>,
) : AddressSelectionStore {
    override fun observe(ownerUid: String): Flow<String?> {
        val key = key(ownerUid)
        // Los errores de disco se propagan; no se convierten en ausencia de selección.
        return dataStore.data.map { it[key] }.distinctUntilChanged()
    }

    override suspend fun select(ownerUid: String, addressId: String?) {
        val key = key(ownerUid)
        addressId?.let(::requireDocumentId)
        dataStore.edit { preferences ->
            if (addressId == null) preferences.remove(key) else preferences[key] = addressId
        }
    }

    override suspend fun reconcile(
        ownerUid: String,
        availableAddressIds: Set<String>,
        defaultAddressId: String?,
    ): String? {
        val key = key(ownerUid)
        val updated = dataStore.edit { preferences ->
            val selected = AddressSelectionPolicy.resolve(
                availableAddressIds, preferences[key], defaultAddressId,
            )
            if (selected == null) preferences.remove(key) else preferences[key] = selected
        }
        return updated[key]
    }

    private fun key(ownerUid: String): Preferences.Key<String> {
        requireDocumentId(ownerUid)
        return stringPreferencesKey("selected_address_$ownerUid")
    }
}
