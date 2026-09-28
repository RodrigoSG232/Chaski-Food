package com.chaskifood.app.feature.address.data

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.ReverseGeocodingProvider
import com.chaskifood.app.feature.address.domain.ReverseGeocodingResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val PERUAN_SPANISH = Locale.forLanguageTag("es-PE")

class AndroidReverseGeocodingProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReverseGeocodingProvider {
    override suspend fun addressFor(point: AddressCoordinates): ReverseGeocodingResult {
        if (!point.isValid || !Geocoder.isPresent()) return ReverseGeocodingResult.Unavailable
        return try {
            val address = withTimeoutOrNull(12_000L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    reverseGeocodeAsync(point)
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        Geocoder(context, PERUAN_SPANISH)
                            .getFromLocation(point.latitude, point.longitude, 1)
                            ?.firstOrNull()
                    }
                }
            } ?: return ReverseGeocodingResult.Unavailable
            address?.formatAddress()?.takeIf(String::isNotBlank)
                ?.let(ReverseGeocodingResult::Found) ?: ReverseGeocodingResult.Unavailable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReverseGeocodingResult.Unavailable
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reverseGeocodeAsync(point: AddressCoordinates): Address? =
        suspendCancellableCoroutine { continuation ->
            Geocoder(context, PERUAN_SPANISH).getFromLocation(
                point.latitude, point.longitude, 1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    private fun Address.formatAddress(): String =
        getAddressLine(0)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
}
