package com.chaskifood.app.feature.checkout.data

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

internal class FirebaseBuyerTokenSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val sessions: CartSessionSource,
) : BuyerTokenSource {
    override fun isCurrent(uid: String, generation: Long) = sessions.isCurrent(CartSession(uid, generation))

    override suspend fun token(uid: String, generation: Long): String {
        // La API de esta entrega es exclusivamente local: jamás envía un token de producción.
        check(auth.app.options.projectId == "demo-chaski-hu15") { "Backend local no configurado para este proyecto." }
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
