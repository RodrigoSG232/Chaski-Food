package com.chaskifood.app.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.StoreOfferState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StoreOfferUiState {
    data object Loading : StoreOfferUiState
    data class Success(val offer: StoreOffer) : StoreOfferUiState
    data class Unavailable(val message: String) : StoreOfferUiState
    data class Error(val message: String) : StoreOfferUiState
}

@HiltViewModel
class StoreOfferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StoreOfferRepository,
) : ViewModel() {
    val storeId: String = savedStateHandle.get<String>("storeId").orEmpty()

    private val _uiState = MutableStateFlow<StoreOfferUiState>(StoreOfferUiState.Loading)
    val uiState: StateFlow<StoreOfferUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        observation?.cancel()
        _uiState.value = StoreOfferUiState.Loading
        observation = viewModelScope.launch {
            try {
                repository.observeStoreOffer(storeId).collect { state ->
                    _uiState.value = when (state) {
                        StoreOfferState.Loading -> StoreOfferUiState.Loading
                        is StoreOfferState.Ready -> StoreOfferUiState.Success(state.offer)
                        is StoreOfferState.StoreUnavailable -> StoreOfferUiState.Unavailable(state.message)
                        is StoreOfferState.Error -> StoreOfferUiState.Error(state.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = StoreOfferUiState.Error("No se pudo consultar la oferta del local.")
            }
        }
    }
}
