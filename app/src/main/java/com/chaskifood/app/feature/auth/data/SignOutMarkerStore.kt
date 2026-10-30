package com.chaskifood.app.feature.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Synchronous storage for the sign-out marker; `write` returns true only once the value is on disk. */
internal interface SignOutMarkerStorage {
    fun read(): String?
    fun write(uid: String?): Boolean
}

internal class PreferencesSignOutMarkerStorage @Inject constructor(@ApplicationContext context: Context) : SignOutMarkerStorage {
    private val preferences = context.getSharedPreferences("auth_sign_out_marker", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString(KEY, null)
    // commit(), not apply(): the marker must be durable before FirebaseAuth.signOut starts its own asynchronous write.
    override fun write(uid: String?): Boolean =
        (if (uid == null) preferences.edit().remove(KEY) else preferences.edit().putString(KEY, uid)).commit()
    private companion object { const val KEY = "signed_out_uid" }
}

/** Firebase Auth persists sign-out asynchronously: if the process dies right after signOut, the next process can
 * restore the previous user. The marker is written before signing out and enforced at startup. It can only close a
 * session, never grant one, and it is cleared only after an explicit sign-in succeeds. */
@Singleton
class SignOutMarkerStore @Inject internal constructor(private val storage: SignOutMarkerStorage) {
    /** Best effort: a failed write never blocks the sign-out itself. */
    fun markSigningOut(uid: String) { storage.write(uid) }

    /** Only after a successful sign-in: a failed attempt keeps the marker, so a previous user the SDK restores is
     * still closed at startup. A failed clear can only sign the new session out at next start (fail closed). */
    fun clearAfterSignIn() { if (storage.read() != null) storage.write(null) }

    /** True when the restored user is the one that explicitly signed out and the SDK did not persist it. */
    fun mustCompleteSignOut(currentUid: String?): Boolean = currentUid != null && currentUid == storage.read()
}
