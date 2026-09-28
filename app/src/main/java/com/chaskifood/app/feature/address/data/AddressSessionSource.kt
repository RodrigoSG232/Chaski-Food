package com.chaskifood.app.feature.address.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AddressSession(val uid: String?, val generation: Long)

interface AddressSessionSource {
    val sessions: StateFlow<AddressSession>
    fun current(): AddressSession
    fun isCurrent(session: AddressSession): Boolean = current() == session
}

/** Listener único, con vida de aplicación; no depende del ID guardado en preferencias. */
@Singleton
class FirebaseAddressSessionSource @Inject constructor(
    private val auth: FirebaseAuth,
) : AddressSessionSource {
    private val state = MutableStateFlow(AddressSession(auth.currentUser?.uid, 0))
    private var lastUser = auth.currentUser
    override val sessions: StateFlow<AddressSession> = state

    private val listener = FirebaseAuth.AuthStateListener { refresh() }

    init {
        auth.addAuthStateListener(listener)
    }

    @Synchronized
    private fun refresh(): AddressSession {
        val user = auth.currentUser
        val uid = user?.uid
        if (uid != state.value.uid || user !== lastUser) {
            state.value = AddressSession(uid, state.value.generation + 1)
            lastUser = user
        }
        return state.value
    }

    override fun current(): AddressSession = refresh()
}
