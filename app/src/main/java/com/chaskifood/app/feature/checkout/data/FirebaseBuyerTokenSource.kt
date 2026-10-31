package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.core.firebase.FoodApiEnvironment
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

internal interface BuyerTokenSource {
    suspend fun token(uid: String, generation: Long): String
    fun isCurrent(uid: String, generation: Long): Boolean
}

internal class BuyerSessionChanged : Exception()
internal class BuyerAuthenticationRequired : Exception()
internal class BuyerBackendNotConfigured : Exception()

internal class FirebaseBuyerTokenSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val sessions: CartSessionSource,
) : BuyerTokenSource {
    override fun isCurrent(uid: String, generation: Long) = sessions.isCurrent(CartSession(uid, generation))

    override suspend fun token(uid: String, generation: Long): String {
        if (!FoodApiEnvironment.isConfigured(auth.app.options.projectId)) throw BuyerBackendNotConfigured()
        if (!isCurrent(uid, generation)) throw BuyerSessionChanged()
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: throw BuyerAuthenticationRequired()
        val token = try {
            user.getIdToken(false).await().token ?: throw BuyerAuthenticationRequired()
        } catch (_: FirebaseAuthInvalidUserException) {
            throw BuyerAuthenticationRequired()
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            throw BuyerAuthenticationRequired()
        }
        if (!isCurrent(uid, generation)) throw BuyerSessionChanged()
        return token
    }
}
