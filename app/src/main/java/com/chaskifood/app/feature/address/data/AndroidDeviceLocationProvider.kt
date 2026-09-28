package com.chaskifood.app.feature.address.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.DeviceLocationProvider
import com.chaskifood.app.feature.address.domain.DeviceLocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidDeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationProvider {
    override suspend fun currentLocation(): DeviceLocationResult {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return DeviceLocationResult.PermissionRequired
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return DeviceLocationResult.Unavailable
        return try {
            if (!LocationManagerCompat.isLocationEnabled(manager)) return DeviceLocationResult.Disabled
            val providers = buildList {
                if (fine) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }.filter { manager.isProviderEnabled(it) }
            // GPS primero; red como alternativa. Cada intento termina y cancela su registro.
            for (provider in providers) {
                val fix = withTimeoutOrNull(15_000L) { capture(manager, provider) } ?: continue
                val point = AddressCoordinates(fix.latitude, fix.longitude)
                val ageNanos = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
                if (!point.isValid || ageNanos !in 0L..60_000_000_000L) continue
                val accuracy = fix.accuracy.takeIf { fix.hasAccuracy() && it.isFinite() && it >= 0 }
                return DeviceLocationResult.Available(point, accuracy,
                    approximate = !fine || accuracy == null || accuracy > 100f)
            }
            DeviceLocationResult.Unavailable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            DeviceLocationResult.PermissionRequired
        } catch (_: Exception) {
            DeviceLocationResult.Unavailable
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Comprobado arriba; también se maneja revocación durante la captura.
    private suspend fun capture(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
}
