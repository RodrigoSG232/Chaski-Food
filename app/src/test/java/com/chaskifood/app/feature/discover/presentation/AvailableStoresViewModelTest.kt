package com.chaskifood.app.feature.discover.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresState
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.discover.domain.SelectedDeliveryAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
class AvailableStoresViewModelTest {
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
    fun `reacciona al cambio de direccion y muestra locales disponibles`() = runTest {
        val repository = FakeAvailableStoresRepository()
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value is AvailableStoresUiState.AddressRequired)

        repository.states.value = AvailableStoresState.Ready(address("Casa"), listOf(store()))
        runCurrent()

        val state = model.uiState.value as AvailableStoresUiState.Success
        assertEquals("Casa", state.selectedAddress.label)
        assertEquals("store-1", state.stores.single().id)
    }

    @Test
    fun `diferencia ausencia de cobertura de ausencia de direccion`() = runTest {
        val repository = FakeAvailableStoresRepository()
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value is AvailableStoresUiState.AddressRequired)

        repository.states.value = AvailableStoresState.Ready(address(), emptyList())
        runCurrent()

        assertTrue(model.uiState.value is AvailableStoresUiState.Empty)
    }

    @Test
    fun `representa sesion cerrada y error sin convertirlos en lista vacia`() = runTest {
        val repository = FakeAvailableStoresRepository(AvailableStoresState.SignedOut)
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value is AvailableStoresUiState.SignedOut)

        repository.states.value = AvailableStoresState.Error("Sin conexión")
        runCurrent()
        assertEquals("Sin conexión", (model.uiState.value as AvailableStoresUiState.Error).message)
    }

    @Test
    fun `reintentar sustituye la observacion fallida`() = runTest {
        var attempts = 0
        val repository = object : AvailableStoresRepository {
            override fun observeAvailableStores(): Flow<AvailableStoresState> = flow {
                attempts++
                if (attempts == 1) error("fallo")
                emit(AvailableStoresState.Ready(address(), listOf(store())))
            }
        }
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value is AvailableStoresUiState.Error)

        model.retry()
        runCurrent()

        assertTrue(model.uiState.value is AvailableStoresUiState.Success)
        assertEquals(2, attempts)
    }

    private fun model(repository: AvailableStoresRepository) =
        AvailableStoresViewModel(repository).also { viewModels.put("available-stores", it) }

    private fun address(label: String = "Trabajo") = SelectedDeliveryAddress(
        id = "address-1",
        label = label,
        addressText = "Av. Principal 123",
    )

    private fun store() = DiscoverableStore(
        id = "store-1",
        businessId = "business-1",
        businessName = "Sabor Peruano",
        businessCategory = "Criolla",
        name = "Miraflores",
        address = "Av. Larco 123",
        phone = "999888777",
        distanceKm = 1.2,
    )
}

private class FakeAvailableStoresRepository(
    initial: AvailableStoresState = AvailableStoresState.AddressRequired,
) : AvailableStoresRepository {
    val states = MutableStateFlow(initial)
    override fun observeAvailableStores(): Flow<AvailableStoresState> = states
}
