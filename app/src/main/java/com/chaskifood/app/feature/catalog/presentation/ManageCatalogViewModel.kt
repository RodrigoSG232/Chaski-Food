@file:OptIn(ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.catalog.domain.CatalogRepository
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.validProductPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ManageCatalogUiState {
    object Loading : ManageCatalogUiState
    object NoBusinessFound : ManageCatalogUiState
    object NotApproved : ManageCatalogUiState
    data class Success(
        val businessId: String,
        val businessName: String,
        val categories: List<ProductCategory>,
        val products: List<Product>,
    ) : ManageCatalogUiState
    data class Error(val message: String) : ManageCatalogUiState
}

@HiltViewModel
class ManageCatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val businessRepository: BusinessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val reloads = MutableStateFlow(0)
    fun retryLoading() { reloads.value += 1 }

    val uiState: StateFlow<ManageCatalogUiState> = reloads.flatMapLatest {
        authRepository.currentUserFlow.flatMapLatest { user ->
            if (user == null) flowOf<ManageCatalogUiState>(ManageCatalogUiState.NoBusinessFound)
            else businessRepository.getBusinessRequest(user.uid).flatMapLatest { result ->
                when (result) {
                    is ApiResult.Failure -> flowOf<ManageCatalogUiState>(ManageCatalogUiState.Error(result.message ?: "No se pudo cargar el negocio."))
                    is ApiResult.Success -> {
                        val business = result.data
                        when {
                            business == null -> flowOf<ManageCatalogUiState>(ManageCatalogUiState.NoBusinessFound)
                            business.status != BusinessStatus.APPROVED -> flowOf<ManageCatalogUiState>(ManageCatalogUiState.NotApproved)
                            else -> combine(catalogRepository.getCategories(business.id), catalogRepository.getProducts(business.id)) { categories, products ->
                                when {
                                    categories is ApiResult.Failure -> ManageCatalogUiState.Error(categories.message ?: "No se pudieron cargar las categorías.")
                                    products is ApiResult.Failure -> ManageCatalogUiState.Error(products.message ?: "No se pudieron cargar los productos.")
                                    else -> ManageCatalogUiState.Success(business.id, business.businessName,
                                        (categories as ApiResult.Success).data, (products as ApiResult.Success).data)
                                }
                            }
                        }
                    }
                }
            }.onStart { emit(ManageCatalogUiState.Loading) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManageCatalogUiState.Loading)

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearSaveError() {
        if (!_saving.value) _saveError.value = null
    }

    fun saveCategory(category: ProductCategory, onSuccess: () -> Unit = {}) {
        if (_saving.value) return
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                when (val result = catalogRepository.saveCategory(category)) {
                    is ApiResult.Success -> { _actionMessage.value = "Categoría guardada con éxito."; onSuccess() }
                    is ApiResult.Failure -> { _actionMessage.value = result.message; _saveError.value = result.message }
                }
            } finally { _saving.value = false }
        }
    }

    fun toggleCategoryStatus(categoryId: String, isActive: Boolean) {
        viewModelScope.launch {
            when (val result = catalogRepository.toggleCategoryStatus(categoryId, isActive)) {
                is ApiResult.Success -> _actionMessage.value = if (isActive) "Categoría activada." else "Categoría desactivada."
                is ApiResult.Failure -> _actionMessage.value = result.message
            }
        }
    }

    fun saveProduct(product: Product, onSuccess: () -> Unit = {}) {
        if (_saving.value) return
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                if (product.name.isBlank()) {
                    _actionMessage.value = "Ingresa el nombre del producto."
                    _saveError.value = _actionMessage.value
                    return@launch
                }
                if (!validProductPrice(product.price)) {
                    _actionMessage.value = "El precio debe ser mayor a 0 y tener hasta dos decimales."
                    _saveError.value = _actionMessage.value
                    return@launch
                }
                if (product.prepTimeMinutes !in 1..1440) {
                    _actionMessage.value = "Indica una preparación de 1 a 1440 minutos."
                    _saveError.value = _actionMessage.value
                    return@launch
                }
                if (product.categoryId.isBlank()) {
                    _actionMessage.value = "Selecciona una categoría para el producto."
                    _saveError.value = _actionMessage.value
                    return@launch
                }

                when (val result = catalogRepository.saveProduct(product)) {
                    is ApiResult.Success -> { _actionMessage.value = "Producto guardado con éxito."; onSuccess() }
                    is ApiResult.Failure -> { _actionMessage.value = result.message; _saveError.value = result.message }
                }
            } finally { _saving.value = false }
        }
    }

    fun toggleProductStatus(productId: String, isActive: Boolean) {
        viewModelScope.launch {
            when (val result = catalogRepository.toggleProductStatus(productId, isActive)) {
                is ApiResult.Success -> _actionMessage.value = if (isActive) "Producto activado." else "Producto desactivado."
                is ApiResult.Failure -> _actionMessage.value = result.message
            }
        }
    }
}
