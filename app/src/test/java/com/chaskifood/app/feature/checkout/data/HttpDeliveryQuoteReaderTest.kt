package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteApi
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteRequest
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteResponse
import com.chaskifood.app.feature.checkout.data.remote.QuoteLocationResponse
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteFailure
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteResult
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HttpDeliveryQuoteReaderTest {
    private class Tokens : BuyerTokenSource {
        var current = true
        override suspend fun token(uid: String, generation: Long): String = "fixture-token"
        override fun isCurrent(uid: String, generation: Long) = current
    }

    private val json = Json { ignoreUnknownKeys = true }
    private fun valid() = DeliveryQuoteResponse(1_918.8, 380, "PEN", "lima-driving-car-v1",
        "2026-10-26T12:00:00.000Z", "2026-10-26T12:05:00.000Z",
        QuoteLocationResponse(-12.04596, -77.03054), QuoteLocationResponse(-12.0515, -77.0345), "quote-fixture")

    private fun api(block: suspend (DeliveryQuoteRequest, String) -> Response<DeliveryQuoteResponse>) =
        object : DeliveryQuoteApi {
            override suspend fun quote(request: DeliveryQuoteRequest, authorization: String) = block(request, authorization)
        }

    private fun reader(api: DeliveryQuoteApi, tokens: Tokens = Tokens()) = HttpDeliveryQuoteReader(api, tokens, json)
    private suspend fun HttpDeliveryQuoteReader.quote() = quote("store", "address", "uid", 1)
    private fun failure(reason: DeliveryQuoteFailure) = DeliveryQuoteResult.Failed(reason)

    @Test fun `cotizacion valida conserva importe del servidor sin recalcularlo`() = runTest {
        val result = reader(api { request, authorization ->
            assertEquals(DeliveryQuoteRequest("store", "address"), request)
            assertEquals("Bearer fixture-token", authorization)
            Response.success(valid())
        }).quote()
        assertTrue(result is DeliveryQuoteResult.Quoted)
        val quote = (result as DeliveryQuoteResult.Quoted).quote
        assertEquals("quote-fixture", quote.quoteId)
        assertEquals(380L, quote.deliveryFeeMinor)
        assertEquals(1_918.8, quote.distanceMeters, 0.0)
        assertEquals("lima-driving-car-v1", quote.tariffVersion)
        assertEquals(300_000L, quote.expiresAtEpochMillis - quote.quotedAtEpochMillis)
        assertEquals(-12.04596, quote.storeLocation.latitude, 0.0)
        assertEquals(-77.0345, quote.deliveryLocation.longitude, 0.0)
    }

    @Test fun `respuesta monetaria o temporal incorrecta no produce cotizacion`() = runTest {
        val invalid = listOf(
            valid().copy(quoteId = ""), valid().copy(quoteId = "other/quote"),
            valid().copy(deliveryFeeMinor = 9_007_199_254_740_992L), valid().copy(deliveryFeeMinor = -1), valid().copy(distanceMeters = Double.NaN),
            valid().copy(distanceMeters = -1.0), valid().copy(currency = "USD"),
            valid().copy(tariffVersion = " "), valid().copy(quotedAt = "not-a-date"),
            valid().copy(expiresAt = "2026-10-26T12:00:00.000Z"),
            valid().copy(storeLocation = QuoteLocationResponse(Double.NaN, -77.0)),
            valid().copy(deliveryLocation = QuoteLocationResponse(91.0, -77.0)),
        )
        for (body in invalid) assertEquals(failure(DeliveryQuoteFailure.MALFORMED_RESPONSE),
            reader(api { _, _ -> Response.success(body) }).quote())
    }

    @Test fun `errores del backend mantienen causas distinguibles`() = runTest {
        val codes = mapOf(
            "ADDRESS_NOT_FOUND" to DeliveryQuoteFailure.ADDRESS_NOT_FOUND,
            "STORE_UNAVAILABLE" to DeliveryQuoteFailure.STORE_UNAVAILABLE,
            "BUSINESS_SUSPENDED" to DeliveryQuoteFailure.BUSINESS_SUSPENDED,
            "BUSINESS_NOT_APPROVED" to DeliveryQuoteFailure.BUSINESS_NOT_APPROVED,
            "OUTSIDE_COVERAGE" to DeliveryQuoteFailure.OUTSIDE_COVERAGE,
            "INVALID_LOCATION" to DeliveryQuoteFailure.INVALID_LOCATION,
            "NO_ROUTE" to DeliveryQuoteFailure.NO_ROUTE,
            "ROUTING_UNAVAILABLE" to DeliveryQuoteFailure.ROUTING_UNAVAILABLE,
            "ROUTING_QUOTA_EXCEEDED" to DeliveryQuoteFailure.ROUTING_QUOTA_EXCEEDED,
            "ROUTING_CREDENTIALS_INVALID" to DeliveryQuoteFailure.ROUTING_CONFIGURATION,
            "ROUTING_INVALID_RESPONSE" to DeliveryQuoteFailure.ROUTING_INVALID_RESPONSE,
            "QUOTE_UNAVAILABLE" to DeliveryQuoteFailure.SERVER_UNAVAILABLE,
            "DATA_INCONSISTENT" to DeliveryQuoteFailure.DATA_INCONSISTENT,
            "QUOTE_NOT_FOUND" to DeliveryQuoteFailure.QUOTE_NOT_FOUND,
            "QUOTE_EXPIRED" to DeliveryQuoteFailure.QUOTE_EXPIRED,
            "QUOTE_INCOMPATIBLE" to DeliveryQuoteFailure.QUOTE_INCOMPATIBLE,
            "QUOTE_VERSION_INVALID" to DeliveryQuoteFailure.QUOTE_VERSION_INVALID,
            "QUOTE_INVALID" to DeliveryQuoteFailure.DATA_INCONSISTENT,
        )
        for ((code, expected) in codes) {
            val response = Response.error<DeliveryQuoteResponse>(503, "{\"error\":\"$code\"}".toResponseBody())
            assertEquals(failure(expected), reader(api { _, _ -> response }).quote())
        }
        assertEquals(failure(DeliveryQuoteFailure.AUTHENTICATION_REQUIRED),
            reader(api { _, _ -> Response.error(401, "{}".toResponseBody()) }).quote())
        assertEquals(failure(DeliveryQuoteFailure.MALFORMED_RESPONSE),
            reader(api { _, _ -> Response.error(503, "{}".toResponseBody()) }).quote())
    }

    @Test fun `cambio de sesion descarta la respuesta y cancelacion externa se propaga`() = runTest {
        val tokens = Tokens()
        val pending = reader(api { _, _ -> delay(100); tokens.current = false; Response.success(valid()) }, tokens)
        assertEquals(failure(DeliveryQuoteFailure.SESSION_CHANGED), pending.quote())
        val cancelled = async { reader(api { _, _ -> awaitCancellation() }).quote() }
        runCurrent(); cancelled.cancel(); runCurrent()
        assertTrue(cancelled.isCancelled)
    }

    @Test fun `token ausente o sesion sustituida no llaman al servidor`() = runTest {
        val unreachable = api { _, _ -> fail("La API no debe recibir una llamada"); Response.success(valid()) }
        val auth = object : BuyerTokenSource {
            override suspend fun token(uid: String, generation: Long): String = throw BuyerAuthenticationRequired()
            override fun isCurrent(uid: String, generation: Long) = true
        }
        val session = object : BuyerTokenSource {
            override suspend fun token(uid: String, generation: Long): String = throw BuyerSessionChanged()
            override fun isCurrent(uid: String, generation: Long) = false
        }
        assertEquals(failure(DeliveryQuoteFailure.AUTHENTICATION_REQUIRED),
            HttpDeliveryQuoteReader(unreachable, auth, json).quote())
        assertEquals(failure(DeliveryQuoteFailure.SESSION_CHANGED),
            HttpDeliveryQuoteReader(unreachable, session, json).quote())
    }

    @Test fun `timeout de cotizacion no se interpreta como tarifa cero`() = runTest {
        assertEquals(failure(DeliveryQuoteFailure.TIMEOUT),
            reader(api { _, _ -> delay(20_000); Response.success(valid()) }).quote())
    }

    @Test fun `contrato HTTP real envia POST JSON y token y deserializa la respuesta`() = kotlinx.coroutines.runBlocking {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    assertEquals("POST /api/v1/delivery-quotes HTTP/1.1", input.readLine())
                    val headers = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    assertTrue(headers.any { it.equals("Authorization: Bearer fixture-token", ignoreCase = true) })
                    val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(':').trim().toInt()
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) read += input.read(body, read, length - read)
                    assertEquals("{\"storeId\":\"store\",\"addressId\":\"address\"}", String(body))
                    val payload = """{"quoteId":"quote-fixture","distanceMeters":1918.8,"deliveryFeeMinor":380,"currency":"PEN","tariffVersion":"lima-driving-car-v1","quotedAt":"2026-10-26T12:00:00.000Z","expiresAt":"2026-10-26T12:05:00.000Z","storeLocation":{"latitude":-12.04596,"longitude":-77.03054},"deliveryLocation":{"latitude":-12.0515,"longitude":-77.0345}}"""
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${payload.toByteArray().size}\r\nConnection: close\r\n\r\n$payload").toByteArray())
                }
            }
            val retrofit = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.localPort}/api/v1/")
                .client(OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            assertTrue(reader(retrofit.create(DeliveryQuoteApi::class.java)).quote() is DeliveryQuoteResult.Quoted)
            worker.join(3000)
            assertFalse(worker.isAlive)
        }
    }

    @Test fun `relogin del mismo UID conserva referencia sin reutilizar generacion anterior`() = runTest {
        val generations = mutableListOf<Long>()
        val tokens = object : BuyerTokenSource {
            override suspend fun token(uid: String, generation: Long): String {
                assertEquals("uid", uid); generations += generation; return "fixture-token-$generation"
            }
            override fun isCurrent(uid: String, generation: Long) = true
        }
        val client = HttpDeliveryQuoteReader(api { _, _ -> Response.success(valid()) }, tokens, json)
        val first = client.quote("store", "address", "uid", 1) as DeliveryQuoteResult.Quoted
        val second = client.quote("store", "address", "uid", 3) as DeliveryQuoteResult.Quoted
        assertEquals(listOf(1L, 3L), generations)
        assertEquals("quote-fixture", first.quote.quoteId)
        assertEquals(first.quote, second.quote)
    }

    @Test fun `JSON incompleto y conexion fallida no producen tarifa`() = kotlinx.coroutines.runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val retrofit = Retrofit.Builder().baseUrl("http://127.0.0.1:$port/api/v1/")
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        assertEquals(failure(DeliveryQuoteFailure.CONNECTIVITY),
            reader(retrofit.create(DeliveryQuoteApi::class.java)).quote())
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* consume cabeceras */ }
                    val payload = "{\"distanceMeters\":100}"
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${payload.toByteArray().size}\r\nConnection: close\r\n\r\n$payload").toByteArray())
                }
            }
            val incomplete = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.localPort}/api/v1/")
                .client(OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            assertEquals(failure(DeliveryQuoteFailure.MALFORMED_RESPONSE),
                reader(incomplete.create(DeliveryQuoteApi::class.java)).quote())
            worker.join(3000)
            assertFalse(worker.isAlive)
        }
    }
}
