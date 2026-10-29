package com.chaskifood.app.core.access.data

import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.domain.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Solo loopback y proyecto ficticio; fixture sin datos ni credenciales de produccion. */
class AccessContextEmulatorTest {
    private val project = "demo-chaski-hu15"
    private val root = "/$project/southamerica-west1/foodApi/api/v1/access/"
    private val host = "127.0.0.1"
    private val prefix = "identity34-${UUID.randomUUID()}"
    private val password = "emulator-password"
    private fun request(port: Int, path: String, body: JSONObject, token: String = "owner"): JSONObject {
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 25_000
            connection.setRequestProperty("Authorization", "Bearer $token"); connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) }
            assertEquals("Solicitud ficticia fallo en $port", 200, connection.responseCode)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    private fun account(email: String, verified: Boolean = true, admin: Boolean = false): String {
        val result = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key", JSONObject().put("email", email).put("password", password).put("returnSecureToken", true))
        val uid = result.getString("localId"); update(uid, JSONObject().put("emailVerified", verified).put("customAttributes", if (admin) "{\"admin\":true}" else "{}")); return uid
    }
    private fun update(uid: String, fields: JSONObject) = request(9099, "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update", fields.put("localId", uid))
    private fun login(email: String) = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)).getString("idToken")
    private fun operation(token: String, action: String, revision: Long, email: String? = null, business: String? = null, stores: List<String>? = null): Long {
        val body = JSONObject().put("action", action).put("operationId", UUID.randomUUID().toString()).put("expectedRevision", revision)
        email?.let { body.put("email", it) }; business?.let { body.put("businessId", it) }
        stores?.let { body.put("storeIds", org.json.JSONArray(it)).put("fullName", "Ficticio").put("phone", "0") }
        return request(5001, root + "operations", body, token).getLong("revision")
    }
    private fun seed(path: String, values: Map<String, String>) {
        val fields = JSONObject(); values.forEach { (k,v) -> fields.put(k, JSONObject().put("stringValue", v)) }
        val write = JSONObject().put("update", JSONObject().put("name", "projects/$project/databases/(default)/documents/$path").put("fields", fields))
        request(8089, "/v1/projects/$project/databases/(default)/documents:commit", JSONObject().put("writes", org.json.JSONArray().put(write)))
    }
    private inner class Fixture {
        val app: FirebaseApp
        val auth: FirebaseAuth
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val source: SharedAccessIdentitySource
        val context: SharedAccessContext
        init {
            assertEquals("LOCAL", BuildConfig.FIREBASE_MODE)
            app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext,
                FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:identity34").setApiKey("demo-key").build(), UUID.randomUUID().toString())!!
            auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
            val driver = FirebaseAccessIdentityDriver(auth); source = SharedAccessIdentitySource(driver, scope)
            val json = Json { ignoreUnknownKeys = true }
            val api = Retrofit.Builder().baseUrl("http://$host:5001/$project/southamerica-west1/foodApi/api/v1/")
                .client(OkHttpClient()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(AccessFactsApi::class.java)
            context = SharedAccessContext(source, RemoteAccessFacts(api, driver, json, true), scope, true)
        }
        suspend fun resolved(experience: CommercialExperience, status: AccessBusinessStatus? = null): AccessResolution.Resolved = withTimeout(25_000) {
            context.states.first { val r = it.resolution as? AccessResolution.Resolved; r?.experience == experience && r.ownership?.status == status }.resolution as AccessResolution.Resolved
        }
        suspend fun close() { auth.signOut(); scope.coroutineContext[Job]!!.cancelAndJoin(); app.delete() }
    }
    @Test fun identidadRealReingresoVerificacionCorreoYReconciliacion() = runBlocking {
        val f = Fixture()
        try {
            val adminEmail = "$prefix-admin@example.test"; account(adminEmail, admin = true); val token = login(adminEmail)
            val email = "$prefix-user@example.test"; val uid = account(email, verified = false); operation(token, "PREPARE", 0, email)
            f.auth.signInWithEmailAndPassword(email, password).await()
            val initial = f.resolved(CommercialExperience.CLIENT); assertEquals(EmailVerification.UNVERIFIED, initial.identity.verification)
            f.auth.signOut(); withTimeout(10_000) { f.context.states.first { it.resolution == AccessResolution.SignedOut } }
            f.auth.signInWithEmailAndPassword(email, password).await()
            val again = f.resolved(CommercialExperience.CLIENT); assertTrue(again.identity.session.generation > initial.identity.session.generation)
            update(uid, JSONObject().put("emailVerified", true)); f.context.refresh()
            try { withTimeout(25_000) { f.context.states.first { (it.resolution as? AccessResolution.Resolved)?.identity?.verification == EmailVerification.VERIFIED } } }
            catch (timeout: TimeoutCancellationException) { throw AssertionError("Verificación: ${f.context.currentState().phase}/${f.context.currentState().problem}", timeout) }
            val newEmail = "$prefix-new@example.test"; update(uid, JSONObject().put("email", newEmail)); f.context.refresh()
            try { withTimeout(25_000) { f.source.states.first { (it.identity as? AccessIdentity.Authenticated)?.email == newEmail } } }
            catch (timeout: TimeoutCancellationException) { throw AssertionError("Correo: ${f.context.currentState().phase}/${f.context.currentState().problem}", timeout) }
            val blocked = try { withTimeout(25_000) { f.context.states.first { it.resolution is AccessResolution.Error && it.problem == AccessCheckProblem.RECONCILIATION_REQUIRED } } }
            catch (timeout: TimeoutCancellationException) { throw AssertionError("Reconciliación: ${f.context.currentState().phase}/${f.context.currentState().problem}", timeout) }
            assertNull(blocked.serverRevision)
        } finally { f.close() }
    }
    @Test fun hechosRealesPropietarioSuspendidoResponsableYRevocacion() = runBlocking {
        val f = Fixture()
        try {
            val adminEmail = "$prefix-admin2@example.test"; account(adminEmail, admin = true); val admin = login(adminEmail)
            val ownerEmail = "$prefix-owner@example.test"; val ownerUid = account(ownerEmail); val ownerToken = login(ownerEmail)
            val managerEmail = "$prefix-manager@example.test"; account(managerEmail)
            operation(admin, "PREPARE", 0, ownerEmail); val managerRevision = operation(admin, "PREPARE", 0, managerEmail)
            val business = mapOf("ownerUid" to ownerUid, "status" to "APPROVED", "businessName" to "Ficticio")
            seed("business_requests/$ownerUid", business)
            val ids = listOf("$ownerUid-one", "$ownerUid-two")
            ids.forEach { seed("stores/$it", mapOf("businessId" to ownerUid, "status" to "ACTIVE", "operationalStatus" to "CLOSED")) }
            f.auth.signInWithEmailAndPassword(ownerEmail, password).await()
            val owner = f.resolved(CommercialExperience.OWNER, AccessBusinessStatus.APPROVED)
            assertTrue(ids.all { owner.capabilities.allows(AccessCapability.OPERATE_HOURS_AND_OFFERS, AccessScope.Store(ownerUid, it)) })
            assertFalse(owner.capabilities.grants.any { it.capability == AccessCapability.READ_STORE_ORDERS || it.capability == AccessCapability.START_PURCHASE })
            seed("business_requests/$ownerUid", business + ("status" to "SUSPENDED")); f.context.refresh()
            val suspended = f.resolved(CommercialExperience.OWNER, AccessBusinessStatus.SUSPENDED)
            assertEquals(2, suspended.capabilities.grants.size)
            seed("business_requests/$ownerUid", business)
            val assignedRevision = operation(ownerToken, "ASSIGN", managerRevision, managerEmail, ownerUid, ids)
            f.auth.signOut(); f.auth.signInWithEmailAndPassword(managerEmail, password).await()
            val manager = f.resolved(CommercialExperience.MANAGER)
            assertTrue(ids.all { manager.capabilities.allows(AccessCapability.READ_STORE_ORDERS, AccessScope.Store(ownerUid, it)) })
            assertFalse(manager.capabilities.grants.any { it.capability == AccessCapability.START_PURCHASE })
            operation(ownerToken, "REVOKE", assignedRevision, managerEmail, ownerUid); f.context.refresh()
            val client = f.resolved(CommercialExperience.CLIENT)
            assertFalse(client.capabilities.grants.any { it.scope is AccessScope.Store })
            assertTrue(client.capabilities.allows(AccessCapability.START_PURCHASE, AccessScope.Account(client.identity.session.uid)))
        } finally { f.close() }
    }
}
