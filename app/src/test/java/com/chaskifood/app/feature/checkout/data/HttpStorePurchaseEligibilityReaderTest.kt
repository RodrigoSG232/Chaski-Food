package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.data.remote.StoreEligibilityApi
import com.chaskifood.app.feature.checkout.data.remote.StoreEligibilityResponse
import com.chaskifood.app.feature.checkout.domain.PurchaseEligibility
import com.chaskifood.app.feature.checkout.domain.PurchaseBlockReason
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HttpStorePurchaseEligibilityReaderTest {
    private class Tokens : BuyerTokenSource {
        var current = true
        override suspend fun token(uid: String, generation: Long) = "fixture-token"
        override fun isCurrent(uid: String, generation: Long) = current
    }
    private fun api(block: suspend () -> Response<StoreEligibilityResponse>) = object : StoreEligibilityApi {
        override suspend fun check(storeId: String, authorization: String): Response<StoreEligibilityResponse> {
            assertEquals("Bearer fixture-token", authorization)
            return block()
        }
    }
    private fun valid(decision: String = "ELIGIBLE") = StoreEligibilityResponse("store", "business", decision, "2026-10-26T00:00:00Z")

    @Test fun `elegible requiere identidad y datos completos`() = runTest {
        for (body in listOf(valid().copy(storeId = "other"), valid().copy(businessId = null),
            valid().copy(checkedAt = ""), valid("UNKNOWN"))) {
            assertEquals(PurchaseEligibility.Unavailable,
                HttpStorePurchaseEligibilityReader(api { Response.success(body) }, Tokens()).check("store", "uid", 1))
        }
        assertEquals(PurchaseEligibility.Eligible("business"),
            HttpStorePurchaseEligibilityReader(api { Response.success(valid()) }, Tokens()).check("store", "uid", 1))
    }
    @Test fun `conserva solo el nombre publico recibido cuando es elegible`() = runTest {
        val body = valid().copy(businessName = "Negocio público")
        assertEquals(PurchaseEligibility.Eligible("business", "Negocio público"),
            HttpStorePurchaseEligibilityReader(api { Response.success(body) }, Tokens()).check("store", "uid", 1))
        assertEquals(PurchaseEligibility.Eligible("business"),
            HttpStorePurchaseEligibilityReader(api { Response.success(body.copy(businessName = " ")) }, Tokens())
                .check("store", "uid", 1))
    }
    @Test fun `suspension se distingue de un fallo tecnico`() = runTest {
        val reader = HttpStorePurchaseEligibilityReader(api { Response.success(valid("BUSINESS_SUSPENDED")) }, Tokens())
        assertEquals(PurchaseEligibility.Blocked(PurchaseBlockReason.BUSINESS_SUSPENDED), reader.check("store", "uid", 1))
    }
    @Test fun `http 401 exige autenticacion y otros errores no habilitan`() = runTest {
        for (code in listOf(401, 403, 429, 500, 503)) {
            val reader = HttpStorePurchaseEligibilityReader(api { Response.error(code, "{}".toResponseBody()) }, Tokens())
            assertEquals(if (code == 401) PurchaseEligibility.AuthenticationRequired else PurchaseEligibility.Unavailable,
                reader.check("store", "uid", 1))
        }
    }
    @Test fun `timeout no habilita y cancela la llamada`() = runTest {
        var cancelled = false
        val reader = HttpStorePurchaseEligibilityReader(api {
            try { delay(20_000); Response.success(valid()) } finally { cancelled = true }
        }, Tokens())
        assertEquals(PurchaseEligibility.Unavailable, reader.check("store", "uid", 1))
        assertTrue(cancelled)
    }
    @Test fun `cancelacion externa se propaga`() = runTest {
        val reader = HttpStorePurchaseEligibilityReader(api { awaitCancellation() }, Tokens())
        val job = async { reader.check("store", "uid", 1) }
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(job.isCancelled)
    }
    @Test fun `otra generacion de sesion descarta respuesta`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val tokens = Tokens()
        val reader = HttpStorePurchaseEligibilityReader(api { gate.await(); Response.success(valid()) }, tokens)
        val call = async { reader.check("store", "uid", 1) }
        runCurrent(); tokens.current = false; gate.complete(Unit)
        assertEquals(PurchaseEligibility.SessionChanged, call.await())
    }
    @Test fun `respuesta HTTP incompleta falla en Retrofit real`() = runBlocking {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* consume cabeceras */ }
                    val body = "{\"decision\":\"ELIGIBLE\"}"
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            }
            val reader = HttpStorePurchaseEligibilityReader(realApi(server.localPort), Tokens())
            assertEquals(PurchaseEligibility.Unavailable, reader.check("store", "uid", 1))
            worker.join(3000)
            assertFalse(worker.isAlive)
        }
    }
    @Test fun `servicio HTTP caido no habilita`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val reader = HttpStorePurchaseEligibilityReader(realApi(port), Tokens())
        assertEquals(PurchaseEligibility.Unavailable, reader.check("store", "uid", 1))
    }
    private fun realApi(port: Int): StoreEligibilityApi = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:$port/")
        .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build())
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .build().create(StoreEligibilityApi::class.java)
}
