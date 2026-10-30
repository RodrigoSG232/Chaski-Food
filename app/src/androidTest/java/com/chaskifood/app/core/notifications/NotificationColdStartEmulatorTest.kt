package com.chaskifood.app.core.notifications

import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.ChaskiApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real process recreation, driven by the host between separate `am instrument` runs: each phase starts in a new
 * process, and the host also kills the backgrounded app (`am kill`, never force-stop) in between. Only the Auth
 * Emulator is reachable, so the access facts stay unresolved as in a push-started cold process. Never FCM delivery. */
class NotificationColdStartEmulatorTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private fun phase(name: String) = assumeTrue(arguments.getString("hu16ColdStartPhase") == name)
    private val email get() = requireNotNull(arguments.getString("hu16ColdStartEmail"))
    private fun data(recipient: String) = mapOf("type" to "NEW_ORDER", "eventId" to "new_order_${"c".repeat(64)}", "orderId" to "c".repeat(64),
        "businessId" to "cold-business", "storeId" to "cold-store", "recipientUid" to recipient)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun coordinator() = (context.applicationContext as ChaskiApplication).notifications.get()

    @Test fun prepare() = runBlocking {
        phase("prepare"); check(BuildConfig.FIREBASE_MODE == "LOCAL" && BuildConfig.EMULATOR_HOST == "127.0.0.1")
        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email, "emulator-password").await()
        assertEquals(email, auth.currentUser?.email)
        // am instrument kills the process as soon as the test returns; give the SDK's asynchronous persistence time to land.
        kotlinx.coroutines.delay(3_000)
    }

    @Test fun restoredIdentityGatesTheHintWithoutNetwork() {
        phase("verify"); check(BuildConfig.FIREBASE_MODE == "LOCAL")
        // Read synchronously before any listener or network call: this is what onMessageReceived sees.
        val restored = FirebaseAuth.getInstance().currentUser
        assertEquals("Persisted identity must be restored in the recreated process", email, restored?.email)
        val uid = requireNotNull(restored).uid
        // Synthetic Emulator UID handed to the host for the signed-out phase; also proves a new process per phase.
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("hu16ColdStartUid", uid); putInt("hu16ColdStartPid", android.os.Process.myPid()) })
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val notifications = coordinator()
        assertFalse("A foreign recipient is dropped", notifications.receive(data("foreign-recipient")))
        val shown = notifications.receive(data(uid))
        if (Build.VERSION.SDK_INT >= 33) assertTrue("POST_NOTIFICATIONS was granted by the host", shown)
        assertEquals(1, manager.activeNotifications.count { it.tag == "new_order_${"c".repeat(64)}" })
        assertFalse("Visual deduplication in the same process", notifications.receive(data(uid)))
        manager.cancelAll()
    }

    @Test fun signOut() = runBlocking {
        phase("signout")
        coordinator().beforeSignOut(); FirebaseAuth.getInstance().signOut()
        assertNull(FirebaseAuth.getInstance().currentUser)
        // Observed: if the process dies within milliseconds of signOut, the SDK may restore the previous user.
        kotlinx.coroutines.delay(3_000)
    }

    @Test fun signedOutRecreatedProcessShowsNothing() {
        phase("verifySignedOut")
        assertNull(FirebaseAuth.getInstance().currentUser)
        val previous = requireNotNull(arguments.getString("hu16ColdStartUid"))
        assertFalse(coordinator().receive(data(previous)))
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }
}
