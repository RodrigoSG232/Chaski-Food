package com.chaskifood.app.core.firebase

import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.feature.auth.data.FirebaseUserProfileStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Firestore Rules through the Android client SDK against the LOCAL Emulator (demo project only).
 * Fixtures are seeded with the Emulator admin bypass; every assertion runs as a signed-in client. */
class FirestoreRulesClientEmulatorTest {
    private val host = InstrumentationRegistry.getArguments().getString("hu34Host", "10.0.2.2")
        .also { require(it == "10.0.2.2" || it == "127.0.0.1") }
    private val project = "demo-chaski-hu15"
    private val prefix = "rules-client-${UUID.randomUUID().toString().take(8)}"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private fun http(port: Int, method: String, path: String, body: JSONObject?, token: String = "owner"): JSONObject {
        val c = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer $token"); c.setRequestProperty("Content-Type", "application/json")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        assertEquals("Fixture request failed: $method $path", 200, c.responseCode)
        return c.inputStream.bufferedReader().use { JSONObject(it.readText().ifBlank { "{}" }) }.also { c.disconnect() }
    }
    private fun account(email: String): String {
        val uid = http(9099, "POST", "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
            JSONObject().put("email", email).put("password", "emulator-password").put("returnSecureToken", true)).getString("localId")
        http(9099, "POST", "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update",
            JSONObject().put("localId", uid).put("emailVerified", true))
        return uid
    }
    /** Admin-bypass fixture write: only strings and booleans are needed here. */
    private fun seed(path: String, data: Map<String, Any>) {
        val fields = JSONObject()
        data.forEach { (k, v) -> fields.put(k, if (v is Boolean) JSONObject().put("booleanValue", v) else JSONObject().put("stringValue", v.toString())) }
        val name = "projects/$project/databases/(default)/documents/$path"
        http(8089, "POST", "/v1/projects/$project/databases/(default)/documents:commit", JSONObject().put("writes",
            org.json.JSONArray().put(JSONObject().put("update", JSONObject().put("name", name).put("fields", fields)))))
    }
    private suspend fun signIn(email: String) { auth.signOut(); auth.signInWithEmailAndPassword(email, "emulator-password").await() }
    private suspend fun denied(label: String, block: suspend () -> Unit) {
        try { block(); fail("Expected PERMISSION_DENIED: $label") }
        catch (e: FirebaseFirestoreException) { assertEquals(label, FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code) }
    }

