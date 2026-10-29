package com.chaskifood.app.core.navigation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.MainActivity
import com.chaskifood.app.core.access.data.SharedAccessContext
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Prueba del grafo registrado y los ViewModels Hilt reales; nunca usa servicios Cloud. */
class AccessHostEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val project = "demo-chaski-hu15"
    private val password = "fictional-local-password"
    private val prefix = "nav34-${UUID.randomUUID()}"
    private lateinit var nav: NavHostController
    private fun request(port: Int, path: String, body: JSONObject, authorization: String = "owner"): JSONObject {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $authorization")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            check(connection.responseCode == 200) { "Fallo en fixture local" }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    private fun account(email: String): String {
        val result = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true))
        val uid = result.getString("localId")
        request(9099, "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update",
            JSONObject().put("localId", uid).put("emailVerified", true))
        return uid
    }
    private fun seed(path: String, values: Map<String, String>) {
        val fields = JSONObject()
        values.forEach { (key, value) -> fields.put(key, JSONObject().put("stringValue", value)) }
        request(8089, "/v1/projects/$project/databases/(default)/documents:commit", JSONObject().put("writes",
            org.json.JSONArray().put(JSONObject().put("update", JSONObject()
                .put("name", "projects/$project/databases/(default)/documents/$path").put("fields", fields)))))
    }
    @Test fun grafoRealInyectaEditoresYBloqueaComprasDePropietario() = runBlocking {
        check(BuildConfig.FIREBASE_MODE == "LOCAL" && BuildConfig.EMULATOR_HOST == "127.0.0.1")
        check(FirebaseApp.getInstance().options.projectId == project)
        val auth = FirebaseAuth.getInstance()
        val email = "$prefix-owner@example.test"
        val uid = account(email)
        val adminEmail = "$prefix-admin@example.test"
        val adminUid = account(adminEmail)
        request(9099, "/identitytoolkit.googleapis.com/v1/projects/$project/accounts:update",
            JSONObject().put("localId", adminUid).put("customAttributes", "{\"admin\":true}"))
        val adminToken = request(9099, "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key",
            JSONObject().put("email", adminEmail).put("password", password).put("returnSecureToken", true)).getString("idToken")
        request(5001, "/$project/southamerica-west1/foodApi/api/v1/access/operations",
            JSONObject().put("action", "PREPARE").put("operationId", UUID.randomUUID().toString())
                .put("expectedRevision", 0).put("email", email), adminToken)
        val business = mapOf("ownerUid" to uid, "businessName" to "Negocio ficticio", "status" to "APPROVED")
        seed("business_requests/$uid", business)
        seed("stores/$uid-one", mapOf("businessId" to uid, "name" to "Local ficticio", "status" to "ACTIVE", "operationalStatus" to "CLOSED"))
        lateinit var access: AccessNavigationViewModel
        compose.runOnUiThread { access = androidx.lifecycle.ViewModelProvider(compose.activity)[AccessNavigationViewModel::class.java] }
        try {
            auth.signInWithEmailAndPassword(email, password).await()
            try {
                withTimeout(25_000) { access.states.first { (it.resolution as? AccessResolution.Resolved)?.experience == CommercialExperience.OWNER } }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Inicio propietario: ${access.current().phase}/${access.current().problem}", timeout)
            }
            compose.runOnUiThread {
                compose.activity.setContent {
                    nav = rememberNavController()
                    ChaskiTheme { ChaskiNavHost(nav, ChaskiDestinations.MAIN) }
                }
            }
            compose.waitForIdle()
            compose.runOnIdle { nav.navigate(ChaskiDestinations.STORE_OPERATIONS) }
            compose.onNodeWithText("Panel Operativo de Locales").assertIsDisplayed()
            compose.runOnIdle { nav.navigate(ChaskiDestinations.STORE_PRODUCT_CONFIGURATION) }
            compose.waitForIdle()
            compose.waitUntil(25_000) { compose.onAllNodesWithText("Productos por local").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Productos por local").assertIsDisplayed()
            compose.runOnIdle { nav.navigate(ChaskiDestinations.CART) }
            compose.waitForIdle()
            compose.waitUntil(25_000) { nav.currentDestination?.route == ChaskiDestinations.MAIN }
            compose.runOnIdle { assertEquals(ChaskiDestinations.MAIN, nav.currentDestination?.route) }
            seed("business_requests/$uid", business + ("status" to "SUSPENDED"))
            access.refresh()
            try {
                withTimeout(25_000) { access.states.first { (it.resolution as? AccessResolution.Resolved)?.ownership?.status == AccessBusinessStatus.SUSPENDED } }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Actualización suspensión: ${access.current().phase}/${access.current().problem}", timeout)
            }
            compose.runOnIdle { nav.navigate(ChaskiDestinations.MANAGE_STORES) }
            compose.waitForIdle()
            compose.waitUntil(25_000) { nav.currentDestination?.route == ChaskiDestinations.MAIN }
            compose.runOnIdle { assertEquals(ChaskiDestinations.MAIN, nav.currentDestination?.route) }
            val buyerEmail = "$prefix-buyer@example.test"
            account(buyerEmail)
            request(5001, "/$project/southamerica-west1/foodApi/api/v1/access/operations",
                JSONObject().put("action", "PREPARE").put("operationId", UUID.randomUUID().toString())
                    .put("expectedRevision", 0).put("email", buyerEmail), adminToken)
            auth.signOut()
            withTimeout(25_000) { access.states.first { it.phase == com.chaskifood.app.core.access.data.AccessContextPhase.SIGNED_OUT } }
            auth.signInWithEmailAndPassword(buyerEmail, password).await()
            withTimeout(25_000) { access.states.first { (it.resolution as? AccessResolution.Resolved)?.experience == CommercialExperience.CLIENT } }
            compose.runOnIdle { nav.navigate(ChaskiDestinations.MAIN) }
            compose.waitForIdle()
            lateinit var originalCart: com.chaskifood.app.feature.cart.presentation.CartViewModel
            lateinit var originalAddress: com.chaskifood.app.feature.address.presentation.AddressViewModel
            compose.runOnIdle {
                val main = nav.getBackStackEntry(ChaskiDestinations.MAIN)
                originalCart = androidx.lifecycle.ViewModelProvider(main)[com.chaskifood.app.feature.cart.presentation.CartViewModel::class.java]
                originalAddress = androidx.lifecycle.ViewModelProvider(main)[com.chaskifood.app.feature.address.presentation.AddressViewModel::class.java]
                nav.navigate(ChaskiDestinations.CHECKOUT)
            }
            compose.waitUntil(25_000) { compose.onAllNodesWithText("Revisar compra").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Revisar compra").assertIsDisplayed()
            compose.runOnIdle { nav.navigate(ChaskiDestinations.ADDRESS_BOOK) }
            compose.waitForIdle()
            compose.runOnIdle {
                val main = nav.getBackStackEntry(ChaskiDestinations.MAIN)
                assertSame(originalCart, androidx.lifecycle.ViewModelProvider(main)[com.chaskifood.app.feature.cart.presentation.CartViewModel::class.java])
                assertSame(originalAddress, androidx.lifecycle.ViewModelProvider(main)[com.chaskifood.app.feature.address.presentation.AddressViewModel::class.java])
                assertEquals(ChaskiDestinations.ADDRESS_BOOK, nav.currentDestination?.route)
            }
        } finally { auth.signOut() }
    }
}
