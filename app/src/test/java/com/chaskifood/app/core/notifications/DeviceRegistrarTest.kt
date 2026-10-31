package com.chaskifood.app.core.notifications

import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.checkout.data.BuyerTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class DeviceRegistrarTest {
    private class Sessions(initial: CartSession) : CartSessionSource {
        val state = MutableStateFlow(initial)
        override val sessions: StateFlow<CartSession> = state
        override fun current() = state.value
    }
    private class Tokens(private val sessions: Sessions) : BuyerTokenSource {
        override suspend fun token(uid: String, generation: Long) = "id-token-$uid-$generation"
        override fun isCurrent(uid: String, generation: Long) = sessions.current() == CartSession(uid, generation)
    }
    private class Api : DeviceApi {
        val calls = mutableListOf<String>()
        var putCode = 204
        var lastBody: DeviceRegistration? = null
        override suspend fun put(id: String, bearer: String, body: DeviceRegistration): Response<Unit> {
            lastBody = body
            calls += "PUT:$bearer:${body.token}:${body.bindingVersion}"
            return if (putCode in 200..299) Response.success(204, Unit) else Response.error(putCode, "".toResponseBody())
        }
        override suspend fun remove(id: String, bearer: String, bindingVersion: Long): Response<Unit> {
            calls += "DELETE:$bearer:$bindingVersion"; return Response.success(204, Unit)
        }
    }
    private val a = CartSession("manager-a", 1)
    private val b = CartSession("manager-b", 2)
    private var version = 0L
    private fun registrar(api: Api, sessions: Sessions, push: suspend () -> String = { "fcm-token" }) =
        DeviceRegistrar(api, sessions, Tokens(sessions), "installation", { ++version }, push)

    @Test fun registrationBodyCarriesTheThreeFieldsTheFoodApiRequiresWithTheAppJson() = runTest {
        val api = Api(); val sessions = Sessions(a)
        assertEquals(RegistrationOutcome.REGISTERED, registrar(api, sessions).register(a))
        // Mismo Json que Retrofit (encodeDefaults=false): un campo con valor por defecto se omitiria y daria 400.
        val json = com.chaskifood.app.core.network.NetworkModule.provideJson()
        val encoded = json.parseToJsonElement(json.encodeToString(DeviceRegistration.serializer(), checkNotNull(api.lastBody))) as kotlinx.serialization.json.JsonObject
        assertEquals(setOf("token", "bindingVersion", "platform"), encoded.keys)
        assertEquals("ANDROID", (encoded["platform"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
    @Test fun registersCurrentSessionWithItsOwnIdToken() = runTest {
        val api = Api(); val sessions = Sessions(a)
        assertEquals(RegistrationOutcome.REGISTERED, registrar(api, sessions).register(a))
        assertEquals(listOf("PUT:Bearer id-token-manager-a-1:fcm-token:1"), api.calls)
    }

    @Test fun uidChangeWhileWaitingForTheFcmTokenNeverBindsTheOldOrNewUid() = runTest {
        val api = Api(); val sessions = Sessions(a); val token = CompletableDeferred<String>()
        val registrar = registrar(api, sessions) { token.await() }
        val pending = async { registrar.register(a) }
        testScheduler.runCurrent()
        sessions.state.value = b; token.complete("fcm-token")
        assertEquals(RegistrationOutcome.SESSION_CHANGED, pending.await())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun reloginOfTheSameUidIsANewSessionAndStaleWorkDoesNotBindIt() = runTest {
        val api = Api(); val sessions = Sessions(a); val token = CompletableDeferred<String>()
        val registrar = registrar(api, sessions) { token.await() }
        val pending = async { registrar.register(a) }
        testScheduler.runCurrent()
        sessions.state.value = CartSession("manager-a", 3); token.complete("fcm-token")
        assertEquals(RegistrationOutcome.SESSION_CHANGED, pending.await())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun signOutWaitsForInFlightBindingThenRemovesWithHigherVersionAndBlocksLateRegistrations() = runTest {
        val api = Api(); val sessions = Sessions(a); val token = CompletableDeferred<String>()
        val registrar = registrar(api, sessions) { token.await() }
        val inFlight = async { registrar.register(a) }
        testScheduler.runCurrent()
        val signOut = async { registrar.unregister(a, signOut = true) }
        testScheduler.runCurrent()
        token.complete("fcm-token")
        // The already-started binding observes the sign-out before its PUT.
        assertEquals(RegistrationOutcome.SESSION_CHANGED, inFlight.await()); signOut.await()
        assertEquals(RegistrationOutcome.SESSION_CHANGED, registrar.register(a))
        assertEquals(listOf("DELETE:Bearer id-token-manager-a-1:1"), api.calls)
        // A later session on the same installation registers normally with a newer version.
        sessions.state.value = b
        assertEquals(RegistrationOutcome.REGISTERED, registrar.register(b))
        assertEquals("PUT:Bearer id-token-manager-b-2:fcm-token:2", api.calls.last())
    }

    @Test fun serverDenialIsReportedAndTransportFailuresNeverEscape() = runTest {
        val api = Api().apply { putCode = 403 }; val sessions = Sessions(a)
        assertEquals(RegistrationOutcome.NOT_ELIGIBLE, registrar(api, sessions).register(a))
        api.putCode = 503
        assertEquals(RegistrationOutcome.FAILED, registrar(api, sessions).register(a))
        assertEquals(RegistrationOutcome.FAILED, registrar(api, sessions) { error("FCM unavailable") }.register(a))
        assertEquals(RegistrationOutcome.SESSION_CHANGED, registrar(api, sessions).register(CartSession(null, 1)))
    }
}
