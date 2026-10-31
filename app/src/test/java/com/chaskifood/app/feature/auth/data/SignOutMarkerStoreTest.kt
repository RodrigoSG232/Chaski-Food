package com.chaskifood.app.feature.auth.data

import org.junit.Assert.*
import org.junit.Test

class SignOutMarkerStoreTest {
    private class Storage(var value: String? = null, var writable: Boolean = true) : SignOutMarkerStorage {
        var writes = 0
        override fun read() = value
        override fun write(uid: String?): Boolean { writes++; if (writable) value = uid; return writable }
    }

    @Test fun restoredUserThatSignedOutMustBeSignedOutAgain() {
        val storage = Storage(); val marker = SignOutMarkerStore(storage)
        marker.markSigningOut("manager")
        assertTrue(marker.mustCompleteSignOut("manager"))
        assertFalse(marker.mustCompleteSignOut("other"))
        assertFalse(marker.mustCompleteSignOut(null))
        // Startup never clears it: a second interrupted completion is enforced again.
        assertTrue(marker.mustCompleteSignOut("manager"))
    }

    @Test fun failedSignInAttemptKeepsTheMarkerSoTheOldUserCannotBeRestored() {
        // The repository clears only after a successful sign-in; a failed attempt never calls clearAfterSignIn.
        val marker = SignOutMarkerStore(Storage("manager"))
        assertTrue("Old user restored by the SDK after a failed login is closed again", marker.mustCompleteSignOut("manager"))
    }

    @Test fun successfulSignInClearsTheMarkerAndRepeatsAreFree() {
        val storage = Storage("manager"); val marker = SignOutMarkerStore(storage)
        marker.clearAfterSignIn()
        assertFalse(marker.mustCompleteSignOut("manager"))
        val writes = storage.writes
        marker.clearAfterSignIn(); assertEquals("No write when already clear", writes, storage.writes)
    }

    /** Fails the first [failures] writes, then behaves normally. */
    private class Flaky(private var failures: Int, private val throws: Boolean = false) : SignOutMarkerStorage {
        var value: String? = null; var writes = 0
        override fun read() = value
        override fun write(uid: String?): Boolean {
            writes++
            if (failures > 0) { failures--; if (throws) throw java.io.IOException("disk"); return false }
            value = uid; return true
        }
    }

    @Test fun successfulMarkIsConfirmedOnFirstAttempt() {
        val storage = Flaky(0)
        assertTrue(SignOutMarkerStore(storage).markSigningOut("manager"))
        assertEquals(1, storage.writes); assertEquals("manager", storage.value)
    }

    @Test fun recoverableFailureIsRetriedWithinTheBound() {
        val storage = Flaky(2)
        assertTrue(SignOutMarkerStore(storage).markSigningOut("manager"))
        assertEquals(3, storage.writes); assertTrue(SignOutMarkerStore(storage).mustCompleteSignOut("manager"))
        val throwing = Flaky(1, throws = true)
        assertTrue("A throwing storage is treated as a failed attempt", SignOutMarkerStore(throwing).markSigningOut("manager"))
    }

    @Test fun persistentFailureIsReportedAfterExactlyTheBoundedAttempts() {
        val storage = Flaky(Int.MAX_VALUE)
        assertFalse(SignOutMarkerStore(storage).markSigningOut("manager"))
        assertEquals(SignOutMarkerStore.MARK_ATTEMPTS, storage.writes)
        assertFalse("Nothing protects an interrupted sign-out in this case", SignOutMarkerStore(storage).mustCompleteSignOut("manager"))
        val lying = object : SignOutMarkerStorage { override fun read(): String? = null; override fun write(uid: String?) = true }
        assertFalse("A write that cannot be read back is not trusted", SignOutMarkerStore(lying).markSigningOut("manager"))
    }

    @Test fun interruptedSignOutWithAPersistedMarkerIsCompletedAtStartup() {
        val storage = Storage(); SignOutMarkerStore(storage).markSigningOut("manager")
        // New process: a fresh store over the same persisted storage sees the restored user.
        val restarted = SignOutMarkerStore(storage)
        assertTrue(restarted.mustCompleteSignOut("manager")); assertFalse(restarted.mustCompleteSignOut("other"))
    }

    @Test fun storageFailuresOnlyEverFailClosed() {
        // A failed clear keeps the marker: at worst the same UID is signed out at next start.
        val stale = Storage("manager", writable = false); val marker = SignOutMarkerStore(stale)
        marker.clearAfterSignIn()
        assertTrue(marker.mustCompleteSignOut("manager"))
        // A failed mark never blocks sign-out and never grants anything.
        val unwritable = Storage(writable = false)
        SignOutMarkerStore(unwritable).markSigningOut("manager")
        assertNull(unwritable.value)
    }
}
