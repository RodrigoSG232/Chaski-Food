package com.chaskifood.app.feature.checkout.data.local

import androidx.room.withTransaction
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.checkout.data.CheckoutPreparationGuard
import com.chaskifood.app.feature.checkout.domain.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

enum class LocalOrderIntentState { PREPARED, IN_FLIGHT, UNKNOWN, SUCCEEDED, REJECTED_FINAL }
internal data class LocalOrderIntent(
    val intent: OrderIntent, val baseCartRevision: Long, val state: LocalOrderIntentState,
    val result: OrderAttemptResult?, val receiptApplied: Boolean, val cartConsumed: Boolean,
)
internal sealed interface LocalIntentResult {
    data class Ready(val stored: LocalOrderIntent) : LocalIntentResult
    data class Unresolved(val intentId: String) : LocalIntentResult
    data object NotFound : LocalIntentResult
    data object ReviewRequired : LocalIntentResult
    data object SessionChanged : LocalIntentResult
    data object Conflict : LocalIntentResult
    data object StorageError : LocalIntentResult
}

/** Solo Room: ninguna operación envía HTTP, programa compras ni obtiene tokens. */
@Singleton
internal class CheckoutIntentStore @Inject constructor(
    private val database: ChaskiDatabase,
    private val sessions: CartSessionSource,
    private val context: CheckoutPreparationGuard,
    private val clock: CheckoutClock,
) {
    private val dao = database.checkoutIntentDao()
    private val carts = database.cartDao()
    private class StalePreparation : RuntimeException()
    private class SessionDuringConsumption : RuntimeException()

    /** Invocar con el contexto que originó la revisión y tras consentimiento explícito del usuario. */
    suspend fun prepare(expected: CheckoutContext, reviewed: CheckoutQuote, accepted: AcceptedPurchase,
        intentId: String): LocalIntentResult = storage {
        val session = CartSession(expected.ownerUid, expected.sessionGeneration)
        if (!sessions.isCurrent(session)) return@storage LocalIntentResult.SessionChanged
        if (!context.isCurrent(expected) || !matches(expected, reviewed, accepted)) return@storage LocalIntentResult.ReviewRequired
        val intent = OrderIntent(intentId, accepted)
        val row = CheckoutIntentEntity(intent.ownerUid, intent.intentId, 1, OrderIntentCodec.encode(intent), intent.requestHash,
            accepted.deliveryQuote.quoteId, expected.cartRevision, LocalOrderIntentState.PREPARED.name,
            null, null, null, null, false, false)
        try {
            val result = database.withTransaction {
                val current = carts.getCart(intent.ownerUid)
                if (current == null || current.cart.revision != expected.cartRevision || current.toDomain() != expected.cart ||
                    !context.isObservedCurrent(expected) || !sessions.isCurrent(session)) throw StalePreparation()
                val existing = dao.get(intent.ownerUid, intent.intentId)
                if (existing != null) {
                    if (existing.requestHash != intent.requestHash) return@withTransaction LocalIntentResult.Conflict
                    return@withTransaction LocalIntentResult.Ready(decode(existing))
                }
                val pending = dao.unresolved(intent.ownerUid)
                if (pending != null) return@withTransaction LocalIntentResult.Unresolved(pending.intentId)
                dao.insert(row)
                if (!context.isObservedCurrent(expected) || !sessions.isCurrent(session)) throw StalePreparation()
                LocalIntentResult.Ready(decode(row))
            }
            if (sessions.isCurrent(session)) result else LocalIntentResult.SessionChanged
        } catch (_: StalePreparation) {
            if (sessions.isCurrent(session)) LocalIntentResult.ReviewRequired else LocalIntentResult.SessionChanged
        }
    }

    /** IN_FLIGHT tras reapertura es incierto; conserva ID y bytes originales. */
    suspend fun recover(session: CartSession, intentId: String): LocalIntentResult = storage {
        val uid = session.uid ?: return@storage LocalIntentResult.SessionChanged
        if (!sessions.isCurrent(session)) return@storage LocalIntentResult.SessionChanged
        val result = database.withTransaction {
            val row = dao.get(uid, intentId) ?: return@withTransaction LocalIntentResult.NotFound
            val recovered = if (row.state == LocalOrderIntentState.IN_FLIGHT.name) row.copy(
                state = LocalOrderIntentState.UNKNOWN.name, uncertainty = OrderUncertainty.NOT_OBSERVED.name) else row
            if (recovered != row) dao.update(recovered)
            LocalIntentResult.Ready(decode(recovered))
        }
        if (sessions.isCurrent(session)) result else LocalIntentResult.SessionChanged
    }

    suspend fun unresolved(session: CartSession): LocalIntentResult = storage {
        val uid = session.uid ?: return@storage LocalIntentResult.SessionChanged
        if (!sessions.isCurrent(session)) return@storage LocalIntentResult.SessionChanged
        val row = dao.unresolved(uid)
        if (!sessions.isCurrent(session)) LocalIntentResult.SessionChanged
        else if (row == null) LocalIntentResult.NotFound else LocalIntentResult.Ready(decode(row))
    }

    /** Persistir esta transición antes de la futura llamada HTTP; nunca genera otro ID. */
    suspend fun markInFlight(session: CartSession, intentId: String): LocalIntentResult = storage {
        val uid = session.uid ?: return@storage LocalIntentResult.SessionChanged
        if (!sessions.isCurrent(session)) return@storage LocalIntentResult.SessionChanged
        val result = database.withTransaction {
            val row = dao.get(uid, intentId) ?: return@withTransaction LocalIntentResult.NotFound
            val state = LocalOrderIntentState.valueOf(row.state)
            if (state == LocalOrderIntentState.SUCCEEDED || state == LocalOrderIntentState.REJECTED_FINAL) {
                return@withTransaction LocalIntentResult.Ready(decode(row))
            }
            val next = row.copy(state = LocalOrderIntentState.IN_FLIGHT.name, uncertainty = null)
            dao.update(next)
            LocalIntentResult.Ready(decode(next))
        }
        if (sessions.isCurrent(session)) result else LocalIntentResult.SessionChanged
    }

    /** Una respuesta tardía puede guardarse para su UID original, sin publicarla en otra sesión. */
    suspend fun recordResult(session: CartSession, intentId: String, result: OrderAttemptResult): LocalIntentResult = storage {
        try { record(session, intentId, result, allowConsumption = true) }
        catch (_: SessionDuringConsumption) { record(session, intentId, result, allowConsumption = false) }
    }

    suspend fun applyRecoveredReceipt(session: CartSession, intentId: String): LocalIntentResult = storage {
        val uid = session.uid ?: return@storage LocalIntentResult.SessionChanged
        if (!sessions.isCurrent(session)) return@storage LocalIntentResult.SessionChanged
        val row = dao.get(uid, intentId) ?: return@storage LocalIntentResult.NotFound
        val result = decode(row).result as? OrderAttemptResult.Succeeded ?: return@storage LocalIntentResult.Conflict
        recordResult(session, intentId, result)
    }

    private suspend fun record(session: CartSession, intentId: String, result: OrderAttemptResult,
        allowConsumption: Boolean): LocalIntentResult {
        val uid = session.uid ?: return LocalIntentResult.SessionChanged
        val stored = database.withTransaction {
            val original = dao.get(uid, intentId) ?: return@withTransaction LocalIntentResult.NotFound
            val previous = decode(original)
            if (result !is OrderAttemptResult.Unknown && runCatching {
                RecordedOrderAttempt(uid, intentId, original.requestHash, result)
            }.isFailure) return@withTransaction LocalIntentResult.Conflict
            if (previous.result is OrderAttemptResult.Succeeded || previous.result is OrderAttemptResult.RejectedFinal) {
                if (result !is OrderAttemptResult.Unknown && !sameTerminal(previous.result, result)) return@withTransaction LocalIntentResult.Conflict
            }
            var next = when {
                previous.state == LocalOrderIntentState.SUCCEEDED || previous.state == LocalOrderIntentState.REJECTED_FINAL -> original
                result is OrderAttemptResult.Succeeded -> original.copy(state = LocalOrderIntentState.SUCCEEDED.name,
                    receiptOrderId = result.receipt.orderId, receiptCreatedAt = result.receipt.createdAtEpochMillis, uncertainty = null)
                result is OrderAttemptResult.RejectedFinal -> original.copy(state = LocalOrderIntentState.REJECTED_FINAL.name,
                    rejection = result.reason.name, uncertainty = null)
                result is OrderAttemptResult.Unknown -> original.copy(state = LocalOrderIntentState.UNKNOWN.name, uncertainty = result.reason.name)
                else -> error("Resultado no reconocido")
            }
            val canApply = next.state == LocalOrderIntentState.SUCCEEDED.name && !next.receiptApplied &&
                allowConsumption && sessions.isCurrent(session)
            if (canApply) {
                val current = carts.getCart(uid)
                val consume = current?.cart?.revision == next.baseCartRevision
                if (consume) carts.deleteCart(uid)
                next = next.copy(receiptApplied = true, cartConsumed = consume)
                dao.update(next)
                if (!sessions.isCurrent(session)) throw SessionDuringConsumption() // Room revierte recibo + consumo juntos.
            } else if (next != original) dao.update(next)
            LocalIntentResult.Ready(decode(next))
        }
        return if (sessions.isCurrent(session)) stored else LocalIntentResult.SessionChanged
    }

    private fun matches(c: CheckoutContext, q: CheckoutQuote, a: AcceptedPurchase): Boolean {
        val address = c.selectedAddress ?: return false
        return c.cartRevision >= 0 && c.ownerUid == a.ownerUid && c.cart.ownerUid == a.ownerUid &&
            c.cart.businessId == a.businessId && c.cart.storeId == a.storeId &&
            q.ownerUid == a.ownerUid && q.storeId == a.storeId && q.storeName == a.storeName &&
            q.addressId == address.id && q.addressLabel == address.label && q.addressText == address.addressText &&
            a.delivery.addressId == address.id && a.delivery.label == address.label &&
            a.delivery.destination.addressText == address.addressText && a.delivery.destination.location == address.location &&
            a.delivery.destination.reference == address.reference && a.delivery.destination.instructions == address.instructions &&
            a.deliveryQuote.quoteId == q.deliveryQuote.quoteId && a.deliveryQuote.quote == q.deliveryQuote &&
            q.deliveryQuoteDeadlineMillis > clock.monotonicTimeMillis() &&
            q.subtotalMinor == a.amounts.subtotalMinor && q.deliveryFeeMinor == a.amounts.deliveryFeeMinor && q.totalMinor == a.amounts.totalMinor &&
            q.lines.map { listOf(it.productId, it.name, it.quantity, it.unitPriceMinor, it.lineTotalMinor) }.sortedBy { it[0] as String } ==
                a.items.map { listOf(it.productId, it.name, it.quantity, it.unitPriceMinor, it.lineTotalMinor) } &&
            c.cart.items.map { it.productId to it.quantity }.sortedBy { it.first } == a.items.map { it.productId to it.quantity }
    }

    private fun decode(row: CheckoutIntentEntity): LocalOrderIntent {
        require(row.payloadVersion == 1 && row.baseCartRevision >= 0)
        val intent = OrderIntentCodec.decode(row.payload)
        require(intent.ownerUid == row.ownerUid && intent.intentId == row.intentId && intent.requestHash == row.requestHash &&
            intent.accepted.deliveryQuote.quoteId == row.quoteId)
        val state = LocalOrderIntentState.valueOf(row.state)
        val result = when (state) {
            LocalOrderIntentState.SUCCEEDED -> OrderAttemptResult.Succeeded(OrderReceipt(requireNotNull(row.receiptOrderId),
                row.intentId, intent.accepted, requireNotNull(row.receiptCreatedAt)))
            LocalOrderIntentState.REJECTED_FINAL -> OrderAttemptResult.RejectedFinal(OrderRejection.valueOf(requireNotNull(row.rejection)))
            LocalOrderIntentState.UNKNOWN -> OrderAttemptResult.Unknown(OrderUncertainty.valueOf(requireNotNull(row.uncertainty)))
            else -> null
        }
        return LocalOrderIntent(intent, row.baseCartRevision, state, result, row.receiptApplied, row.cartConsumed)
    }

    private fun sameTerminal(a: OrderAttemptResult, b: OrderAttemptResult): Boolean = when {
        a is OrderAttemptResult.RejectedFinal && b is OrderAttemptResult.RejectedFinal -> a.reason == b.reason
        a is OrderAttemptResult.Succeeded && b is OrderAttemptResult.Succeeded ->
            a.receipt.orderId == b.receipt.orderId && a.receipt.createdAtEpochMillis == b.receipt.createdAtEpochMillis &&
                OrderIntent(a.receipt.intentId, a.receipt.accepted).requestHash == OrderIntent(b.receipt.intentId, b.receipt.accepted).requestHash
        else -> false
    }
    private suspend fun storage(block: suspend () -> LocalIntentResult): LocalIntentResult = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { LocalIntentResult.StorageError }
}
