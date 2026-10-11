package com.chaskifood.app.feature.discover.domain

import kotlinx.coroutines.flow.Flow

data class SelectedDeliveryAddress(
    val id: String,
    val label: String,
    val addressText: String,
)

sealed interface AvailableStoresState {
    data object Loading : AvailableStoresState
    data object SignedOut : AvailableStoresState
    data object AddressRequired : AvailableStoresState
    data class Ready(
        val selectedAddress: SelectedDeliveryAddress,
        val stores: List<DiscoverableStore>,
    ) : AvailableStoresState
    data class Error(val message: String) : AvailableStoresState
}

interface AvailableStoresRepository {
    /** Se recalcula al cambiar la sesión, las direcciones o la selección local. */
    fun observeAvailableStores(): Flow<AvailableStoresState>
}
