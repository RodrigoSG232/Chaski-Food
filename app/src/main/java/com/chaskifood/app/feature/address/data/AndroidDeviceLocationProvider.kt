package com.chaskifood.app.feature.address.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
                if (fine) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }.filter { it in manager.getProviders(true) }
            firstAvailableLocation(providers.map { provider ->
                suspend {
                    capture(manager, provider)?.toResult(fine) ?: DeviceLocationResult.Unavailable
                }
            })
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

    private fun Location.toResult(fine: Boolean): DeviceLocationResult {
        val point = AddressCoordinates(latitude, longitude)
        val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
        if (!point.isValid || ageNanos !in 0L..60_000_000_000L) return DeviceLocationResult.Unavailable
        val precision = accuracy.takeIf { hasAccuracy() && it.isFinite() && it >= 0 }
        return DeviceLocationResult.Available(point, precision,
            approximate = !fine || precision == null || precision > 100f)
    }

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
