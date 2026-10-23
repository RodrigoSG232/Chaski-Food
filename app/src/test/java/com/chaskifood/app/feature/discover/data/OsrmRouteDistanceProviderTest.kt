package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.discover.domain.RouteDestination
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OsrmRouteDistanceProviderTest {
    @Test
    fun `convierte matriz en estimaciones por local y omite rutas ausentes`() = runTest {
        val api = FakeOsrmRouteApi(OsrmTableResponse(
            code = "Ok",
            distances = listOf(listOf(0.0, 1_800.0, null)),
            durations = listOf(listOf(0.0, 241.0, null)),
        ))
        val provider = OsrmRouteDistanceProvider(api)
        val origin = AddressCoordinates(-12.1211, -77.0297)
        val destinations = listOf(
            RouteDestination("local-1", AddressCoordinates(-12.1195, -77.0278)),
            RouteDestination("local-2", AddressCoordinates(-12.12, -77.03)),
        )

        val estimates = provider.estimate(origin, destinations)

        assertEquals("-77.0297,-12.1211;-77.0278,-12.1195;-77.03,-12.12", api.coordinates)
        assertEquals("0", api.sources)
        assertEquals("distance,duration", api.annotations)
        assertEquals(1.8, estimates.getValue("local-1").distanceKm, 0.001)
        assertEquals(5, estimates.getValue("local-1").durationMinutes)
        assertFalse(estimates.containsKey("local-2"))

        provider.estimate(origin, destinations)
        assertEquals(1, api.calls)
    }

    @Test
    fun `rechaza coordenadas invalidas sin llamar a la red`() = runTest {
        val api = FakeOsrmRouteApi(OsrmTableResponse("Ok"))
        val provider = OsrmRouteDistanceProvider(api)

        val estimates = provider.estimate(
            AddressCoordinates(91.0, -77.0),
            listOf(RouteDestination("local-1", AddressCoordinates(-12.0, -77.0))),
        )

        assertEquals(emptyMap<String, Any>(), estimates)
        assertEquals(0, api.calls)
    }
}

private class FakeOsrmRouteApi(private val response: OsrmTableResponse) : OsrmRouteApi {
    var calls = 0
    var coordinates = ""
    var sources = ""
    var annotations = ""

    override suspend fun table(
        coordinates: String,
        sources: String,
        annotations: String,
    ): OsrmTableResponse {
        calls++
        this.coordinates = coordinates
        this.sources = sources
        this.annotations = annotations
        return response
    }
}
