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

    @Test fun explicitSignInClearsTheMarkerBeforeAuthenticatingSameUid() {
        val storage = Storage("manager"); val marker = SignOutMarkerStore(storage)
        assertTrue(marker.clearBeforeSignIn())
        assertFalse(marker.mustCompleteSignOut("manager"))
        val writes = storage.writes
        assertTrue(marker.clearBeforeSignIn()); assertEquals("No write when already clear", writes, storage.writes)
    }

    @Test fun failedClearBlocksSignInAndFailedMarkNeverBlocksSignOut() {
        val stale = SignOutMarkerStore(Storage("manager", writable = false))
        assertFalse(stale.clearBeforeSignIn())
        val unwritable = Storage(writable = false)
        SignOutMarkerStore(unwritable).markSigningOut("manager")
        assertNull(unwritable.value)
    }
}
