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

    private val userBusinessFlow = authRepository.currentUserFlow.flatMapLatest { user ->
        val uid = user?.uid ?: ""
        if (uid.isBlank()) flowOf(ApiResult.Success(null))
        else businessRepository.getBusinessRequest(uid)
    }

    private val categoriesFlow = userBusinessFlow.flatMapLatest { result ->
        val bizId = (result as? ApiResult.Success)?.data?.id ?: ""
        if (bizId.isBlank()) flowOf(ApiResult.Success(emptyList()))
        else catalogRepository.getCategories(bizId)
    }

    private val productsFlow = userBusinessFlow.flatMapLatest { result ->
        val bizId = (result as? ApiResult.Success)?.data?.id ?: ""
        if (bizId.isBlank()) flowOf(ApiResult.Success(emptyList()))
        else catalogRepository.getProducts(bizId)
    }

    val uiState: StateFlow<ManageCatalogUiState> = combine(
        userBusinessFlow,
        categoriesFlow,
        productsFlow,
    ) { bizResult, catResult, prodResult ->
        when (bizResult) {
            is ApiResult.Success -> {
                val biz = bizResult.data
                if (biz == null) {
                    ManageCatalogUiState.NoBusinessFound
                } else if (biz.status != BusinessStatus.APPROVED) {
                    ManageCatalogUiState.NotApproved
                } else {
                    val cats = (catResult as? ApiResult.Success)?.data ?: emptyList()
                    val prods = (prodResult as? ApiResult.Success)?.data ?: emptyList()
                    ManageCatalogUiState.Success(
                        businessId = biz.id,
                        businessName = biz.businessName,
                        categories = cats,
                        products = prods,
                    )
                }
            }
            is ApiResult.Failure -> ManageCatalogUiState.Error(bizResult.message ?: "Error al cargar datos del negocio.")
            null -> ManageCatalogUiState.Loading
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageCatalogUiState.Loading,
    )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun saveCategory(category: ProductCategory) {
        viewModelScope.launch {
            when (val result = catalogRepository.saveCategory(category)) {
                is ApiResult.Success -> _actionMessage.value = "Categoría guardada con éxito."
                is ApiResult.Failure -> _actionMessage.value = result.message
            }
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

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            if (product.name.isBlank()) {
                _actionMessage.value = "Ingresa el nombre del producto."
                return@launch
            }
            if (product.price <= 0.0) {
                _actionMessage.value = "El precio del producto debe ser mayor a 0."
                return@launch
            }
            if (product.categoryId.isBlank()) {
                _actionMessage.value = "Selecciona una categoría para el producto."
                return@launch
            }

            when (val result = catalogRepository.saveProduct(product)) {
                is ApiResult.Success -> _actionMessage.value = "Producto guardado con éxito."
                is ApiResult.Failure -> _actionMessage.value = result.message
            }
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