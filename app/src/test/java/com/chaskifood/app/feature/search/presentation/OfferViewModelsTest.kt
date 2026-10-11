package com.chaskifood.app.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.discover.domain.SelectedDeliveryAddress
import com.chaskifood.app.feature.search.domain.OfferCategory
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.StoreOfferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfferViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `busqueda combina ofertas disponibles y filtra texto`() = runTest {
        val first = store("store-1", "Miraflores")
        val second = store("store-2", "Centro")
        val available = FakeAvailableStores(ready(first, second))
        val offers = FakeStoreOffers().apply {
            state("store-1").value = StoreOfferState.Ready(offer(first, "Ceviche", "category-1"))
            state("store-2").value = StoreOfferState.Ready(offer(second, "Suspiro", "category-2"))
        }
        val model = OfferSearchViewModel(available, offers).also { viewModels.put("search", it) }
        runCurrent()

        model.updateQuery("suspiro")
        runCurrent()

        val content = model.uiState.value as OfferSearchUiState.Content
        assertEquals(listOf("Suspiro"), content.results.products.map { it.name })
        assertEquals(listOf("store-2"), content.results.stores.map { it.id })
    }

    @Test
    fun `busqueda reacciona si el local deja de estar disponible`() = runTest {
        val store = store("store-1", "Miraflores")
        val available = FakeAvailableStores(ready(store))
        val offers = FakeStoreOffers().apply {
            state(store.id).value = StoreOfferState.Ready(offer(store, "Ceviche", "category-1"))
        }
        val model = OfferSearchViewModel(available, offers).also { viewModels.put("search", it) }
        runCurrent()
        assertEquals(1, (model.uiState.value as OfferSearchUiState.Content).results.stores.size)

        available.states.value = ready()
        runCurrent()

        val content = model.uiState.value as OfferSearchUiState.Content
        assertTrue(content.results.stores.isEmpty())
        assertTrue(content.results.products.isEmpty())
    }

    @Test
    fun `busqueda distingue direccion requerida y error`() = runTest {
        val available = FakeAvailableStores(AvailableStoresState.AddressRequired)
        val model = OfferSearchViewModel(available, FakeStoreOffers()).also { viewModels.put("search", it) }
        runCurrent()
        assertTrue(model.uiState.value is OfferSearchUiState.AddressRequired)

        available.states.value = AvailableStoresState.Error("Sin conexión")
        runCurrent()
        assertEquals("Sin conexión", (model.uiState.value as OfferSearchUiState.Error).message)
    }

    @Test
    fun `oferta del local usa storeId de navegacion y conserva actualizaciones`() = runTest {
        val repository = FakeStoreOffers()
        val model = StoreOfferViewModel(
            SavedStateHandle(mapOf("storeId" to "store-1")),
            repository,
        ).also { viewModels.put("store", it) }
        runCurrent()
        assertEquals("store-1", repository.lastObservedId)

        val store = store("store-1", "Miraflores")
        repository.state("store-1").value = StoreOfferState.Ready(offer(store, "Ceviche", "category-1"))
        runCurrent()
        assertEquals("Ceviche", (model.uiState.value as StoreOfferUiState.Success).offer.products.single().name)

        repository.state("store-1").value = StoreOfferState.StoreUnavailable("Local cerrado")
        runCurrent()
        assertEquals("Local cerrado", (model.uiState.value as StoreOfferUiState.Unavailable).message)
    }

    private fun ready(vararg stores: DiscoverableStore) = AvailableStoresState.Ready(
        SelectedDeliveryAddress("address-1", "Casa", "Av. Principal"),
        stores.toList(),
    )

    private fun store(id: String, name: String) = DiscoverableStore(
        id = id,
        businessId = "business-$id",
        businessName = "Negocio $name",
        businessCategory = "Restaurante",
        name = name,
        address = "Av. $name",
        phone = "999888777",
        distanceKm = 1.0,
    )

    private fun offer(store: DiscoverableStore, productName: String, categoryId: String) = StoreOffer(
        store = store,
        categories = listOf(OfferCategory(categoryId, "Categoría $categoryId", 0)),
        products = listOf(StoreOfferProduct(
            id = "product-$productName",
            businessId = store.businessId,
            storeId = store.id,
            storeName = store.name,
            categoryId = categoryId,
            categoryName = "Categoría $categoryId",
            name = productName,
            description = "Descripción",
            price = 15.0,
            imageUrl = null,
            prepTimeMinutes = 20,
        )),
    )
}

private class FakeAvailableStores(initial: AvailableStoresState) : AvailableStoresRepository {
    val states = MutableStateFlow(initial)
    override fun observeAvailableStores(): Flow<AvailableStoresState> = states
}

private class FakeStoreOffers : StoreOfferRepository {
    private val states = mutableMapOf<String, MutableStateFlow<StoreOfferState>>()
    var lastObservedId: String? = null
    fun state(storeId: String) = states.getOrPut(storeId) {
        MutableStateFlow<StoreOfferState>(StoreOfferState.Loading)
    }
    override fun observeStoreOffer(storeId: String): Flow<StoreOfferState> {
        lastObservedId = storeId
        return state(storeId)
    }
}
