package com.chaskifood.app.feature.business.data

import com.chaskifood.app.feature.business.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class CommercialOperationsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private class Sessions : CommercialSessionSource {
        var current = CommercialSession("a", "a@example.test", Any())
        override fun capture() = current
        override fun isCurrent(session: CommercialSession) = current == session
        override suspend fun token(session: CommercialSession): String {
            if (!isCurrent(session)) throw CommercialFailure("SESSION_CHANGED")
            return "fixture-token"
        }
    }
    private class Store : CommercialAttemptStore {
        val records = mutableMapOf<String, CommercialAttempt>()
        override fun read(uid: String) = records[uid]
        override fun save(attempt: CommercialAttempt) { records[attempt.uid] = attempt }
        override fun complete(uid: String, operationId: String) { if (records[uid]?.command?.operationId == operationId) records.remove(uid) }
    }
    private class Api : CommercialApi {
        val commands = mutableListOf<CommercialCommand>()
        val revisions = mutableListOf<CommercialRevisionRequest>()
        var ownReads = 0
        var factsResponse: Response<CommercialFactsSummary> = Response.success(CommercialFactsSummary("a"))
        var write: suspend (CommercialCommand) -> Response<CommercialResult> = { Response.success(CommercialResult(it.action, 6)) }
        override suspend fun execute(authorization: String, command: CommercialCommand): Response<CommercialResult> {
            assertEquals("Bearer fixture-token", authorization); commands += command; return write(command)
        }
        override suspend fun ownRevision(authorization: String): Response<CommercialRevision> { ownReads++; return Response.success(CommercialRevision(5)) }
        override suspend fun revision(authorization: String, target: CommercialRevisionRequest): Response<CommercialRevision> {
            revisions += target; return Response.success(CommercialRevision(5))
        }
        override suspend fun facts(authorization: String) = factsResponse
    }
    private val api = Api(); private val sessions = Sessions(); private val store = Store()
    private fun gateway() = CommercialOperations(api, sessions, store, json)
    private val mutation = CommercialMutation("ASSIGN", businessId = "business", email = "manager@example.test", storeIds = listOf("store"), fullName = "Prueba", phone = "0")
    private fun <T> error(status: Int, code: String): Response<T> = Response.error(status, "{\"error\":\"$code\"}".toResponseBody("application/json".toMediaType()))
    private suspend fun failure(block: suspend () -> Unit): CommercialFailure {
        try { block(); fail("Se esperaba error") } catch (error: CommercialFailure) { return error }
        throw AssertionError()
    }
    @Test fun `exito limpia intento y usa revision del destinatario`() = runBlocking {
        gateway().execute(mutation, "a"); assertTrue(store.records.isEmpty()); assertEquals(5L, api.commands.single().expectedRevision)
        assertEquals(CommercialRevisionRequest("business", "manager@example.test"), api.revisions.single())
    }
    @Test fun `perdida respuesta conserva ID revision y datos al recrear adaptador`() = runBlocking {
        api.write = { throw IOException("respuesta perdida") }
        assertTrue(failure { gateway().execute(mutation, "a") }.uncertain)
        val first = api.commands.single(); assertNotNull(store.read("a"))
        api.write = { Response.success(CommercialResult(it.action, 6)) }
        gateway().execute(mutation, "a"); assertEquals(first, api.commands.last()); assertEquals(1, api.revisions.size)
    }
    @Test fun `cambiar datos no sobrescribe intento incierto`() = runBlocking {
        api.write = { throw IOException() }; failure { gateway().execute(mutation, "a") }
        val pending = store.read("a"); assertEquals("PENDING_OPERATION", failure { gateway().execute(mutation.copy(phone = "nuevo"), "a") }.code)
        assertEquals(pending, store.read("a")); assertEquals(1, api.commands.size)
    }
    @Test fun `error temporal es distinto de conflicto y conserva intento`() = runBlocking {
        api.write = { error(503, "UNAVAILABLE") }; val temporary = failure { gateway().execute(mutation, "a") }
        assertTrue(temporary.uncertain); assertNotNull(store.read("a"))
        api.write = { error(409, "AUTHORIZATION_CONFLICT") }; val conflict = failure { gateway().execute(mutation, "a") }
        assertFalse(conflict.uncertain); assertEquals("AUTHORIZATION_CONFLICT", conflict.code); assertNull(store.read("a"))
    }
    @Test fun `revision obsoleta no se reintenta automaticamente`() = runBlocking {
        api.write = { error(409, "STALE_REVISION") }; assertEquals("STALE_REVISION", failure { gateway().execute(mutation, "a") }.code)
        assertEquals(1, api.commands.size); assertNull(store.read("a"))
    }
    @Test fun `sesion cambiada no publica exito ni borra intento anterior`() = runBlocking {
        api.write = { sessions.current = CommercialSession("b", "b@example.test", Any()); Response.success(CommercialResult(it.action, 6)) }
        assertEquals("SESSION_CHANGED", failure { gateway().execute(mutation, "a") }.code)
        assertNotNull(store.read("a")); assertNull(store.read("b"))
    }
    @Test fun `logout login mismo UID no acepta respuesta vieja`() = runBlocking {
        api.write = { sessions.current = sessions.current.copy(identityKey = Any()); Response.success(CommercialResult(it.action, 6)) }
        assertEquals("SESSION_CHANGED", failure { gateway().execute(mutation, "a") }.code)
        assertNotNull(store.read("a"))
    }
    @Test fun `identidad distinta antes de empezar no envia escritura`() = runBlocking {
        assertEquals("SESSION_CHANGED", failure { gateway().execute(mutation, "b") }.code); assertTrue(api.commands.isEmpty())
    }
    @Test fun `cancelacion conserva intento sin convertirlo en conflicto`() = runBlocking {
        api.write = { throw CancellationException("cancelado") }
        try { gateway().execute(mutation, "a"); fail() } catch (_: CancellationException) { }
        assertNotNull(store.read("a"))
    }
    @Test fun `aprobacion usa UID de solicitud para revision no correo comercial`() = runBlocking {
        gateway().execute(CommercialMutation("APPROVE", businessId = "owner"), "a")
        assertEquals(CommercialRevisionRequest("owner"), api.revisions.single())
    }
    @Test fun `registro reenvio y bind usan revision propia`() = runBlocking {
        for (action in listOf("REGISTER", "RESUBMIT", "BIND")) gateway().execute(CommercialMutation(action), "a")
        assertEquals(3, api.ownReads); assertTrue(api.revisions.isEmpty())
    }
    @Test fun `cambio correo captura dos revisiones y payload estable`() = runBlocking {
        gateway().execute(mutation.copy(previousEmail = "old@example.test"), "a")
        assertEquals(2, api.revisions.size); assertEquals(5L, api.commands.single().previousRevision)
    }
    @Test fun `bind solo ante reconciliacion no ante fallos temporales`() = runBlocking {
        api.factsResponse = error(503, "SERVICE_UNAVAILABLE")
        assertEquals("SERVICE_UNAVAILABLE", failure { gateway().ensureBinding("a") }.code); assertTrue(api.commands.isEmpty())
        api.factsResponse = error(409, "RECONCILIATION_REQUIRED"); gateway().ensureBinding("a")
        assertEquals("BIND", api.commands.single().action)
    }
    @Test fun `hechos ya validos no repiten bind`() = runBlocking { gateway().ensureBinding("a"); assertTrue(api.commands.isEmpty()) }
    @Test fun `respuesta malformada no olvida intento`() = runBlocking {
        api.write = { Response.success(CommercialResult("OTHER", -1)) }
        assertTrue(failure { gateway().execute(mutation, "a") }.uncertain); assertNotNull(store.read("a"))
    }
    @Test fun `401 conserva mismo intento para recuperacion`() = runBlocking {
        api.write = { error(401, "AUTHENTICATION_REQUIRED") }; failure { gateway().execute(mutation, "a") }
        assertNotNull(store.read("a")); assertEquals(1, api.commands.size)
    }

    @Test fun `cambio verificacion invalida respuesta pendiente`() = runBlocking {
        api.write = { sessions.current = sessions.current.copy(emailVerified = true); Response.success(CommercialResult(it.action, 6)) }
        assertEquals("SESSION_CHANGED", failure { gateway().execute(mutation, "a") }.code)
        assertNotNull(store.read("a"))
    }

    @Test fun `sesion cambiada durante validaciones previas no comienza otra mutacion`() = runBlocking {
        val old = sessions.current.identityKey
        sessions.current = sessions.current.copy(identityKey = Any())
        assertEquals("SESSION_CHANGED", failure { gateway().execute(mutation, "a", old) }.code)
        assertTrue(api.commands.isEmpty()); assertTrue(store.records.isEmpty())
    }
}
