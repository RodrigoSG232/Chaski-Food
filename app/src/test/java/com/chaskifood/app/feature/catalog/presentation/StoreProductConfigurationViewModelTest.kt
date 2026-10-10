package com.chaskifood.app.feature.catalog.presentation

import android.app.Activity
import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.auth.domain.AuthUser
import com.chaskifood.app.feature.business.domain.AuditLog
import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.OperationalStatus
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.catalog.domain.CatalogRepository
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.StoreProductConfigurationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreProductConfigurationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val auth = ConfigurationTestAuth()
    private val businesses = ConfigurationTestBusinesses()
    private val stores = ConfigurationTestStores()
    private val catalog = ConfigurationTestCatalog()
    private val configurations = ConfigurationTestRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `propietario carga locales productos y configuracion del local seleccionado`() = runTest {
        val store = BusinessStore(id = "store-1", businessId = "business-1", name = "Centro")
        stores.owned.value = ApiResult.Success(listOf(store))
        catalog.products("business-1").value = ApiResult.Success(listOf(product("product-1", 10.0)))
        configurations.configurations("store-1").value = ApiResult.Success(listOf(
            configuration("store-1", "product-1", 12.50, StoreProductAvailability.AVAILABLE),
        ))

        val model = model()
        runCurrent()

        val state = model.uiState.value as StoreProductConfigurationUiState.Success
        assertEquals("store-1", state.selectedStoreId)
        assertEquals(12.50, state.products.single().price, 0.0)
        assertEquals(StoreProductAvailability.AVAILABLE, state.products.single().availability)
        assertTrue(state.products.single().isConfigured)
        assertEquals(1, stores.ownerQueries)
        assertEquals(0, stores.managerQueries)
    }

    @Test
    fun `producto sin configuracion inicia agotado y conserva precio base como borrador`() = runTest {
        stores.owned.value = ApiResult.Success(listOf(BusinessStore(id = "store-1", businessId = "business-1")))
        catalog.products("business-1").value = ApiResult.Success(listOf(product("product-1", 9.90)))

        val model = model()
        runCurrent()

        val item = (model.uiState.value as StoreProductConfigurationUiState.Success).products.single()
        assertEquals(9.90, item.price, 0.0)
        assertEquals(StoreProductAvailability.OUT_OF_STOCK, item.availability)
        assertFalse(item.isConfigured)
    }

    @Test
    fun `responsable sin negocio propio carga solamente locales asignados`() = runTest {
        businesses.owned.value = ApiResult.Success(null)
        stores.assigned.value = ApiResult.Success(listOf(BusinessStore(id = "assigned", businessId = "business-2")))
        catalog.products("business-2").value = ApiResult.Success(emptyList())

        val model = model()
        runCurrent()

        val state = model.uiState.value as StoreProductConfigurationUiState.Success
        assertEquals("assigned", state.selectedStoreId)
        assertEquals(0, stores.ownerQueries)
        assertEquals(1, stores.managerQueries)
        assertEquals("owner@example.com", stores.managerIdentifier)
    }

    @Test
    fun `negocio no aprobado sin asignaciones tiene estado distinto a lista vacia`() = runTest {
        businesses.owned.value = ApiResult.Success(BusinessRequest(
            id = "business-1",
            ownerUid = "owner",
            status = BusinessStatus.SUSPENDED,
        ))
        stores.assigned.value = ApiResult.Success(emptyList())

        val model = model()
        runCurrent()

        assertEquals(
            StoreProductConfigurationUiState.BusinessNotApproved(BusinessStatus.SUSPENDED),
            model.uiState.value,
        )
    }

    @Test
    fun `seleccionar local cambia productos y cancela la observacion anterior`() = runTest {
        stores.owned.value = ApiResult.Success(listOf(
            BusinessStore(id = "store-1", businessId = "business-1"),
            BusinessStore(id = "store-2", businessId = "business-1"),
        ))
        catalog.products("business-1").value = ApiResult.Success(listOf(product("product-1", 10.0)))
        val model = model()
        runCurrent()

        model.selectStore("store-2")
        runCurrent()

        assertEquals("store-2", (model.uiState.value as StoreProductConfigurationUiState.Success).selectedStoreId)
        assertEquals(0, configurations.activeCollectors("store-1"))
        assertEquals(1, configurations.activeCollectors("store-2"))
    }

    @Test
    fun `local suspendido no intenta consultar ni configurar productos`() = runTest {
        stores.owned.value = ApiResult.Success(listOf(BusinessStore(
            id = "store-1",
            businessId = "business-1",
            status = StoreStatus.SUSPENDED,
        )))

        val model = model()
        runCurrent()

        assertTrue(model.uiState.value is StoreProductConfigurationUiState.StoreNotEnabled)
        assertEquals(0, catalog.productQueries)
        assertEquals(0, configurations.configurationQueries)
    }

    @Test
    fun `doble guardado del mismo producto envia una sola escritura y conserva exito`() = runTest {
        prepareSuccessfulContent()
        configurations.gates["store-1_product-1"] = CompletableDeferred()
        val model = model()
        runCurrent()

        repeat(2) {
            model.saveConfiguration("product-1", 13.50, StoreProductAvailability.AVAILABLE)
        }
        runCurrent()
        assertEquals(1, configurations.saved.size)
        assertTrue(model.operations.value.getValue("store-1_product-1").saving)

        configurations.gates.getValue("store-1_product-1").complete(Unit)
        runCurrent()
        val operation = model.operations.value.getValue("store-1_product-1")
        assertFalse(operation.saving)
        assertEquals(1L, operation.successRevision)
        assertNotNull(operation.message)
    }

    @Test
    fun `productos diferentes pueden guardarse simultaneamente y mantienen errores separados`() = runTest {
        prepareSuccessfulContent(productCount = 2)
        configurations.gates["store-1_product-1"] = CompletableDeferred()
        configurations.failureByProduct["product-2"] = "Precio rechazado"
        val model = model()
        runCurrent()

        model.saveConfiguration("product-1", 11.0, StoreProductAvailability.AVAILABLE)
        model.saveConfiguration("product-2", 12.0, StoreProductAvailability.OUT_OF_STOCK)
        runCurrent()

        assertTrue(model.operations.value.getValue("store-1_product-1").saving)
        assertEquals("Precio rechazado", model.operations.value.getValue("store-1_product-2").error)
        configurations.gates.getValue("store-1_product-1").complete(Unit)
        runCurrent()
    }

    @Test
    fun `producto ajeno a la lista visible no se guarda`() = runTest {
        prepareSuccessfulContent()
        val model = model()
        runCurrent()

        model.saveConfiguration("foreign", 10.0, StoreProductAvailability.AVAILABLE)
        runCurrent()

        assertTrue(configurations.saved.isEmpty())
        assertNotNull(model.operations.value["store-1_foreign"]?.error)
    }

    @Test
    fun `precio invalido se rechaza antes de consultar el repositorio`() = runTest {
        prepareSuccessfulContent()
        val model = model()
        runCurrent()

        model.saveConfiguration("product-1", 10.999, StoreProductAvailability.AVAILABLE)
        runCurrent()

        assertTrue(configurations.saved.isEmpty())
        assertTrue(model.operations.value.getValue("store-1_product-1").error!!.contains("precio", ignoreCase = true))
    }

    private fun model() = StoreProductConfigurationViewModel(
        auth,
        businesses,
        stores,
        catalog,
        configurations,
    ).also { viewModels.put("store-products", it) }

    private fun prepareSuccessfulContent(productCount: Int = 1) {
        stores.owned.value = ApiResult.Success(listOf(BusinessStore(id = "store-1", businessId = "business-1")))
        catalog.products("business-1").value = ApiResult.Success(
            (1..productCount).map { product("product-$it", 10.0 + it) },
        )
    }

    private fun product(id: String, price: Double) = Product(
        id = id,
        businessId = "business-1",
        categoryId = "category-1",
        name = "Producto $id",
        price = price,
    )

    private fun configuration(
        storeId: String,
        productId: String,
        price: Double,
        availability: StoreProductAvailability,
    ) = StoreProductConfiguration("business-1", storeId, productId, price, availability)
}

