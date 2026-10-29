package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.cart.data.*
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal data class CheckoutBuyerSnapshot(val name: String, val phone: String?)
/** La identidad visible ya procede de Auth. No abre el perfil Firestore privado al cliente.
 * foodApi compara nuevamente contra su fuente autoritativa y rechaza divergencias de consentimiento. */
internal class CheckoutBuyerSnapshotSource @Inject constructor(
    private val auth: FirebaseAuth, private val sessions: CartSessionSource,
) {
    suspend fun read(uid: String, generation: Long): CheckoutBuyerSnapshot {
        val session = CartSession(uid, generation)
        require(sessions.isCurrent(session))
        val user = requireNotNull(auth.currentUser?.takeIf { it.uid == uid })
        check(withTimeoutOrNull(10_000) { user.reload().await(); true } == true)
        require(sessions.isCurrent(session))
        val name = user.displayName?.takeIf { it.isNotBlank() }
        return CheckoutBuyerSnapshot(requireNotNull(name), user.phoneNumber)
    }
}
