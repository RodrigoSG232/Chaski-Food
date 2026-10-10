package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.DeviceLocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Las fuentes comparten un plazo; la primera captura válida cancela las restantes. */
internal suspend fun firstAvailableLocation(
    requests: List<suspend () -> DeviceLocationResult>,
    timeoutMillis: Long = 15_000L,
): DeviceLocationResult = coroutineScope {
    val results = Channel<DeviceLocationResult>(Channel.UNLIMITED)
    val jobs = requests.map { request ->
        launch {
            val result = try { request() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { DeviceLocationResult.PermissionRequired }
            catch (_: Exception) { DeviceLocationResult.Unavailable }
            results.send(result)
        }
    }
    try {
        withTimeoutOrNull(timeoutMillis) {
            var fallback: DeviceLocationResult = DeviceLocationResult.Unavailable
            repeat(jobs.size) {
                when (val result = results.receive()) {
                    is DeviceLocationResult.Available -> if (result.coordinates.isValid) return@withTimeoutOrNull result
                    DeviceLocationResult.PermissionRequired -> fallback = result
                    else -> Unit
                }
            }
            fallback
        } ?: DeviceLocationResult.Unavailable
    } finally {
        jobs.forEach { it.cancel() }
        results.cancel()
    }
}