private class ConfigurationTestAuth : AuthRepository {
    override val currentUserFlow = MutableStateFlow<AuthUser?>(AuthUser("owner", "owner@example.com", "Owner"))
    override suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> = error("unused")
    override suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String> = error("unused")
    override suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit> = error("unused")
    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> = error("unused")
    override suspend fun updateProfile(displayName: String): ApiResult<Unit> = error("unused")
    override suspend fun signOut() { currentUserFlow.value = null }
}

private class ConfigurationTestBusinesses : BusinessRepository {
    val owned = MutableStateFlow<ApiResult<BusinessRequest?>>(ApiResult.Success(
        BusinessRequest(id = "business-1", ownerUid = "owner", status = BusinessStatus.APPROVED),
    ))
    override fun getBusinessRequest(ownerUid: String): Flow<ApiResult<BusinessRequest?>> = owned
    override fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>> = error("unused")
    override suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> = error("unused")
    override suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> = error("unused")
    override suspend fun evaluateBusinessRequest(requestId: String, status: BusinessStatus, observations: String?, reviewerEmail: String): ApiResult<Unit> = error("unused")
    override suspend fun suspendBusiness(requestId: String, reason: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override suspend fun reactivateBusiness(requestId: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>> = error("unused")
}

private class ConfigurationTestStores : StoreRepository {
    val owned = MutableStateFlow<ApiResult<List<BusinessStore>>>(ApiResult.Success(emptyList()))
    val assigned = MutableStateFlow<ApiResult<List<BusinessStore>>>(ApiResult.Success(emptyList()))
    var ownerQueries = 0
    var managerQueries = 0
    var managerIdentifier: String? = null
    override fun getStoresByBusiness(businessId: String): Flow<ApiResult<List<BusinessStore>>> {
        ownerQueries++
        return owned
    }
    override fun getStoresForManager(managerEmailOrUid: String): Flow<ApiResult<List<BusinessStore>>> {
        managerQueries++
        managerIdentifier = managerEmailOrUid
        return assigned
    }
    override suspend fun createStore(store: BusinessStore): ApiResult<BusinessStore> = error("unused")
    override suspend fun updateStore(store: BusinessStore): ApiResult<BusinessStore> = error("unused")
    override fun getManagersByBusiness(businessId: String): Flow<ApiResult<List<StoreManager>>> = error("unused")
    override suspend fun assignManager(manager: StoreManager): ApiResult<StoreManager> = error("unused")
    override suspend fun updateOperationalStatus(storeId: String, status: OperationalStatus, pauseReason: String?): ApiResult<Unit> = error("unused")
    override suspend fun updateOperatingHours(storeId: String, hours: List<DayOperatingHours>): ApiResult<Unit> = error("unused")
    override suspend fun setAdministrativeStatus(storeId: String, status: StoreStatus, reason: String?): ApiResult<Unit> = error("unused")
}

private class ConfigurationTestCatalog : CatalogRepository {
    private val products = mutableMapOf<String, MutableStateFlow<ApiResult<List<Product>>>>()
    var productQueries = 0
    fun products(businessId: String) = products.getOrPut(businessId) { MutableStateFlow(ApiResult.Success(emptyList())) }
    override fun getProducts(businessId: String): Flow<ApiResult<List<Product>>> {
        productQueries++
        return products(businessId)
    }
    override fun getCategories(businessId: String): Flow<ApiResult<List<ProductCategory>>> = error("unused")
    override suspend fun saveCategory(category: ProductCategory): ApiResult<ProductCategory> = error("unused")
    override suspend fun toggleCategoryStatus(categoryId: String, isActive: Boolean): ApiResult<Unit> = error("unused")
    override suspend fun saveProduct(product: Product): ApiResult<Product> = error("unused")
    override suspend fun toggleProductStatus(productId: String, isActive: Boolean): ApiResult<Unit> = error("unused")
}

private class ConfigurationTestRepository : StoreProductConfigurationRepository {
    private val flows = mutableMapOf<String, CountingConfigurationFlow>()
    val saved = mutableListOf<StoreProductConfiguration>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val failureByProduct = mutableMapOf<String, String>()
    var configurationQueries = 0
    fun configurations(storeId: String): MutableStateFlow<ApiResult<List<StoreProductConfiguration>>> =
        flows.getOrPut(storeId) { CountingConfigurationFlow() }.state
    fun activeCollectors(storeId: String): Int = flows[storeId]?.active ?: 0
    override fun getConfigurations(storeId: String): Flow<ApiResult<List<StoreProductConfiguration>>> {
        configurationQueries++
        return flows.getOrPut(storeId) { CountingConfigurationFlow() }.flow()
    }
    override suspend fun getConfiguration(storeId: String, productId: String): ApiResult<StoreProductConfiguration?> = error("unused")
    override suspend fun saveConfiguration(configuration: StoreProductConfiguration): ApiResult<StoreProductConfiguration> {
        saved += configuration
        gates[configuration.id]?.await()
        failureByProduct[configuration.productId]?.let { return ApiResult.Failure(it) }
        return ApiResult.Success(configuration)
    }
}

private class CountingConfigurationFlow {
    val state = MutableStateFlow<ApiResult<List<StoreProductConfiguration>>>(ApiResult.Success(emptyList()))
    var active = 0
    fun flow(): Flow<ApiResult<List<StoreProductConfiguration>>> = kotlinx.coroutines.flow.flow {
        active++
        try {
            state.collect { emit(it) }
        } finally {
            active--
        }
    }
}
