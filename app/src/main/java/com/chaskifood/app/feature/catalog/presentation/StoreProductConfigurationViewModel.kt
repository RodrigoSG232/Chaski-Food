@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.catalog.presentation

import com.chaskifood.app.core.access.domain.StoreAuthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.catalog.domain.CatalogRepository
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.StoreProductConfigurationRepository
import com.chaskifood.app.feature.catalog.domain.storeProductConfigurationId
import com.chaskifood.app.feature.catalog.domain.validProductPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoreProductItem(
    val product: Product,
    val configuration: StoreProductConfiguration?,
) {
    val price: Double get() = configuration?.price ?: product.price
    val availability: StoreProductAvailability
        get() = configuration?.availability ?: StoreProductAvailability.OUT_OF_STOCK
    val isConfigured: Boolean get() = configuration != null
}

sealed interface StoreProductConfigurationUiState {
    data object Loading : StoreProductConfigurationUiState
    data object SignedOut : StoreProductConfigurationUiState
    data class BusinessNotApproved(val status: BusinessStatus) : StoreProductConfigurationUiState
    data object NoStores : StoreProductConfigurationUiState
    data class StoreNotEnabled(
        val stores: List<BusinessStore>,
        val selectedStoreId: String,
    ) : StoreProductConfigurationUiState
    data class LoadingProducts(
        val stores: List<BusinessStore>,
        val selectedStoreId: String,
    ) : StoreProductConfigurationUiState
    data class Success(
        val stores: List<BusinessStore>,
        val selectedStoreId: String,
        val products: List<StoreProductItem>,
    ) : StoreProductConfigurationUiState
    data class Error(
        val message: String,
        val stores: List<BusinessStore> = emptyList(),
        val selectedStoreId: String? = null,
    ) : StoreProductConfigurationUiState
}

data class ProductConfigurationOperationState(
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val successRevision: Long = 0,
)

