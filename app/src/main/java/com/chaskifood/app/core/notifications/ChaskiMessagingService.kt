package com.chaskifood.app.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChaskiMessagingService:FirebaseMessagingService(){
    @Inject lateinit var coordinator:NotificationCoordinator
    override fun onNewToken(token:String){coordinator.tokenChanged(token)}
    override fun onMessageReceived(message:RemoteMessage){
        // FCM invokes this on its worker thread. Do not leave unbounded async work after it returns.
        // A slow authorization check drops only the hint; the HTTP inbox remains authoritative.
        val work=coordinator.receive(message.data)?:return
        kotlinx.coroutines.runBlocking {
            try {kotlinx.coroutines.withTimeoutOrNull(2500){work.join()}}finally {work.cancel()}
        }
    }
}
