@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.search.domain.OfferCategory
import com.chaskifood.app.feature.search.domain.OfferSearchResults
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.StoreOfferState
import com.chaskifood.app.feature.search.domain.searchStoreOffers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OfferSearchUiState {
    data object Loading : OfferSearchUiState
    data object SignedOut : OfferSearchUiState
    data object AddressRequired : OfferSearchUiState
    data class Content(
        val query: String,
        val selectedCategoryId: String?,
        val categories: List<OfferCategory>,
        val results: OfferSearchResults,
    ) : OfferSearchUiState
    data class Error(val message: String) : OfferSearchUiState
}

@HiltViewModel
class OfferSearchViewModel @Inject constructor(
    private val availableStoresRepository: AvailableStoresRepository,
    private val storeOfferRepository: StoreOfferRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OfferSearchUiState>(OfferSearchUiState.Loading)
    val uiState: StateFlow<OfferSearchUiState> = _uiState.asStateFlow()

    private var query = ""
    private var selectedCategoryId: String? = null
    private var offers: List<StoreOffer> = emptyList()
    private var hasOffersSnapshot = false
    private var observation: Job? = null

    init {
        load()
    }

    fun updateQuery(value: String) {
        query = value
        publishContent()
    }

    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        publishContent()
    }

    fun retry() = load()

    private fun load() {
        observation?.cancel()
        offers = emptyList()
        hasOffersSnapshot = false
        _uiState.value = OfferSearchUiState.Loading
        observation = viewModelScope.launch {
            try {
                observeOffers().collect { state ->
                    when (state) {
                        SearchOffersState.Loading -> _uiState.value = OfferSearchUiState.Loading
                        SearchOffersState.SignedOut -> _uiState.value = OfferSearchUiState.SignedOut
                        SearchOffersState.AddressRequired -> _uiState.value = OfferSearchUiState.AddressRequired
                        is SearchOffersState.Error -> _uiState.value = OfferSearchUiState.Error(state.message)
                        is SearchOffersState.Ready -> {
                            offers = state.offers
                            hasOffersSnapshot = true
                            if (selectedCategoryId != null && offers.none { offer ->
                                    offer.categories.any { it.id == selectedCategoryId }
                                }
                            ) selectedCategoryId = null
                            publishContent()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = OfferSearchUiState.Error("No se pudo consultar la oferta disponible.")
            }
        }
    }

    private fun observeOffers(): Flow<SearchOffersState> =
        availableStoresRepository.observeAvailableStores().flatMapLatest { available ->
            when (available) {
                AvailableStoresState.Loading -> flowOf(SearchOffersState.Loading)
                AvailableStoresState.SignedOut -> flowOf(SearchOffersState.SignedOut)
                AvailableStoresState.AddressRequired -> flowOf(SearchOffersState.AddressRequired)
                is AvailableStoresState.Error -> flowOf(SearchOffersState.Error(available.message))
                is AvailableStoresState.Ready -> {
                    val flows = available.stores.map { store ->
                        storeOfferRepository.observeStoreOffer(store.id)
                    }
                    if (flows.isEmpty()) flowOf(SearchOffersState.Ready(emptyList()))
                    else combine(flows) { states ->
                        val error = states.filterIsInstance<StoreOfferState.Error>().firstOrNull()
                        if (error != null) SearchOffersState.Error(error.message)
                        else SearchOffersState.Ready(
                            states.filterIsInstance<StoreOfferState.Ready>().map { it.offer },
                        )
                    }
                }
            }
        }

    private fun publishContent() {
        if (!hasOffersSnapshot) return
        val categories = offers.flatMap { it.categories }
            .distinctBy { it.id }
            .sortedWith(compareBy(OfferCategory::displayOrder, OfferCategory::name))
        _uiState.value = OfferSearchUiState.Content(
            query = query,
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            results = searchStoreOffers(offers, query, selectedCategoryId),
        )
    }
}

private sealed interface SearchOffersState {
    data object Loading : SearchOffersState
    data object SignedOut : SearchOffersState
    data object AddressRequired : SearchOffersState
    data class Ready(val offers: List<StoreOffer>) : SearchOffersState
    data class Error(val message: String) : SearchOffersState
}
