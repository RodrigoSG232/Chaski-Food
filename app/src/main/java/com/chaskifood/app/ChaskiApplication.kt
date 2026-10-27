package com.chaskifood.app

import android.app.Application
import com.chaskifood.app.core.firebase.FirebaseRuntime
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChaskiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseRuntime.configure()
    }
}
