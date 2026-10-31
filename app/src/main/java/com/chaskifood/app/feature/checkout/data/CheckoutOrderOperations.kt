package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.cart.data.*
import com.chaskifood.app.feature.checkout.data.local.*
import com.chaskifood.app.feature.checkout.domain.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

/** Coordina las operaciones existentes. Un candado por UID protege también dos ViewModels. */
@Singleton
internal class CheckoutOrderOperations @Inject constructor(
    private val store: CheckoutIntentStore, private val sessions: CartSessionSource,
    private val http: HttpOrderReader, private val access: CheckoutPurchaseGuard,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private suspend fun locked(session: CartSession, action: suspend () -> CheckoutConfirmation): CheckoutConfirmation {
        val uid = session.uid ?: return CheckoutConfirmation.SessionChanged
        if (!sessions.isCurrent(session)) return CheckoutConfirmation.SessionChanged
        val lock = locks.getOrPut(uid) { Mutex() }
        if (!lock.tryLock()) return CheckoutConfirmation.Busy
        return try { action() } finally { lock.unlock() }
    }
    suspend fun confirm(context: CheckoutContext, quote: CheckoutQuote): CheckoutConfirmation {
        val session = CartSession(context.ownerUid, context.sessionGeneration)
        return locked(session) {
            if (!access.allows(context.ownerUid, true)) return@locked CheckoutConfirmation.PurchaseForbidden
            val accepted = quote.acceptedPurchase ?: return@locked CheckoutConfirmation.ReviewRequired
            val prepared = store.prepare(context, quote, accepted, UUID.randomUUID().toString())
            if (prepared is LocalIntentResult.Unresolved) return@locked CheckoutConfirmation.Pending(prepared.intentId)
            if (prepared !is LocalIntentResult.Ready) return@locked map(prepared)
            exchange(session, prepared.stored, true)
        }
    }
    suspend fun pending(session: CartSession, id: String?): CheckoutConfirmation = locked(session) {
        if (!access.allows(session.uid!!, false)) return@locked CheckoutConfirmation.SessionChanged
        val found = if (id == null) store.unresolved(session) else store.recover(session, id)
        if (found is LocalIntentResult.Ready && found.stored.result is OrderAttemptResult.Succeeded)
            map(store.applyRecoveredReceipt(session, found.stored.intent.intentId)) else map(found)
    }
    suspend fun resolve(session: CartSession, id: String, send: Boolean): CheckoutConfirmation = locked(session) {
        if (!access.allows(session.uid!!, send)) return@locked if (send) CheckoutConfirmation.PurchaseForbidden else CheckoutConfirmation.SessionChanged
        val found = store.recover(session, id)
        if (found !is LocalIntentResult.Ready) return@locked map(found)
        if (found.stored.result is OrderAttemptResult.Succeeded)
            return@locked map(store.applyRecoveredReceipt(session, id))
        if (found.stored.result is OrderAttemptResult.RejectedFinal) return@locked map(found)
        exchange(session, found.stored, send)
    }
    private suspend fun exchange(session: CartSession, stored: LocalOrderIntent, send: Boolean): CheckoutConfirmation {
        if (!sessions.isCurrent(session)) return CheckoutConfirmation.SessionChanged
        if (!access.allows(session.uid!!, send)) return CheckoutConfirmation.PurchaseForbidden
        val original = if (send) {
            val marked = store.markInFlight(session, stored.intent.intentId)
            if (marked !is LocalIntentResult.Ready) return map(marked)
            marked.stored
        } else stored
        if (!sessions.isCurrent(session)) return CheckoutConfirmation.SessionChanged
        // No automatic POST: this bounded block only finishes the explicit user operation and saves
        // a late receipt for its original UID, even if the destination ViewModel is cleared.
        return withContext(NonCancellable) {
            if (!sessions.isCurrent(session)) return@withContext CheckoutConfirmation.SessionChanged
            if (!access.allows(session.uid, send)) return@withContext CheckoutConfirmation.PurchaseForbidden
            when (val response = http.execute(original.intent, session.generation, send)) {
                OrderHttpResult.Conflict -> {
                    store.recordResult(session, original.intent.intentId, OrderAttemptResult.Unknown(OrderUncertainty.SERVER_UNAVAILABLE))
                    if (sessions.isCurrent(session)) CheckoutConfirmation.Conflict(original.intent.intentId) else CheckoutConfirmation.SessionChanged
                }
                is OrderHttpResult.Outcome -> map(store.recordResult(session, original.intent.intentId, response.result))
            }
        }
    }
    private fun map(result: LocalIntentResult): CheckoutConfirmation = when (result) {
        is LocalIntentResult.Ready -> when (val outcome = result.stored.result) {
            is OrderAttemptResult.Succeeded -> CheckoutConfirmation.Success(outcome.receipt, result.stored.cartConsumed)
            is OrderAttemptResult.RejectedFinal -> CheckoutConfirmation.Rejected(result.stored.intent.intentId, outcome.reason)
            is OrderAttemptResult.Unknown -> CheckoutConfirmation.Pending(result.stored.intent.intentId, outcome.reason)
            null -> CheckoutConfirmation.Pending(result.stored.intent.intentId)
        }
        is LocalIntentResult.Unresolved -> CheckoutConfirmation.Pending(result.intentId)
        LocalIntentResult.NotFound -> CheckoutConfirmation.None
        LocalIntentResult.ReviewRequired -> CheckoutConfirmation.ReviewRequired
        LocalIntentResult.SessionChanged -> CheckoutConfirmation.SessionChanged
        LocalIntentResult.Conflict -> CheckoutConfirmation.Conflict()
        LocalIntentResult.StorageError -> CheckoutConfirmation.StorageError
    }
}
