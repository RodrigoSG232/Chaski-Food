package com.chaskifood.app.feature.splash.presentation

import androidx.lifecycle.ViewModel
import com.chaskifood.app.core.datastore.SessionDataStore
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionDataStore: SessionDataStore,
) : ViewModel() {

    suspend fun isUserLoggedIn(): Boolean {
        val firebaseUser = firebaseAuth.currentUser
        val storedUserId = sessionDataStore.userId.firstOrNull()
        return (firebaseUser != null) && (!storedUserId.isNullOrBlank())
    }
}
