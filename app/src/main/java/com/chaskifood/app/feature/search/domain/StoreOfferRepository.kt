package com.chaskifood.app.feature.search.domain

import kotlinx.coroutines.flow.Flow

sealed interface StoreOfferState {
    data object Loading : StoreOfferState
    data class Ready(val offer: StoreOffer) : StoreOfferState
    data class StoreUnavailable(val message: String) : StoreOfferState
    data class Error(val message: String) : StoreOfferState
}

interface StoreOfferRepository {
    /** Observa la oferta mientras el local siga siendo comprable para la dirección seleccionada. */
    fun observeStoreOffer(storeId: String): Flow<StoreOfferState>
}
