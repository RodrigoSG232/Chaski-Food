package com.chaskifood.app

import android.app.Application
import com.chaskifood.app.core.firebase.FirebaseRuntime
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChaskiApplication : Application() {
    @javax.inject.Inject lateinit var notifications:javax.inject.Provider<com.chaskifood.app.core.notifications.NotificationCoordinator>
    @javax.inject.Inject lateinit var signOutMarker:javax.inject.Provider<com.chaskifood.app.feature.auth.data.SignOutMarkerStore>
    override fun onCreate() {
        super.onCreate()
        FirebaseRuntime.configure()
        // Before any session consumer or FCM service runs: finish a sign-out the SDK did not persist.
        val auth=FirebaseAuth.getInstance()
        if(signOutMarker.get().mustCompleteSignOut(auth.currentUser?.uid))auth.signOut()
        notifications.get().start()
    }
}
