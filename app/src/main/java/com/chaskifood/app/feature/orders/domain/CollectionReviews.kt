package com.chaskifood.app.feature.orders.domain

/**
 * HU22 (E5.4b): administrative reconciliation of a Rider cash collection, mirroring the foodApi E5.4a contract.
 * Narrow admin exception: only the minimal review data below, never buyer data, actors or evidence of other admins.
 * Every amount is an integer in PEN minor units (céntimos); soles are only a presentation concern.
 */
enum class CollectionReviewKind { OPEN_REVIEW, EXCESS_PENDING_REFUND }
enum class CollectionOutcome { FULLY_COLLECTED, NOT_FULLY_COLLECTED }
enum class ExcessStatus { NONE, PENDING_REFUND, REFUNDED }

data class CollectionResolution(val outcome: CollectionOutcome, val confirmedMinor: Long, val shortfallMinor: Long,
    val excessMinor: Long, val excessStatus: ExcessStatus) {
    init { require(confirmedMinor >= 0 && shortfallMinor >= 0 && excessMinor >= 0) }
}

data class CollectionReview(val orderId: String, val kind: CollectionReviewKind, val storeName: String?, val expectedMinor: Long,
    val declaredMinor: Long, val method: String, val openedAtEpochMillis: Long?, val resolution: CollectionResolution?) {
    init {
        require(orderId.matches(Regex("[0-9a-f]{64}")) && expectedMinor >= 1 && declaredMinor >= 1)
        // An excess row only exists for a full collection whose excess is still owed.
        require(kind == CollectionReviewKind.OPEN_REVIEW && resolution == null ||
            kind == CollectionReviewKind.EXCESS_PENDING_REFUND && resolution?.outcome == CollectionOutcome.FULLY_COLLECTED &&
            resolution.excessStatus == ExcessStatus.PENDING_REFUND && resolution.excessMinor >= 1)
    }
    val shortId get() = orderId.take(8).uppercase()
}

sealed interface CollectionReviewsResult {
    data class Loaded(val reviews: List<CollectionReview>, val truncated: Boolean) : CollectionReviewsResult
    data object AccessDenied : CollectionReviewsResult
    data object AuthenticationRequired : CollectionReviewsResult
    data object Unavailable : CollectionReviewsResult
}

/** Pure rules shared by the form, the confirmation and the tests. The backend remains the final authority. */
object CollectionReconciliationRules {
    const val EVIDENCE_MIN = 5
    const val EVIDENCE_MAX = 300
    const val MAX_AMOUNT_MINOR = 10_000_000L
    private val UUID_V4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val AMOUNT = Regex("""(\d{1,6})(?:[.,](\d{1,2}))?""")

    fun validOperationId(id: String) = UUID_V4.matches(id)

    /** "87", "87.5", "87,50" → céntimos, using integer arithmetic only (never Float/Double). */
    fun parseAmountMinor(raw: String): Long? {
        val match = AMOUNT.matchEntire(raw.trim()) ?: return null
        val soles = match.groupValues[1].toLong()
        val cents = match.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        val minor = soles * 100 + cents
        return minor.takeIf { it in 0..MAX_AMOUNT_MINOR }
    }

    fun evidenceValid(raw: String) = raw.trim().let { it.length in EVIDENCE_MIN..EVIDENCE_MAX && it.none { c -> c.isISOControl() } }

    /** "S/ 87.00" from céntimos, with integer arithmetic. */
    fun formatSoles(minor: Long): String {
        require(minor >= 0)
        return "S/ ${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
    }

    /** Same decision as foodApi: the outcome must match the amount; nothing is inferred for the admin. */
    fun amountError(outcome: CollectionOutcome?, amountMinor: Long?, expectedMinor: Long): AmountError? = when {
        amountMinor == null -> AmountError.INVALID
        outcome == CollectionOutcome.FULLY_COLLECTED && amountMinor < expectedMinor -> AmountError.BELOW_TOTAL
        outcome == CollectionOutcome.NOT_FULLY_COLLECTED && amountMinor >= expectedMinor -> AmountError.COVERS_TOTAL
        else -> null
    }
}

enum class AmountError { INVALID, BELOW_TOTAL, COVERS_TOTAL }

/** One RESOLVE intent. A retry after an unknown outcome reuses it unchanged, so the server replays the same operation. */
class ResolveCollectionCommand private constructor(val operationId: String, val outcome: CollectionOutcome,
    val confirmedMinor: Long, val evidence: String) {
    companion object {
        fun create(operationId: String, review: CollectionReview, outcome: CollectionOutcome?, rawAmount: String,
            rawEvidence: String): ResolveCollectionCommand? {
            if (!CollectionReconciliationRules.validOperationId(operationId) || outcome == null ||
                review.kind != CollectionReviewKind.OPEN_REVIEW) return null
            val amount = CollectionReconciliationRules.parseAmountMinor(rawAmount) ?: return null
            if (CollectionReconciliationRules.amountError(outcome, amount, review.expectedMinor) != null) return null
            if (!CollectionReconciliationRules.evidenceValid(rawEvidence)) return null
            return ResolveCollectionCommand(operationId, outcome, amount, rawEvidence.trim())
        }
    }
}

/** RECORD_EXCESS_REFUND: documents a refund made OUTSIDE the app; always the full pending excess (no partial refunds). */
class RecordExcessRefundCommand private constructor(val operationId: String, val refundedMinor: Long, val evidence: String) {
    companion object {
        fun create(operationId: String, review: CollectionReview, rawEvidence: String): RecordExcessRefundCommand? {
            val excess = review.resolution?.takeIf { review.kind == CollectionReviewKind.EXCESS_PENDING_REFUND }?.excessMinor ?: return null
            if (!CollectionReconciliationRules.validOperationId(operationId) || !CollectionReconciliationRules.evidenceValid(rawEvidence)) return null
            return RecordExcessRefundCommand(operationId, excess, rawEvidence.trim())
        }
    }
}

sealed interface ReconciliationResult {
    data class Resolved(val resolution: CollectionResolution, val expectedMinor: Long, val declaredMinor: Long,
        val replayed: Boolean) : ReconciliationResult
    data class Refunded(val excessMinor: Long, val replayed: Boolean) : ReconciliationResult
    /** 409 REVIEW_NOT_OPEN: another admin (or this one earlier) already resolved it. */
    data object ReviewNotOpen : ReconciliationResult
    data object ReviewNotEligible : ReconciliationResult
    data object ExcessNotPending : ReconciliationResult
    data object OperationConflict : ReconciliationResult
    /** 400 or 422: the server rejected the content (amount/outcome or refund mismatch, evidence). */
    data object Invalid : ReconciliationResult
    data object AccessDenied : ReconciliationResult
    data object AuthenticationRequired : ReconciliationResult
    data object NotFound : ReconciliationResult
    /** Timeout or network failure: the operation MAY have been applied; only the same operation may be retried. */
    data object Unavailable : ReconciliationResult
}

interface CollectionReviewsRepository {
    suspend fun reviews(): CollectionReviewsResult
    suspend fun resolve(orderId: String, command: ResolveCollectionCommand): ReconciliationResult
    suspend fun recordRefund(orderId: String, command: RecordExcessRefundCommand): ReconciliationResult
}
