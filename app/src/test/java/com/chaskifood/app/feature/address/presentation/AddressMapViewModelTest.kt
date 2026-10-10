package com.chaskifood.app.feature.address.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.DeviceLocationProvider
import com.chaskifood.app.feature.address.domain.DeviceLocationResult
import com.chaskifood.app.feature.address.domain.ReverseGeocodingProvider
import com.chaskifood.app.feature.address.domain.ReverseGeocodingResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressMapViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val point = AddressCoordinates(-12.0, -77.0)
    private val otherPoint = AddressCoordinates(-12.1, -77.1)
    private val fix = DeviceLocationResult.Available(point, 20f, false)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model(
        initial: AddressCoordinates? = null,
        locate: suspend () -> DeviceLocationResult = { fix },
        geocode: suspend (AddressCoordinates) -> ReverseGeocodingResult = { ReverseGeocodingResult.Found("Dirección encontrada") },
    ) = AddressMapViewModel(
        object : DeviceLocationProvider { override suspend fun currentLocation() = locate() },
        object : ReverseGeocodingProvider { override suspend fun addressFor(point: AddressCoordinates) = geocode(point) },
    ).also {
        store.put("map", it)
        it.open(AddressMapRequest("request", initial))
        it.mapLoad("request", 0, AddressMapLoad.READY)
    }

    @Test fun `abrir un punto existente acepta una direccion obtenida inmediatamente`() = runTest {
        val vm = model(initial = point)
        assertEquals(AddressLookupStatus.FOUND, vm.uiState.value.addressLookup)
        assertEquals("Dirección encontrada", vm.uiState.value.suggestedAddress)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test fun `ubicacion actual centra el punto y conserva una direccion inmediata`() = runTest {
        val vm = model()
        vm.locate("request")
        with(vm.uiState.value) {
            assertEquals(point, this.point)
            assertEquals(1, centerRevision)
            assertEquals(AddressLocationStatus.FOUND, locationStatus)
            assertEquals(AddressLookupStatus.FOUND, addressLookup)
            assertEquals("Dirección encontrada", suggestedAddress)
            assertTrue(canConfirm)
        }
    }

    @Test fun `al cambiar de punto no muestra ni confirma la direccion anterior mientras busca`() = runTest {
        val pending = CompletableDeferred<ReverseGeocodingResult>()
        val vm = model(initial = otherPoint, geocode = {
            if (it == otherPoint) ReverseGeocodingResult.Found("Dirección anterior") else pending.await()
        })
        vm.locate("request")
        assertEquals(point, vm.uiState.value.point)
        assertNull(vm.uiState.value.suggestedAddress)
        assertEquals(AddressLookupStatus.LOADING, vm.uiState.value.addressLookup)
        assertFalse(vm.uiState.value.canConfirm)
        pending.complete(ReverseGeocodingResult.Found("Dirección nueva"))
        assertEquals("Dirección nueva", vm.uiState.value.suggestedAddress)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test fun `sin geocodificacion permite confirmar coordenadas sin sustituir el texto del formulario`() = runTest {
        val vm = model(geocode = { ReverseGeocodingResult.Unavailable })
        vm.locate("request")
        assertEquals(point, vm.uiState.value.point)
        assertEquals(AddressLookupStatus.UNAVAILABLE, vm.uiState.value.addressLookup)
        assertNull(vm.uiState.value.suggestedAddress)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test fun `una ubicacion invalida no reemplaza un punto valido`() = runTest {
        val vm = model(initial = otherPoint, locate = { fix.copy(coordinates = AddressCoordinates(100.0, 0.0)) })
        vm.locate("request")
        assertEquals(otherPoint, vm.uiState.value.point)
        assertEquals(AddressLocationStatus.UNAVAILABLE, vm.uiState.value.locationStatus)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test fun `ubicacion aproximada se selecciona y conserva su precision para avisar al usuario`() = runTest {
        val vm = model(locate = { fix.copy(approximate = true, accuracyMeters = 800f) })
        vm.locate("request")
        assertEquals(point, vm.uiState.value.point)
        assertEquals(AddressLocationStatus.APPROXIMATE, vm.uiState.value.locationStatus)
        assertEquals(800f, vm.uiState.value.accuracyMeters)
    }

    @Test fun `un GPS tardio no reemplaza la seleccion manual`() = runTest {
        val pending = CompletableDeferred<DeviceLocationResult>()
        val vm = model(locate = { withContext(NonCancellable) { pending.await() } })
        vm.locate("request")
        assertEquals(AddressLocationStatus.LOCATING, vm.uiState.value.locationStatus)
        vm.selectPoint("request", otherPoint, recenter = true)
        pending.complete(fix)
        assertEquals(otherPoint, vm.uiState.value.point)
        assertEquals(AddressLocationStatus.IDLE, vm.uiState.value.locationStatus)
    }

    @Test fun `cancelar busqueda conserva el punto anterior y permite continuar`() = runTest {
        val pending = CompletableDeferred<DeviceLocationResult>()
        val vm = model(initial = otherPoint, locate = { pending.await() })
        vm.locate("request")
        assertFalse(vm.uiState.value.canConfirm)
        vm.cancelLocation()
        assertEquals(otherPoint, vm.uiState.value.point)
        assertEquals(AddressLocationStatus.IDLE, vm.uiState.value.locationStatus)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test fun `un fallo de captura es visible y permite reintentar`() = runTest {
        var attempts = 0
        val vm = model(locate = { if (attempts++ == 0) throw IllegalStateException("Sin señal") else fix })
        vm.locate("request")
        assertEquals(AddressLocationStatus.UNAVAILABLE, vm.uiState.value.locationStatus)
        assertNull(vm.uiState.value.point)
        vm.locate("request")
        assertEquals(point, vm.uiState.value.point)
        assertTrue(vm.uiState.value.canConfirm)
    }
}
