package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteAccessFactsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val user = AccessIdentity.Authenticated(AccessSession("a", 1), "a@example.test", EmailVerification.VERIFIED)
    private val state = AccessIdentityState(user, AccessProvenance(user.session, 1, 1), IdentityAvailability.READY, "key")
    private val empty = AccessFactsDto("a", 5, "VERIFIED", null, emptyList(), emptyList())
    private var response: Response<AccessFactsDto> = Response.success(empty)
    private var calls = 0
    private val api = object : AccessFactsApi { override suspend fun facts(authorization: String): Response<AccessFactsDto> {
        assertEquals("Bearer fixture", authorization); calls++; return response
    } }
    private val credentials = object : AccessCredentialSource { override suspend fun token(expected: AccessIdentityState): String { assertEquals(state, expected); return "fixture" } }
    private fun reader(enabled: Boolean = true) = RemoteAccessFacts(api, credentials, json, enabled)
    private fun owner(status: String = "APPROVED") = OwnershipDto("a", "a", status, null)
    private fun store(id: String = "s", business: String = "a", status: String = "ACTIVE") = StoreDto(id, business, status, "CLOSED", "APPROVED")
    private fun assignment(uid: String = "a") = AssignmentDto("b_a@example.test", "b", "a@example.test", listOf("s", "t"), "VERIFIED", uid)
    private fun error(status: Int, code: String) { response = Response.error(status, "{\"error\":\"$code\"}".toResponseBody("application/json".toMediaType())) }
    @Test fun `ausencia confirmada permite cliente pero no es error`() = runBlocking {
        val r = reader().read(state); assertEquals(5L, r.revision); assertNull(r.problem)
        assertEquals(CommercialExperience.CLIENT, (ResolveAccess()(user, r.facts) as AccessResolution.Resolved).experience)
    }
    @Test fun `propietario varios locales sin bandeja ni nuevas compras`() = runBlocking {
        response = Response.success(empty.copy(ownership = owner(), stores = listOf(store(), store("t", status = "INACTIVE"))))
        val r = ResolveAccess()(user, reader().read(state).facts) as AccessResolution.Resolved
        assertEquals(CommercialExperience.OWNER, r.experience)
        assertTrue(r.capabilities.allows(AccessCapability.OPERATE_HOURS_AND_OFFERS, AccessScope.Store("a", "s")))
        assertFalse(r.capabilities.allows(AccessCapability.OPERATE_HOURS_AND_OFFERS, AccessScope.Store("a", "t")))
        assertFalse(r.capabilities.grants.any { it.capability in setOf(AccessCapability.READ_STORE_ORDERS, AccessCapability.START_PURCHASE) })
        assertTrue(r.capabilities.allows(AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account("a")))
    }
    @Test fun `suspendido conserva identidad sin gestion`() = runBlocking {
        response = Response.success(empty.copy(ownership = owner("SUSPENDED")))
        val r = ResolveAccess()(user, reader().read(state).facts) as AccessResolution.Resolved
        assertEquals(CommercialExperience.OWNER, r.experience)
        assertEquals(setOf(AccessCapability.READ_PERSONAL_PURCHASES, AccessCapability.READ_OWN_BUSINESS_STATUS), r.capabilities.grants.map { it.capability }.toSet())
    }
    @Test fun `pendiente no convierte en propietario aprobado`() = runBlocking {
        response = Response.success(empty.copy(ownership = owner("PENDING_REVIEW")))
        assertEquals(CommercialExperience.CLIENT, (ResolveAccess()(user, reader().read(state).facts) as AccessResolution.Resolved).experience)
    }
    @Test fun `responsable conserva ambitos sin privilegios globales`() = runBlocking {
        response = Response.success(empty.copy(assignments = listOf(assignment()), stores = listOf(store(business = "b"), store("t", "b"))))
        val r = ResolveAccess()(user, reader().read(state).facts) as AccessResolution.Resolved
        assertEquals(CommercialExperience.MANAGER, r.experience)
        assertTrue(r.capabilities.allows(AccessCapability.READ_STORE_ORDERS, AccessScope.Store("b", "s")))
        assertFalse(r.capabilities.allows(AccessCapability.READ_STORE_ORDERS, AccessScope.Store("b", "foreign")))
        assertFalse(r.capabilities.grants.any { it.capability == AccessCapability.START_PURCHASE })
    }
    @Test fun `datos parciales uid ajeno binding pendiente y estados invalidos no conceden cliente`() = runBlocking {
        for (dto in listOf(empty.copy(uid = "b"), empty.copy(revision = -1), empty.copy(exclusivity = "UNKNOWN"),
            empty.copy(assignments = listOf(assignment("foreign"))), empty.copy(assignments = listOf(assignment().copy(bindingState = "PENDING"))),
            empty.copy(stores = listOf(store())), empty.copy(ownership = owner(), stores = listOf(store(status = "UNKNOWN"))),
            empty.copy(ownership = owner("SUSPENDED"), stores = listOf(store())), empty.copy(assignments = listOf(assignment())))) {
            response = Response.success(dto); assertEquals(AccessCheckProblem.INVALID_RESPONSE, reader().read(state).problem)
        }
    }
    @Test fun `errores HTTP se distinguen sin generar permisos`() = runBlocking {
        for ((status, code, expected) in listOf(Triple(401, "AUTHENTICATION_REQUIRED", AccessCheckProblem.AUTHENTICATION_REQUIRED),
            Triple(403, "FORBIDDEN", AccessCheckProblem.FORBIDDEN), Triple(403, "EMAIL_VERIFICATION_REQUIRED", AccessCheckProblem.EMAIL_VERIFICATION_REQUIRED),
            Triple(409, "PREPARATION_REQUIRED", AccessCheckProblem.PREPARATION_REQUIRED), Triple(409, "RECONCILIATION_REQUIRED", AccessCheckProblem.RECONCILIATION_REQUIRED),
            Triple(503, "DATA_INCONSISTENT", AccessCheckProblem.UNAVAILABLE))) {
            error(status, code); val r = reader().read(state); assertEquals(expected, r.problem); assertTrue(ResolveAccess()(user, r.facts) is AccessResolution.Error)
        }
    }
    @Test fun `conflicto HTTP confirmado conserva solo historial personal`() = runBlocking {
        error(409, "AUTHORIZATION_CONFLICT"); val result = ResolveAccess()(user, reader().read(state).facts) as AccessResolution.Conflict
        assertEquals(setOf(AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account("a"))), result.capabilities.grants)
    }
    @Test fun `configuracion no autorizada no consulta endpoint ni fabrica permisos`() = runBlocking {
        assertEquals(AccessCheckProblem.ENVIRONMENT_UNAVAILABLE, reader(false).read(state).problem); assertEquals(0, calls)
    }
    @Test fun `JSON incompleto y numeros incompatibles no son hechos`() {
        for (payload in listOf("{}", "{\"uid\":\"a\",\"revision\":1.5}", "{\"uid\":\"a\",\"revision\":1,\"exclusivity\":\"VERIFIED\",\"assignments\":[],\"stores\":[]}")) {
            try { json.decodeFromString<AccessFactsDto>(payload); fail("Debe rechazar respuesta parcial") } catch (_: kotlinx.serialization.SerializationException) { }
        }
    }
    @Test fun `cancelacion no se convierte en fallo de consulta`() = runBlocking {
        val remote = RemoteAccessFacts(object : AccessFactsApi { override suspend fun facts(authorization: String): Response<AccessFactsDto> = throw CancellationException() }, credentials, json, true)
        try { remote.read(state); fail() } catch (_: CancellationException) { }
    }
}
