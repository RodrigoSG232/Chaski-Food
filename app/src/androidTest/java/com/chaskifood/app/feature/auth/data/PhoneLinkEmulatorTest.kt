package com.chaskifood.app.feature.auth.data

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.ChaskiApplication
import com.chaskifood.app.MainActivity
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.datastore.SessionDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import kotlin.random.Random

/** D2-H2 with the real FirebaseAuthRepository against the LOCAL Auth/Firestore Emulator (demo project, no SMS).
 * The Emulator exposes verification codes; sessionInfo is the client's verificationId. */
class PhoneLinkEmulatorTest {
    private val host = InstrumentationRegistry.getArguments().getString("hu34Host", "10.0.2.2")
        .also { require(it == "10.0.2.2" || it == "127.0.0.1") }
    private val project = "demo-chaski-hu15"
    private val prefix = "phone-link-${UUID.randomUUID().toString().take(8)}"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseAuthRepository

    private fun http(method: String, path: String, body: JSONObject? = null): JSONObject {
        val c = URL("http://$host:9099$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer owner"); c.setRequestProperty("Content-Type", "application/json")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        assertEquals("Emulator request failed: $method $path", 200, c.responseCode)
        return c.inputStream.bufferedReader().use { JSONObject(it.readText().ifBlank { "{}" }) }.also { c.disconnect() }
    }
    private fun phone() = "+519" + (10_000_000 + Random.nextInt(89_999_999)).toString()
    /** Same Emulator API the client SDK uses to request a code; returns (verificationId, code). */
    private fun verification(phone: String): Pair<String, String> {
        val session = http("POST", "/identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=demo-api-key",
            JSONObject().put("phoneNumber", phone)).getString("sessionInfo")
        val codes = http("GET", "/emulator/v1/projects/$project/verificationCodes").getJSONArray("verificationCodes")
        val code = (0 until codes.length()).map { codes.getJSONObject(it) }.last { it.getString("sessionInfo") == session }.getString("code")
        return session to code
    }
    /** Real repository request (records the requesting UID); the code is read back from the Emulator. */
    private fun requestedThroughRepository(phone: String): Pair<String, String> =
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val sent = runBlocking { repository.sendPhoneVerificationCode(activity!!, phone) }
            assertTrue(sent.toString(), sent is ApiResult.Success)
            val verificationId = (sent as ApiResult.Success).data
            val codes = http("GET", "/emulator/v1/projects/$project/verificationCodes").getJSONArray("verificationCodes")
            verificationId to (0 until codes.length()).map { codes.getJSONObject(it) }.last { it.getString("phoneNumber") == phone }.getString("code")
        }
    private fun accountsWithPhone(phone: String): Int =
        http("POST", "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:lookup", JSONObject().put("phoneNumber",
            org.json.JSONArray().put(phone))).optJSONArray("users")?.length() ?: 0

    @Before fun setUp() {
        check(BuildConfig.FIREBASE_MODE == "LOCAL")
        val options = FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:phonelink")
            .setApiKey("demo-api-key").build()
        app = FirebaseApp.initializeApp(context, options, prefix)!!
        auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099); firebaseAuthSettings.setAppVerificationDisabledForTesting(true) }
        val db = FirebaseFirestore.getInstance(app).apply {
            useEmulator(host, 8089); firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
        val notifications = (context.applicationContext as ChaskiApplication).notifications.get()
        repository = FirebaseAuthRepository(auth, SessionDataStore(context), FirebaseUserProfileStore(auth, db), notifications,
            SignOutMarkerStore(PreferencesSignOutMarkerStorage(context)))
    }
    @After fun tearDown() { auth.signOut(); app.delete() }

    @Test fun signedInEmailAccountLinksPhoneToTheSameUid() = runBlocking {
        val email = "$prefix-link@example.test"
        val created = repository.signUpWithEmail(email, "emulator-password", "Cliente ficticio")
        assertTrue(created.toString(), created is ApiResult.Success)
        val uid = auth.currentUser!!.uid
        val phone = phone(); val (verificationId, code) = requestedThroughRepository(phone)
        val linked = repository.verifyPhoneCode(verificationId, code)
        assertTrue(linked.toString(), linked is ApiResult.Success)
        val user = auth.currentUser!!; user.reload().await()
        assertEquals(uid, user.uid); assertEquals(email, user.email); assertEquals(phone, user.phoneNumber)
        assertEquals(listOf("password", "phone").sorted(), user.providerData.map { it.providerId }.filter { it != "firebase" }.sorted())
        assertEquals(1, accountsWithPhone(phone))
    }

    @Test fun wrongCodeKeepsTheEmailAccountWithoutPhone() = runBlocking {
        val email = "$prefix-wrong@example.test"
        assertTrue(repository.signUpWithEmail(email, "emulator-password", "Cliente ficticio") is ApiResult.Success)
        val uid = auth.currentUser!!.uid
        val phone = phone(); val (verificationId, code) = requestedThroughRepository(phone)
        val wrong = (code.toInt() + 1).mod(1_000_000).toString().padStart(6, '0')
        assertTrue(repository.verifyPhoneCode(verificationId, wrong) is ApiResult.Failure)
        val user = auth.currentUser!!; user.reload().await()
        assertEquals(uid, user.uid); assertNull(user.phoneNumber); assertEquals(0, accountsWithPhone(phone))
        // A wrong attempt keeps the same request usable by its own UID.
        assertTrue(repository.verifyPhoneCode(verificationId, code) is ApiResult.Success)
    }

    @Test fun codeRequestedByOneUidCannotBeLinkedAfterLogoutAndReloginAsAnother() = runBlocking {
        val aEmail = "$prefix-a@example.test"; val bEmail = "$prefix-b@example.test"
        assertTrue(repository.signUpWithEmail(bEmail, "emulator-password", "B") is ApiResult.Success)
        auth.signOut()
        assertTrue(repository.signUpWithEmail(aEmail, "emulator-password", "A") is ApiResult.Success)
        val phone = phone(); val (verificationId, code) = requestedThroughRepository(phone)
        auth.signOut(); auth.signInWithEmailAndPassword(bEmail, "emulator-password").await()
        val late = repository.verifyPhoneCode(verificationId, code)
        assertEquals(FirebaseAuthRepository.PHONE_SESSION_CHANGED, (late as ApiResult.Failure).message)
        auth.currentUser!!.reload().await(); assertNull(auth.currentUser!!.phoneNumber); assertEquals(0, accountsWithPhone(phone))
    }

    @Test fun unknownOrRestoredVerificationIdRequiresANewCode() = runBlocking {
        assertTrue(repository.signUpWithEmail("$prefix-restored@example.test", "emulator-password", "R") is ApiResult.Success)
        val phone = phone(); val (verificationId, code) = verification(phone) // not requested by this process (e.g. after process death)
        val result = repository.verifyPhoneCode(verificationId, code)
        assertEquals(FirebaseAuthRepository.PHONE_SESSION_CHANGED, (result as ApiResult.Failure).message)
        assertEquals(0, accountsWithPhone(phone))
    }

    @Test fun manualCodeWithoutSessionNeverCreatesAPhoneOnlyAccount() = runBlocking {
        auth.signOut()
        val phone = phone(); val (verificationId, code) = verification(phone)
        val result = repository.verifyPhoneCode(verificationId, code)
        assertTrue(result is ApiResult.Failure)
        assertEquals(FirebaseAuthRepository.PHONE_SIGN_IN_REQUIRED, (result as ApiResult.Failure).message)
        assertNull(auth.currentUser); assertEquals(0, accountsWithPhone(phone))
    }

    @Test fun smsRequestWithoutSessionFailsBeforeVerificationSoAutoVerifyCannotSignIn() {
        auth.signOut()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val result = runBlocking { repository.sendPhoneVerificationCode(activity!!, phone()) }
            assertTrue(result is ApiResult.Failure)
            assertEquals(FirebaseAuthRepository.PHONE_SIGN_IN_REQUIRED, (result as ApiResult.Failure).message)
            assertNull(auth.currentUser)
        }
    }
}
