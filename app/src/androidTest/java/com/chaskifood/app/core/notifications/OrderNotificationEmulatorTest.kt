package com.chaskifood.app.core.notifications

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.chaskifood.app.MainActivity
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.feature.cart.data.FirebaseCartSessionSource
import com.chaskifood.app.feature.checkout.data.FirebaseBuyerTokenSource
import com.chaskifood.app.feature.orders.data.StoreOrdersApi
import com.google.firebase.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** LOCAL HTTP/Auth authorization with a synthetic incoming message; never FCM delivery. */
class OrderNotificationEmulatorTest {
    @Test fun syntheticNoticeChecksHu34PermissionSessionDedupAndSafeOpen()=runBlocking {
        require(BuildConfig.FIREBASE_MODE=="LOCAL")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val android=instrumentation.targetContext
        val app=requireNotNull(FirebaseApp.initializeApp(android,FirebaseOptions.Builder().setProjectId("demo-chaski-hu15")
            .setApplicationId("1:123456789:android:hu16notices").setApiKey("demo-api-key").build(),"hu16-notice-e2e"))
        val auth=FirebaseAuth.getInstance(app).apply {useEmulator("127.0.0.1",9099)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val manager=android.getSystemService(NotificationManager::class.java)
        fun permission(grant:Boolean){
            if(Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.executeShellCommand(
                "pm ${if(grant)"grant" else "revoke"} ${android.packageName} android.permission.POST_NOTIFICATIONS").use {descriptor->
                    java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use {it.readText()}}
        }
        fun labels():List<String> {
            val values=mutableListOf<String>()
            fun collect(node:android.view.accessibility.AccessibilityNodeInfo?){
                if(node==null)return
                node.text?.let {values+=it.toString()};node.contentDescription?.let {values+=it.toString()}
                repeat(node.childCount){collect(node.getChild(it))}
            }
            collect(instrumentation.uiAutomation.rootInActiveWindow)
            return values
        }
        try {
            auth.signInWithEmailAndPassword("hu16-e2e-manager@example.test","emulator-password").await()
            val sessions=FirebaseCartSessionSource(auth)
            val retrofit=Retrofit.Builder().baseUrl("http://127.0.0.1:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/")
                .client(OkHttpClient()).addConverterFactory(Json{ignoreUnknownKeys=true}.asConverterFactory("application/json".toMediaType())).build()
            val driver=FirebaseAccessIdentityDriver(auth)
            val identity=SharedAccessIdentitySource(driver,scope)
            val shared=SharedAccessContext(identity,RemoteAccessFacts(retrofit.create(AccessFactsApi::class.java),driver,Json{ignoreUnknownKeys=true},true),scope,true)
            withTimeout(15000){shared.states.first {it.phase==AccessContextPhase.RESOLVED}}
            val tokens=FirebaseBuyerTokenSource(auth,sessions)
            val current=sessions.current()
            val order=retrofit.create(StoreOrdersApi::class.java).page("hu16-e2e-store","Bearer ${tokens.token(current.uid!!,current.generation)}",null).body()!!.orders.single()
            val expected=mapOf("type" to "NEW_ORDER","eventId" to "new_order_${order.orderId}","orderId" to order.orderId,
                "businessId" to "hu16-e2e-owner","storeId" to "hu16-e2e-store","recipientUid" to "hu16-e2e-manager")
            val encoded=InstrumentationRegistry.getArguments().getString("hu16NoticePayload")
            val data=if(encoded==null)expected else {
                val raw=String(Base64.decode(encoded,Base64.URL_SAFE or Base64.NO_WRAP),Charsets.UTF_8)
                val decoded=kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
                decoded.mapValues{(_,value)->(value as kotlinx.serialization.json.JsonPrimitive).content}.also {assertEquals(expected,it)}
            }
            val coordinator=NotificationCoordinator(android,sessions,tokens,shared,scope,retrofit)
            coordinator.start()
            if(Build.VERSION.SDK_INT>=33)assertEquals(PackageManager.PERMISSION_DENIED,
                androidx.core.content.ContextCompat.checkSelfPermission(android,Manifest.permission.POST_NOTIFICATIONS))
            coordinator.receive(data);delay(700)
            assertTrue(manager.activeNotifications.none {it.tag==data["eventId"]})
            permission(true);coordinator.receive(data)
            withTimeout(15000){while(manager.activeNotifications.none {it.tag==data["eventId"]})delay(100)}
            coordinator.receive(data);delay(700)
            assertEquals(1,manager.activeNotifications.count {it.tag==data["eventId"]})
            val notification=manager.activeNotifications.single {it.tag==data["eventId"]}.notification
            assertEquals("Consulta la bandeja del establecimiento.",notification.extras.getCharSequence("android.text").toString())
            val intent=Intent().setAction("NEW_ORDER");data.filterKeys {it!="type"}.forEach{(key,value)->intent.putExtra(key,value)}
            withTimeout(15000){shared.states.first {it.phase==AccessContextPhase.RESOLVED}}
            coordinator.open(intent);assertNotNull(coordinator.takeAuthorized());assertNull(coordinator.takeAuthorized())
            coordinator.open(Intent(intent).putExtra("recipientUid","foreign"));assertNull(coordinator.pending.value)
            coordinator.open(intent);assertNotNull(coordinator.pending.value)
            coordinator.beforeSignOut();auth.signOut()
            auth.signInWithEmailAndPassword("hu16-e2e-manager@example.test","emulator-password").await()
            assertNull(coordinator.takeAuthorized()) // Same UID does not revive an earlier session's launch.
            assertTrue(manager.activeNotifications.none {it.tag==data["eventId"]})
            // Exercise the real Activity/NavHost and its HTTP inbox, not only the launch-policy helper.
            val primary=FirebaseAuth.getInstance()
            try {
                primary.signInWithEmailAndPassword("hu16-e2e-manager@example.test","emulator-password").await()
                val launch=Intent(android,MainActivity::class.java).setAction("NEW_ORDER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data.filterKeys {it!="type"}.forEach{(key,value)->launch.putExtra(key,value)}
                ActivityScenario.launch<MainActivity>(launch).use {scenario->
                    val found=withTimeoutOrNull(20000){
                        while(labels().none {it.contains("Plato ficticio")})delay(200)
                        true
                    }?:false
                    if(!found){
                        val texts=labels()
                        var diagnostic=""
                        scenario.onActivity {
                            val state=androidx.lifecycle.ViewModelProvider(it)[com.chaskifood.app.core.navigation.AccessNavigationViewModel::class.java].current()
                            diagnostic="pending=${it.notifications.pending.value!=null},phase=${state.phase},problem=${state.problem},allowed=${it.notifications.allowed(NewOrderNotice.parse(data)!!)}"
                        }
                        fail("Synthetic LOCAL inbox not visible; $diagnostic; labels=${texts.joinToString()}")
                    }
                    assertTrue(labels().any {it.contains("Pedidos pendientes")})
                }
            } finally {primary.signOut()}
        } finally {scope.cancel();manager.cancelAll();auth.signOut();app.delete()}
    }
}
