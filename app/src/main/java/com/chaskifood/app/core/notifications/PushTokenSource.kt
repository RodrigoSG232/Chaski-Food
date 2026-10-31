package com.chaskifood.app.core.notifications

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Real FCM token boundary. Firebase Messaging 24.1.0 (BOM 33.10.0) exposes getToken/deleteToken;
 * the token is the same opaque string the device registry already stores. LOCAL never calls it. */
internal interface PushTokenSource {
    suspend fun token(): String
    suspend fun delete()
}

internal class FirebasePushTokenSource @Inject constructor() : PushTokenSource {
    override suspend fun token(): String = FirebaseMessaging.getInstance().token.await()
    override suspend fun delete() { FirebaseMessaging.getInstance().deleteToken().await() }
}
