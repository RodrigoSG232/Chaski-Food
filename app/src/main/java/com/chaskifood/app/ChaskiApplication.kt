package com.chaskifood.app

import android.app.Application
import com.chaskifood.app.core.firebase.FirebaseRuntime
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChaskiApplication : Application() {
    @javax.inject.Inject lateinit var notifications:javax.inject.Provider<com.chaskifood.app.core.notifications.NotificationCoordinator>
    override fun onCreate() {
        super.onCreate()
        FirebaseRuntime.configure()
        notifications.get().start()
    }
}
