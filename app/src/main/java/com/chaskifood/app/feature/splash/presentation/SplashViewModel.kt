package com.chaskifood.app.feature.splash.presentation

import androidx.lifecycle.ViewModel
import com.chaskifood.app.core.datastore.SessionDataStore
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionDataStore: SessionDataStore,
) : ViewModel() {

    suspend fun isUserLoggedIn(): Boolean {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser == null) {
            sessionDataStore.clearSession()
            return false
        }
        val storedUserId = sessionDataStore.userId.firstOrNull()
        if (storedUserId != firebaseUser.uid) {
            try {
                val token = firebaseUser.getIdToken(false).await().token ?: return false
                if (firebaseAuth.currentUser?.uid != firebaseUser.uid) return false
                sessionDataStore.setSession(firebaseUser.uid, token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return false }
        }
        return !firebaseUser.phoneNumber.isNullOrBlank()
    }
}
