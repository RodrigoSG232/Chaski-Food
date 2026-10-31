package com.chaskifood.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaskifood.app.core.navigation.ChaskiNavHost
import com.chaskifood.app.ui.theme.ChaskiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var notifications:com.chaskifood.app.core.notifications.NotificationCoordinator
    override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);notifications.open(intent)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notifications.open(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        setContent {
            ChaskiTheme {
                ChaskiNavHost()
            }
        }
    }
}