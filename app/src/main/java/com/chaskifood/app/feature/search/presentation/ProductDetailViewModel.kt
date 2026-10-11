package com.chaskifood.app.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.StoreOfferState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState
    data class Success(val product: StoreOfferProduct) : ProductDetailUiState
    data class Unavailable(val message: String) : ProductDetailUiState
    data class Error(val message: String) : ProductDetailUiState
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StoreOfferRepository,
) : ViewModel() {
    private val storeId = savedStateHandle.get<String>("storeId").orEmpty()
    private val productId = savedStateHandle.get<String>("productId").orEmpty()

    private val _uiState = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeStoreOffer(storeId).collect { state ->
                    _uiState.value = when (state) {
                        StoreOfferState.Loading -> ProductDetailUiState.Loading
                        is StoreOfferState.Ready -> state.offer.products
                            .firstOrNull { it.id == productId }
                            ?.let(ProductDetailUiState::Success)
                            ?: ProductDetailUiState.Unavailable(
                                "Este producto ya no está disponible en el local.",
                            )
                        is StoreOfferState.StoreUnavailable -> ProductDetailUiState.Unavailable(state.message)
                        is StoreOfferState.Error -> ProductDetailUiState.Error(state.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = ProductDetailUiState.Error("No se pudo consultar el producto.")
            }
        }
    }
}
