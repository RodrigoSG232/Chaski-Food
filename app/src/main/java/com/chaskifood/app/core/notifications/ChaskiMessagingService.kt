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
        // FCM invokes this on its worker thread. Only local synchronous checks run here; a dropped or
        // undelivered hint never affects the order, which the HTTP inbox keeps authoritative.
        coordinator.receive(message.data)
    }
}
