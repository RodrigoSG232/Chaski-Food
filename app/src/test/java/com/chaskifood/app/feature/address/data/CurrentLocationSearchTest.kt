package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.DeviceLocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentLocationSearchTest {
    private val location = DeviceLocationResult.Available(AddressCoordinates(-12.0, -77.0), 20f, false)

    @Test fun `una respuesta de red no espera al GPS y cancela su registro`() = runTest {
        var gpsCancelled = false
        val result = firstAvailableLocation(listOf(
            { try { awaitCancellation() } finally { gpsCancelled = true } },
            { delay(50); location },
        ))
        assertEquals(location, result)
        assertEquals(50L, currentTime)
        assertTrue(gpsCancelled)
    }

    @Test fun `un proveedor que falla no descarta otra fuente disponible`() = runTest {
        assertEquals(location, firstAvailableLocation(listOf(
            { throw IllegalArgumentException("Proveedor no disponible") },
            { delay(100); location },
        )))
    }

    @Test fun `no selecciona coordenadas invalidas de una fuente`() = runTest {
        assertEquals(location, firstAvailableLocation(listOf(
            { location.copy(coordinates = AddressCoordinates(Double.NaN, 0.0)) },
            { delay(100); location },
        )))
    }

    @Test fun `varias fuentes sin respuesta comparten un solo plazo y se cancelan`() = runTest {
        var cancelled = 0
        val hanging: suspend () -> DeviceLocationResult = {
            try { awaitCancellation() } finally { cancelled++ }
        }
        assertEquals(DeviceLocationResult.Unavailable, firstAvailableLocation(listOf(hanging, hanging, hanging)))
        assertEquals(15_000L, currentTime)
        assertEquals(3, cancelled)
    }

    @Test fun `sin fuentes habilitadas responde sin esperar`() = runTest {
        assertEquals(DeviceLocationResult.Unavailable, firstAvailableLocation(emptyList()))
        assertEquals(0L, currentTime)
    }

    @Test fun `revocacion del permiso devuelve un resultado recuperable`() = runTest {
        assertEquals(DeviceLocationResult.PermissionRequired, firstAvailableLocation(listOf(
            { throw SecurityException("Permiso revocado") },
            { DeviceLocationResult.Unavailable },
        )))
    }

    @Test fun `cancelar desde la pantalla cancela todas las fuentes`() = runTest {
        var cancelled = 0
        val hanging: suspend () -> DeviceLocationResult = {
            try { awaitCancellation() } finally { cancelled++ }
        }
        val search = launch { firstAvailableLocation(listOf(hanging, hanging)) }
        runCurrent()
        search.cancel()
        advanceUntilIdle()
        assertEquals(2, cancelled)
        assertTrue(search.isCancelled)
    }
}
