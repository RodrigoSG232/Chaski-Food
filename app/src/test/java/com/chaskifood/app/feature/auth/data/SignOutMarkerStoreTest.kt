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
