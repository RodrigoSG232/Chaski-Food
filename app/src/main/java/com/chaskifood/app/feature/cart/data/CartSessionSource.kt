package com.chaskifood.app.feature.cart.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

internal data class CartSession(val uid: String?, val generation: Long)

internal interface CartSessionSource {
    val sessions: StateFlow<CartSession>
    fun current(): CartSession
    fun isCurrent(session: CartSession): Boolean = current() == session
}

/** La identidad del objeto permite invalidar también una nueva sesión con el mismo UID. */
internal class CartSessionTracker(uid: String?, identity: Any?) {
    private val state = MutableStateFlow(CartSession(uid, 0))
    private var lastIdentity: Any? = identity

    val sessions: StateFlow<CartSession> = state

    @Synchronized
    fun update(uid: String?, identity: Any?): CartSession {
        val previous = state.value
        if (uid != previous.uid || identity !== lastIdentity) {
            lastIdentity = identity
            state.value = CartSession(uid, previous.generation + 1)
        }
        return state.value
    }
}

/** Usa la instancia FirebaseAuth compartida; no consulta el UID guardado en DataStore. */
@Singleton
internal class FirebaseCartSessionSource @Inject constructor(
    private val auth: FirebaseAuth,
) : CartSessionSource {
    private val tracker = auth.currentUser.let { CartSessionTracker(it?.uid, it) }
    override val sessions: StateFlow<CartSession> = tracker.sessions

    private val listener = FirebaseAuth.AuthStateListener { current() }

    init {
        auth.addAuthStateListener(listener)
    }

    override fun current(): CartSession = auth.currentUser.let { tracker.update(it?.uid, it) }
}
