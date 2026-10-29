package com.chaskifood.app.feature.business.data

import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.business.data.remote.*
import com.chaskifood.app.feature.business.domain.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
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

/** FirebaseApp aislado, sin usar configuracion ni datos del proyecto compartido. */
class CommercialRepositoryEmulatorTest {
    private val host = InstrumentationRegistry.getArguments().getString("hu34Host", "10.0.2.2")
        .also { require(it == "10.0.2.2" || it == "127.0.0.1") }
    private val project = "demo-chaski-hu15"
    private val prefix = "android34-${UUID.randomUUID()}"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val json = Json { ignoreUnknownKeys = true }
    private fun request(port: Int, path: String, body: JSONObject? = null, token: String = "owner"): JSONObject {
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = if (body == null) "GET" else "POST"
        connection.connectTimeout = 10_000; connection.readTimeout = 25_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) } }
        val status = connection.responseCode
        assertEquals("Solicitud ficticia fallo: $port $path", 200, status)
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }.also { connection.disconnect() }
    }
    private fun account(email: String, admin: Boolean = false): String {
        val created = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key", JSONObject().put("email", email).put("password", "emulator-password").put("returnSecureToken", true))
        val uid = created.getString("localId")
        request(9099, "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update", JSONObject().put("localId", uid).put("emailVerified", true).put("customAttributes", if (admin) "{\"admin\":true}" else "{}"))
        return uid
    }
    private fun login(email: String) = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key", JSONObject().put("email", email).put("password", "emulator-password").put("returnSecureToken", true)).getString("idToken")
    private fun prepare(email: String, adminToken: String) {
        request(5001, "/$project/southamerica-west1/foodApi/api/v1/access/operations", JSONObject().put("action", "PREPARE").put("email", email).put("expectedRevision", 0).put("operationId", UUID.randomUUID().toString()), adminToken)
    }
    @Test fun repositoriosRealesRegistroAprobacionAsignacionRevocacionYHU07() = runBlocking {
        assertEquals("LOCAL", BuildConfig.FIREBASE_MODE)
        val options = FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:commercial34")
            .setApiKey("demo-api-key").setStorageBucket("demo-chaski-hu15.appspot.com").build()
        val app = FirebaseApp.initializeApp(context, options, prefix)!!
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
        val db = FirebaseFirestore.getInstance(app).apply { useEmulator(host, 8089); firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build() }
        val attempts = PreferenceCommercialAttemptStore(context, json)
        val gateway = CommercialOperations(Retrofit.Builder().baseUrl("http://$host:5001/$project/southamerica-west1/foodApi/api/v1/")
            .client(OkHttpClient()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(CommercialApi::class.java), FirebaseCommercialSessionSource(auth), attempts, json)
        val access = FirebaseAccessControl(auth, db)
        val businesses = FirebaseBusinessRepository(db, access, gateway)
        val stores = FirebaseStoreRepository(db, FirebaseStorage.getInstance(app), access, gateway)
        try {
            val adminEmail = "$prefix-admin@example.test"; val ownerEmail = "$prefix-owner@example.test"; val managerEmail = "$prefix-manager@example.test"
            account(adminEmail, true); val uid = account(ownerEmail); account(managerEmail)
            val adminToken = login(adminEmail)
            prepare(ownerEmail, adminToken); prepare(managerEmail, adminToken)
            auth.signInWithEmailAndPassword(ownerEmail, "emulator-password").await()
            val draft = BusinessRequest(ownerUid = uid, businessName = "Prueba Android", ruc = "12345678901", legalAddress = "Lima publica", phone = "000", email = "contact@example.test")
            assertTrue(businesses.submitBusinessRequest(draft) is ApiResult.Success)
            assertEquals("PENDING_REVIEW", db.document("business_requests/$uid").get().await().getString("status"))
            auth.signOut(); auth.signInWithEmailAndPassword(adminEmail, "emulator-password").await()
            assertTrue(businesses.evaluateBusinessRequest(uid, BusinessStatus.OBSERVED, "Corregir", adminEmail) is ApiResult.Success)
            auth.signOut(); auth.signInWithEmailAndPassword(ownerEmail, "emulator-password").await()
            assertTrue(businesses.resubmitBusinessRequest(draft.copy(id = uid)) is ApiResult.Success)
            auth.signOut(); auth.signInWithEmailAndPassword(adminEmail, "emulator-password").await()
            assertTrue(businesses.evaluateBusinessRequest(uid, BusinessStatus.APPROVED, null, adminEmail) is ApiResult.Success)
            auth.signOut(); auth.signInWithEmailAndPassword(ownerEmail, "emulator-password").await()
            val created = stores.createStore(BusinessStore(businessId = uid, name = "Local ficticio", address = "Lima", latitude = -12.0464, longitude = -77.0428, phone = "000"))
            assertTrue(created is ApiResult.Success)
            val storeId = (created as ApiResult.Success).data.id
            val manager = StoreManager(businessId = uid, email = managerEmail, fullName = "Ficticio", phone = "000", assignedStoreIds = listOf(storeId))
            assertTrue(stores.assignManager(manager) is ApiResult.Success)
            auth.signOut(); auth.signInWithEmailAndPassword(managerEmail, "emulator-password").await()
            val assigned = withTimeout(15_000) { stores.getStoresForManager(managerEmail).first() }
            assertTrue(assigned is ApiResult.Success); assertEquals(storeId, (assigned as ApiResult.Success).data.single().id)
            assertTrue(stores.updateOperationalStatus(storeId, OperationalStatus.PAUSED, "Prueba") is ApiResult.Success)
            auth.signOut(); auth.signInWithEmailAndPassword(adminEmail, "emulator-password").await()
            assertTrue(businesses.suspendBusiness(uid, "Prueba", adminEmail, "Ficticio", "12345678901") is ApiResult.Success)
            assertEquals("INACTIVE", db.document("stores/$storeId").get().await().getString("status"))
            assertTrue(businesses.reactivateBusiness(uid, adminEmail, "Ficticio", "12345678901") is ApiResult.Success)
            assertEquals("INACTIVE", db.document("stores/$storeId").get().await().getString("status"))
            auth.signOut(); auth.signInWithEmailAndPassword(ownerEmail, "emulator-password").await()
            assertTrue(stores.revokeManager(uid, managerEmail) is ApiResult.Success)
            val managers = withTimeout(15_000) { stores.getManagersByBusiness(uid).first() }
            assertTrue(managers is ApiResult.Success); assertTrue((managers as ApiResult.Success).data.isEmpty())
        } finally { auth.signOut(); db.terminate().await(); app.delete() }
    }
    @Test fun intentoPrivadoSeRecuperaAlRecrearAlmacen() {
        val uid = "journal-${UUID.randomUUID()}"; val id = UUID.randomUUID().toString()
        val record = CommercialAttempt(uid, "fixture@example.test", CommercialMutation("REGISTER"), CommercialCommand("REGISTER", id, 7))
        val first = PreferenceCommercialAttemptStore(context, json); first.save(record)
        val recovered = PreferenceCommercialAttemptStore(context, json)
        assertEquals(record, recovered.read(uid)); assertNull(recovered.read("other-$uid"))
        recovered.complete(uid, "wrong-id"); assertNotNull(recovered.read(uid))
        recovered.complete(uid, id); assertNull(recovered.read(uid))
    }
}
