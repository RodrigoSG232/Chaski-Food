package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.discover.domain.RoadEstimate
import com.chaskifood.app.feature.discover.domain.RouteDestination
import com.chaskifood.app.feature.discover.domain.RouteDistanceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlin.math.ceil
import javax.inject.Inject
import javax.inject.Singleton

/** El servidor público de demostración admite como máximo una petición por segundo. */
@Singleton
class OsrmRouteDistanceProvider @Inject constructor(
    private val api: OsrmRouteApi,
) : RouteDistanceProvider {
    private val requestMutex = Mutex()
    private var lastRequestNanos = 0L
    private var cachedCoordinates: String? = null
    private var cachedDestinations: List<RouteDestination> = emptyList()
    private var cachedEstimates: Map<String, RoadEstimate> = emptyMap()
    private var cacheUpdatedNanos = 0L

    override suspend fun estimate(
        origin: AddressCoordinates,
        destinations: List<RouteDestination>,
    ): Map<String, RoadEstimate> {
        if (!origin.isValid || destinations.isEmpty() || destinations.any { !it.location.isValid }) {
            return emptyMap()
        }
        val coordinates = (listOf(origin) + destinations.map(RouteDestination::location))
            .joinToString(";") { "${it.longitude},${it.latitude}" }

        requestMutex.lock()
        try {
            if (coordinates == cachedCoordinates && destinations == cachedDestinations &&
                System.nanoTime() - cacheUpdatedNanos < 60_000_000_000L
            ) return cachedEstimates

            val remainingNanos = 1_000_000_000L - (System.nanoTime() - lastRequestNanos)
            if (lastRequestNanos != 0L && remainingNanos > 0L) {
                delay(ceil(remainingNanos / 1_000_000.0).toLong())
            }
            lastRequestNanos = System.nanoTime()
            val result = api.table(coordinates)
            if (result.code != "Ok") return emptyMap()
            val distances = result.distances?.singleOrNull() ?: return emptyMap()
            val durations = result.durations?.singleOrNull() ?: return emptyMap()
            if (distances.size != destinations.size + 1 || durations.size != destinations.size + 1) {
                return emptyMap()
            }
            val estimates = destinations.mapIndexedNotNull { index, destination ->
                val meters = distances[index + 1]
                val seconds = durations[index + 1]
                if (meters == null || seconds == null || !meters.isFinite() || !seconds.isFinite() ||
                    meters < 0 || seconds < 0
                ) null else destination.storeId to RoadEstimate(
                    distanceKm = meters / 1_000.0,
                    durationMinutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1),
                )
            }.toMap()
            cachedCoordinates = coordinates
            cachedDestinations = destinations.toList()
            cachedEstimates = estimates
            cacheUpdatedNanos = System.nanoTime()
            return estimates
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            requestMutex.unlock()
        }
    }
}