    @Before fun setUp() {
        check(BuildConfig.FIREBASE_MODE == "LOCAL")
        val options = FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:rulesclient")
            .setApiKey("demo-api-key").build()
        app = FirebaseApp.initializeApp(context, options, prefix)!!
        auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
        db = FirebaseFirestore.getInstance(app).apply {
            useEmulator(host, 8089); firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
    }
    @After fun tearDown() { auth.signOut(); app.delete() }

    @Test fun realProfileStoreCreatesAndUpdatesOnlyItsOwnProfile() = runBlocking {
        val aEmail = "$prefix-a@example.test"; val bEmail = "$prefix-b@example.test"
        val a = account(aEmail); account(bEmail)
        signIn(aEmail)
        val profiles = FirebaseUserProfileStore(auth, db)
        profiles.ensure(auth.currentUser!!)                        // real sign-in path (FirebaseAuthRepository.loginWithEmail)
        val created = db.document("users/$a").get().await()
        assertEquals(a, created.getString("uid")); assertEquals(aEmail, created.getString("email"))
        profiles.updateDisplayName(auth.currentUser!!, "Nombre ficticio")
        assertEquals("Nombre ficticio", db.document("users/$a").get().await().getString("displayName"))
        denied("profile uid is immutable") { db.document("users/$a").update("uid", "other").await() }
        denied("profile email is immutable") { db.document("users/$a").update("email", "x@example.test").await() }
        denied("profile cannot be deleted") { db.document("users/$a").delete().await() }
        signIn(bEmail)
        denied("foreign profile read") { db.document("users/$a").get().await() }
        denied("foreign profile write") { db.document("users/$a").update("displayName", "Intruso").await() }
        val b = auth.currentUser!!.uid
        denied("extra profile field") {
            db.document("users/$b").set(mapOf("uid" to b, "email" to bEmail, "displayName" to "B", "role" to "ADMIN",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        denied("foreign email in own profile") {
            db.document("users/$b").set(mapOf("uid" to b, "email" to aEmail, "displayName" to "B",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        denied("client timestamps are rejected") {
            db.document("users/$b").set(mapOf("uid" to b, "email" to bEmail, "displayName" to "B",
                "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now())).await()
        }
    }

    @Test fun addressRulesStayOwnerOnlyAndRequireTheOperationProtocol() = runBlocking {
        val aEmail = "$prefix-addr-a@example.test"; val bEmail = "$prefix-addr-b@example.test"
        val a = account(aEmail); account(bEmail)
        signIn(aEmail)
        assertTrue(db.collection("users/$a/addresses").get().await().isEmpty)
        denied("address without operation/preference protocol") {
            db.document("users/$a/addresses/home").set(mapOf("label" to "", "addressText" to "Lima publica",
                "location" to mapOf("lat" to -12.0, "lng" to -77.0), "reference" to "", "instructions" to "",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        signIn(bEmail)
        denied("foreign address list") { db.collection("users/$a/addresses").get().await() }
        denied("foreign delivery preference") { db.document("users/$a/preferences/delivery").get().await() }
    }

    @Test fun storeNoticesKeepPublishedOwnerOnlyPermissions() = runBlocking {
        val ownerEmail = "$prefix-owner@example.test"; val otherEmail = "$prefix-other@example.test"
        val owner = account(ownerEmail); account(otherEmail)
        val notice = "$prefix-notice"
        seed("business_requests/$owner", mapOf("ownerUid" to owner, "status" to "APPROVED", "businessName" to "Negocio ficticio"))
        seed("store_notices/$notice", mapOf("ownerUid" to owner, "storeId" to "$prefix-store", "businessId" to owner,
            "title" to "Aviso", "message" to "Mensaje", "read" to false, "auditId" to notice))
        signIn(ownerEmail)
        assertEquals("Aviso", db.document("store_notices/$notice").get().await().getString("title"))
        denied("owner cannot edit notice content") { db.document("store_notices/$notice").update("title", "Otro").await() }
        db.document("store_notices/$notice").update("read", true).await()
        denied("owner cannot mark unread") { db.document("store_notices/$notice").update("read", false).await() }
        denied("notices cannot be deleted") { db.document("store_notices/$notice").delete().await() }
        denied("non-admin cannot create notices") {
            db.document("store_notices/$prefix-forged").set(mapOf("ownerUid" to owner, "storeId" to "s", "businessId" to owner,
                "title" to "x", "message" to "y", "read" to false, "createdAt" to FieldValue.serverTimestamp(), "auditId" to "$prefix-forged")).await()
        }
        signIn(otherEmail)
        denied("foreign notice read") { db.document("store_notices/$notice").get().await() }
    }

    @Test fun hu34AndHu16RestrictionsStillHoldForClients() = runBlocking {
        val ownerEmail = "$prefix-hu34@example.test"
        val owner = account(ownerEmail)
        seed("business_requests/$owner", mapOf("ownerUid" to owner, "status" to "APPROVED", "businessName" to "Negocio ficticio"))
        signIn(ownerEmail)
        denied("business_requests are written only by foodApi") {
            db.document("business_requests/$prefix-new").set(mapOf("ownerUid" to "$prefix-new", "status" to "PENDING_REVIEW")).await()
        }
        denied("approved owner cannot write store_managers from the client") {
            db.document("store_managers/${owner}_x@example.test").set(mapOf("businessId" to owner, "email" to "x@example.test",
                "assignedStoreIds" to listOf("s"))).await()
        }
        for (path in listOf("commercial_access_index/uid_$owner", "notification_devices/$prefix", "order_events/$prefix",
                "food_orders/$prefix", "delivery_quotes/$prefix", "order_attempts/$owner/intents/$prefix"))
            denied("private collection $path") { db.document(path).get().await() }
    }
}
