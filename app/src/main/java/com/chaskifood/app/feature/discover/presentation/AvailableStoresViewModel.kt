package com.chaskifood.app.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.discover.domain.SelectedDeliveryAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AvailableStoresUiState {
    data object Loading : AvailableStoresUiState
    data object SignedOut : AvailableStoresUiState
    data object AddressRequired : AvailableStoresUiState
    data class Empty(val selectedAddress: SelectedDeliveryAddress) : AvailableStoresUiState
    data class Success(
        val selectedAddress: SelectedDeliveryAddress,
        val stores: List<DiscoverableStore>,
    ) : AvailableStoresUiState
    data class Error(val message: String) : AvailableStoresUiState
}

@HiltViewModel
class AvailableStoresViewModel @Inject constructor(
    private val repository: AvailableStoresRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AvailableStoresUiState>(AvailableStoresUiState.Loading)
    val uiState: StateFlow<AvailableStoresUiState> = _uiState.asStateFlow()

    private var observation: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        observation?.cancel()
        _uiState.value = AvailableStoresUiState.Loading
        observation = viewModelScope.launch {
            try {
                repository.observeAvailableStores().collectLatest { state ->
                    _uiState.value = state.toUiState()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = AvailableStoresUiState.Error(
                    "No se pudieron consultar los locales disponibles.",
                )
            }
        }
    }
}

private fun AvailableStoresState.toUiState(): AvailableStoresUiState = when (this) {
    AvailableStoresState.Loading -> AvailableStoresUiState.Loading
    AvailableStoresState.SignedOut -> AvailableStoresUiState.SignedOut
    AvailableStoresState.AddressRequired -> AvailableStoresUiState.AddressRequired
    is AvailableStoresState.Error -> AvailableStoresUiState.Error(message)
    is AvailableStoresState.Ready -> if (stores.isEmpty()) {
        AvailableStoresUiState.Empty(selectedAddress)
    } else {
        AvailableStoresUiState.Success(selectedAddress, stores)
    }
}
