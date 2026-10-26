package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteApi
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteErrorResponse
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteRequest
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteResponse
import com.chaskifood.app.feature.checkout.domain.DeliveryQuote
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteFailure
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteReader
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

internal class HttpDeliveryQuoteReader @Inject constructor(
    private val api: DeliveryQuoteApi,
    private val tokens: BuyerTokenSource,
    private val json: Json,
) : DeliveryQuoteReader {
    override suspend fun quote(storeId: String, addressId: String, ownerUid: String,
                               sessionGeneration: Long): DeliveryQuoteResult {
        if (!VALID_ID.matches(storeId) || !VALID_ID.matches(addressId)) return failed(DeliveryQuoteFailure.INVALID_REQUEST)
        return try {
            withTimeout(15_000) {
                val token = tokens.token(ownerUid, sessionGeneration)
                val response = api.quote(DeliveryQuoteRequest(storeId, addressId), "Bearer $token")
                if (!tokens.isCurrent(ownerUid, sessionGeneration)) return@withTimeout failed(DeliveryQuoteFailure.SESSION_CHANGED)
                if (!response.isSuccessful) return@withTimeout mapError(response)
                val quote = response.body()?.toDomain() ?: return@withTimeout failed(DeliveryQuoteFailure.MALFORMED_RESPONSE)
                if (!tokens.isCurrent(ownerUid, sessionGeneration)) failed(DeliveryQuoteFailure.SESSION_CHANGED)
                else DeliveryQuoteResult.Quoted(quote)
            }
        } catch (_: BuyerSessionChanged) {
            failed(DeliveryQuoteFailure.SESSION_CHANGED)
        } catch (_: BuyerAuthenticationRequired) {
            failed(DeliveryQuoteFailure.AUTHENTICATION_REQUIRED)
        } catch (_: BuyerBackendNotConfigured) {
            failed(DeliveryQuoteFailure.CONFIGURATION)
        } catch (_: TimeoutCancellationException) {
            failed(DeliveryQuoteFailure.TIMEOUT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            failed(DeliveryQuoteFailure.TIMEOUT)
        } catch (_: IOException) {
            failed(DeliveryQuoteFailure.CONNECTIVITY)
        } catch (_: SerializationException) {
            failed(DeliveryQuoteFailure.MALFORMED_RESPONSE)
        } catch (_: IllegalArgumentException) {
            failed(DeliveryQuoteFailure.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            failed(DeliveryQuoteFailure.CONFIGURATION)
        }
    }

    private fun mapError(response: Response<DeliveryQuoteResponse>): DeliveryQuoteResult {
        if (response.code() == 401) return failed(DeliveryQuoteFailure.AUTHENTICATION_REQUIRED)
        val code = try {
            val raw = response.errorBody()?.string() ?: return failed(DeliveryQuoteFailure.MALFORMED_RESPONSE)
            json.decodeFromString<DeliveryQuoteErrorResponse>(raw).error
        } catch (_: IOException) {
            return failed(DeliveryQuoteFailure.CONNECTIVITY)
        } catch (_: SerializationException) {
            return failed(DeliveryQuoteFailure.MALFORMED_RESPONSE)
        }
        val reason = when (code) {
            "ADDRESS_NOT_FOUND" -> DeliveryQuoteFailure.ADDRESS_NOT_FOUND
            "STORE_UNAVAILABLE" -> DeliveryQuoteFailure.STORE_UNAVAILABLE
            "BUSINESS_SUSPENDED" -> DeliveryQuoteFailure.BUSINESS_SUSPENDED
            "BUSINESS_NOT_APPROVED" -> DeliveryQuoteFailure.BUSINESS_NOT_APPROVED
            "OUTSIDE_COVERAGE" -> DeliveryQuoteFailure.OUTSIDE_COVERAGE
            "INVALID_LOCATION" -> DeliveryQuoteFailure.INVALID_LOCATION
            "NO_ROUTE" -> DeliveryQuoteFailure.NO_ROUTE
            "ROUTING_QUOTA_EXCEEDED" -> DeliveryQuoteFailure.ROUTING_QUOTA_EXCEEDED
            "ROUTING_CREDENTIALS_MISSING", "ROUTING_CREDENTIALS_INVALID" -> DeliveryQuoteFailure.ROUTING_CONFIGURATION
            "ROUTING_UNAVAILABLE" -> DeliveryQuoteFailure.ROUTING_UNAVAILABLE
            "ROUTING_INVALID_RESPONSE" -> DeliveryQuoteFailure.ROUTING_INVALID_RESPONSE
            "DATA_INCONSISTENT" -> DeliveryQuoteFailure.DATA_INCONSISTENT
            "QUOTE_UNAVAILABLE" -> DeliveryQuoteFailure.SERVER_UNAVAILABLE
            "INVALID_REQUEST" -> DeliveryQuoteFailure.INVALID_REQUEST
            else -> DeliveryQuoteFailure.HTTP_ERROR
        }
        return failed(reason)
    }

    private fun DeliveryQuoteResponse.toDomain(): DeliveryQuote {
        require(currency == "PEN")
        return DeliveryQuote(distanceMeters, deliveryFeeMinor, CartCurrency.PEN, tariffVersion,
            parseUtc(quotedAt), parseUtc(expiresAt),
            AddressCoordinates(storeLocation.latitude, storeLocation.longitude),
            AddressCoordinates(deliveryLocation.latitude, deliveryLocation.longitude))
    }

    private fun parseUtc(value: String): Long {
        require(UTC_TIMESTAMP.matches(value))
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val date = parser.parse(value, position)
        require(date != null && position.index == value.length && parser.format(date) == value)
        return date.time
    }

    private fun failed(reason: DeliveryQuoteFailure) = DeliveryQuoteResult.Failed(reason)

    private companion object {
        val VALID_ID = Regex("[A-Za-z0-9_-]{1,128}")
        val UTC_TIMESTAMP = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
    }
}