@HiltViewModel
class StoreProductConfigurationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val businessRepository: BusinessRepository,
    private val storeRepository: StoreRepository,
    private val catalogRepository: CatalogRepository,
    private val configurationRepository: StoreProductConfigurationRepository,
    private val authorization: StoreAuthorization,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StoreProductConfigurationUiState>(StoreProductConfigurationUiState.Loading)
    val uiState: StateFlow<StoreProductConfigurationUiState> = _uiState.asStateFlow()

    private val _operations = MutableStateFlow<Map<String, ProductConfigurationOperationState>>(emptyMap())
    val operations: StateFlow<Map<String, ProductConfigurationOperationState>> = _operations.asStateFlow()

    private var accessObservation: Job? = null
    private var contentObservation: Job? = null
    private var availableStores: List<BusinessStore> = emptyList()
    private var selectedStoreId: String? = null

    private fun allowed(store: BusinessStore): Boolean = authorization.allows(store.businessId, store.id)
    init {
        if (authorization.enforced) {
            viewModelScope.launch {
                authorization.changes.collectLatest {
                    accessObservation?.cancel(); contentObservation?.cancel()
                    availableStores = emptyList(); selectedStoreId = null; _operations.value = emptyMap()
                    _uiState.value = StoreProductConfigurationUiState.Loading
                    if (authorization.hasAuthorizedStores()) load()
                }
            }
        } else load()
    }

    fun retry() = load()

    fun selectStore(storeId: String) {
        if (availableStores.none { it.id == storeId && allowed(it) } || selectedStoreId == storeId) return
        selectedStoreId = storeId
        observeSelectedStore()
    }

    fun clearOperationFeedback(storeId: String, productId: String) {
        val key = storeProductConfigurationId(storeId, productId)
        val current = _operations.value[key] ?: return
        if (!current.saving) {
            _operations.value += key to current.copy(error = null, message = null)
        }
    }

    fun saveConfiguration(
        productId: String,
        price: Double,
        availability: StoreProductAvailability,
    ) {
        val state = _uiState.value as? StoreProductConfigurationUiState.Success ?: return
        val store = state.stores.singleOrNull { it.id == state.selectedStoreId } ?: return
        if (!allowed(store)) return
        val item = state.products.singleOrNull { it.product.id == productId }
        if (item == null || item.product.businessId != store.businessId) {
            showError(storeProductConfigurationId(store.id, productId), "Producto no autorizado. Actualiza la lista.")
            return
        }
        val configuration = StoreProductConfiguration(
            businessId = store.businessId,
            storeId = store.id,
            productId = item.product.id,
            price = price,
            availability = availability,
        )
        val key = configuration.id
        if (_operations.value[key]?.saving == true) return
        if (!validProductPrice(price)) {
            showError(key, "El precio debe ser mayor a 0 y tener hasta dos decimales.")
            return
        }
        val previous = _operations.value[key] ?: ProductConfigurationOperationState()
        _operations.value += key to previous.copy(saving = true, error = null, message = null)

        viewModelScope.launch {
            try {
                val result = try {
                    configurationRepository.saveConfiguration(configuration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ApiResult.Failure("No se pudo guardar la configuración. Intenta nuevamente.")
                }
                when (result) {
                    is ApiResult.Success -> _operations.value += key to ProductConfigurationOperationState(
                        message = "Configuración guardada correctamente.",
                        successRevision = previous.successRevision + 1,
                    )
                    is ApiResult.Failure -> showError(
                        key,
                        result.message ?: "No se pudo guardar la configuración. Intenta nuevamente.",
                        previous.successRevision,
                    )
                }
            } finally {
                val current = _operations.value[key]
                if (current?.saving == true) _operations.value += key to current.copy(saving = false)
            }
        }
    }

    private fun load() {
        if (!authorization.hasAuthorizedStores()) return
        accessObservation?.cancel()
        contentObservation?.cancel()
        availableStores = emptyList()
        selectedStoreId = null
        _uiState.value = StoreProductConfigurationUiState.Loading
        accessObservation = viewModelScope.launch {
            authRepository.currentUserFlow.collectLatest { user ->
                contentObservation?.cancel()
                if (user == null) {
                    contentObservation?.cancel()
                    availableStores = emptyList()
                    selectedStoreId = null
                    _uiState.value = StoreProductConfigurationUiState.SignedOut
                    return@collectLatest
                }
                businessRepository.getBusinessRequest(user.uid).collectLatest { businessResult ->
                    when (businessResult) {
                        is ApiResult.Failure -> {
                            contentObservation?.cancel()
                            _uiState.value = StoreProductConfigurationUiState.Error(
                                businessResult.message ?: "No se pudo comprobar el negocio.",
                            )
                        }
                        is ApiResult.Success -> {
                            val ownedBusiness = businessResult.data?.takeIf { it.status == BusinessStatus.APPROVED }
                            if (ownedBusiness != null) {
                                observeStores(
                                    storeRepository.getStoresByBusiness(ownedBusiness.id),
                                    unavailableBusiness = null,
                                )
                            } else {
                                val email = user.email?.trim()?.lowercase()
                                if (email.isNullOrBlank()) {
                                    showEmptyAccess(businessResult.data)
                                } else {
                                    observeStores(
                                        storeRepository.getStoresForManager(email),
                                        unavailableBusiness = businessResult.data,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun observeStores(
        storesFlow: kotlinx.coroutines.flow.Flow<ApiResult<List<BusinessStore>>>,
        unavailableBusiness: BusinessRequest?,
    ) {
        storesFlow.collectLatest { result ->
            when (result) {
                is ApiResult.Failure -> {
                    contentObservation?.cancel()
                    _uiState.value = StoreProductConfigurationUiState.Error(
                        result.message ?: "No se pudieron cargar los locales autorizados.",
                    )
                }
                is ApiResult.Success -> {
                    availableStores = result.data.filter(::allowed).distinctBy { it.id }
                    if (availableStores.isEmpty()) {
                        selectedStoreId = null
                        contentObservation?.cancel()
                        showEmptyAccess(unavailableBusiness)
                    } else {
                        val selected = selectedStoreId?.takeIf { id -> availableStores.any { it.id == id } }
                            ?: availableStores.first().id
                        selectedStoreId = selected
                        observeSelectedStore()
                    }
                }
            }
        }
    }

    private fun showEmptyAccess(unavailableBusiness: BusinessRequest?) {
        _uiState.value = if (unavailableBusiness != null) {
            StoreProductConfigurationUiState.BusinessNotApproved(unavailableBusiness.status)
        } else {
            StoreProductConfigurationUiState.NoStores
        }
    }

    private fun observeSelectedStore() {
        contentObservation?.cancel()
        val store = availableStores.singleOrNull { it.id == selectedStoreId } ?: return
        if (store.status != StoreStatus.ACTIVE) {
            _uiState.value = StoreProductConfigurationUiState.StoreNotEnabled(availableStores, store.id)
            return
        }
        _uiState.value = StoreProductConfigurationUiState.LoadingProducts(availableStores, store.id)
        contentObservation = viewModelScope.launch {
            combine(
                catalogRepository.getProducts(store.businessId),
                configurationRepository.getConfigurations(store.id),
            ) { productsResult, configurationsResult ->
                when {
                    productsResult is ApiResult.Failure -> StoreProductConfigurationUiState.Error(
                        productsResult.message ?: "No se pudieron cargar los productos.",
                        availableStores,
                        store.id,
                    )
                    configurationsResult is ApiResult.Failure -> StoreProductConfigurationUiState.Error(
                        configurationsResult.message ?: "No se pudo cargar la configuración del local.",
                        availableStores,
                        store.id,
                    )
                    else -> {
                        val products = (productsResult as ApiResult.Success).data
                        val configurations = (configurationsResult as ApiResult.Success).data.associateBy { it.productId }
                        StoreProductConfigurationUiState.Success(
                            stores = availableStores,
                            selectedStoreId = store.id,
                            products = products.map { product -> StoreProductItem(product, configurations[product.id]) },
                        )
                    }
                }
            }.collectLatest { state -> _uiState.value = state }
        }
    }

    private fun showError(key: String, message: String, successRevision: Long? = null) {
        val current = _operations.value[key] ?: ProductConfigurationOperationState()
        _operations.value += key to current.copy(
            saving = false,
            error = message,
            message = null,
            successRevision = successRevision ?: current.successRevision,
        )
    }
}
