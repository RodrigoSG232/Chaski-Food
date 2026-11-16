package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Conc 13: each error is shown on its own. */
enum class ReconciliationError { REJECTED, REVIEW_NOT_OPEN, NOT_ELIGIBLE, EXCESS_NOT_PENDING, CONFLICT, CONNECTION, PERMISSION }
enum class ReconciliationPhase { EDITING, CONFIRMING, SENDING }

/** Conc 04a: nothing preselected, nothing prefilled; the admin chooses every value explicitly. */
data class ResolveForm(val outcome: CollectionOutcome? = null, val amount: String = "", val evidence: String = "") {
    fun amountMinor() = CollectionReconciliationRules.parseAmountMinor(amount)
    fun amountError(expectedMinor: Long): AmountError? =
        if (amount.isBlank()) null else CollectionReconciliationRules.amountError(outcome, amountMinor(), expectedMinor)
    val evidenceValid get() = CollectionReconciliationRules.evidenceValid(evidence)
    fun complete(expectedMinor: Long) = outcome != null && amount.isNotBlank() && amountError(expectedMinor) == null && evidenceValid
}

/** Conc 06: every value derives from the current form, never from sample data. */
data class ResolveConfirmation(val outcome: CollectionOutcome, val expectedMinor: Long, val declaredMinor: Long,
    val confirmedMinor: Long) {
    val shortfallMinor get() = if (outcome == CollectionOutcome.NOT_FULLY_COLLECTED) expectedMinor - confirmedMinor else 0
    val excessMinor get() = if (outcome == CollectionOutcome.FULLY_COLLECTED) confirmedMinor - expectedMinor else 0
}

data class CollectionReviewsUiState(val loading: Boolean = true, val reviews: List<CollectionReview> = emptyList(),
    val truncated: Boolean = false, val loadError: String? = null, val accessDenied: Boolean = false,
    val selected: CollectionReview? = null, val form: ResolveForm = ResolveForm(), val refundEvidence: String = "",
    val phase: ReconciliationPhase = ReconciliationPhase.EDITING, val error: ReconciliationError? = null,
    /** Conc 08–10 and 12: the confirmed server result (possibly a replay of the same operation). */
    val done: ReconciliationResult? = null) {
    val confirmation: ResolveConfirmation? get() {
        val review = selected ?: return null
        val outcome = form.outcome ?: return null
        val amount = form.amountMinor() ?: return null
        return ResolveConfirmation(outcome, review.expectedMinor, review.declaredMinor, amount)
    }
    /** After a connection error the outcome is unknown: the form is frozen and only the same operation may be retried. */
    val uncertain get() = error == ReconciliationError.CONNECTION
}

@HiltViewModel
class CollectionReviewsViewModel @Inject constructor(private val repository: CollectionReviewsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CollectionReviewsUiState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var sendJob: Job? = null
    /** The operation whose outcome is unknown or about to be sent; a retry reuses it so the server replays it. */
    private var pendingResolve: ResolveCollectionCommand? = null
    private var pendingRefund: RecordExcessRefundCommand? = null
    internal var newOperationId: () -> String = { java.util.UUID.randomUUID().toString() }

    init { load() }

    fun load() {
        if (loadJob?.isActive == true) return
        mutable.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            when (val result = try { repository.reviews() } catch (e: CancellationException) { throw e } catch (_: Exception) { CollectionReviewsResult.Unavailable }) {
                is CollectionReviewsResult.Loaded -> mutable.update { it.copy(loading = false, reviews = result.reviews, truncated = result.truncated) }
                CollectionReviewsResult.AccessDenied, CollectionReviewsResult.AuthenticationRequired ->
                    mutable.value = CollectionReviewsUiState(loading = false, accessDenied = true)
                CollectionReviewsResult.Unavailable -> mutable.update { it.copy(loading = false,
                    loadError = "No pudimos consultar las revisiones de cobro. Comprueba la conexión.") }
            }
        }
    }

    /** Selection waits for a fresh list, so an uncertain operation is always reconciled with the server first. */
    fun select(orderId: String) {
        val current = mutable.value
        if (current.loading || current.selected != null) return
        val review = current.reviews.firstOrNull { it.orderId == orderId } ?: return
        pendingResolve = null; pendingRefund = null
        mutable.update { it.copy(selected = review, form = ResolveForm(), refundEvidence = "", phase = ReconciliationPhase.EDITING,
            error = null, done = null) }
    }

    fun backToList() {
        val current = mutable.value
        if (current.phase == ReconciliationPhase.SENDING || current.selected == null) return
        // Anything that may have changed on the server (a result, an unknown outcome, a concurrent resolution) reloads the list.
        val reload = current.done != null || current.error in setOf(ReconciliationError.CONNECTION, ReconciliationError.REVIEW_NOT_OPEN,
            ReconciliationError.NOT_ELIGIBLE, ReconciliationError.EXCESS_NOT_PENDING, ReconciliationError.CONFLICT)
        pendingResolve = null; pendingRefund = null
        mutable.update { it.copy(selected = null, form = ResolveForm(), refundEvidence = "", phase = ReconciliationPhase.EDITING,
            error = null, done = null) }
        if (reload) load()
    }

    /** Only a content rejection (400/422) keeps the form editable; state errors and unknown outcomes freeze it. */
    private fun editable() = mutable.value.let { it.selected != null && it.phase == ReconciliationPhase.EDITING && it.done == null &&
        (it.error == null || it.error == ReconciliationError.REJECTED) }
    fun setOutcome(outcome: CollectionOutcome) { if (editable()) mutable.update { it.copy(form = it.form.copy(outcome = outcome), error = null) } }
    fun setAmount(raw: String) { if (editable()) mutable.update { it.copy(form = it.form.copy(amount = raw.take(12)), error = null) } }
    fun setEvidence(raw: String) { if (editable()) mutable.update { it.copy(form = it.form.copy(evidence = raw.take(EVIDENCE_INPUT_MAX)), error = null) } }
    fun setRefundEvidence(raw: String) { if (editable()) mutable.update { it.copy(refundEvidence = raw.take(EVIDENCE_INPUT_MAX), error = null) } }

    /** Conc 04 → 06: validates locally and prepares ONE new operation for confirmation. */
    fun requestConfirm() {
        if (!editable()) return
        val state = mutable.value
        val review = state.selected!!
        when (review.kind) {
            CollectionReviewKind.OPEN_REVIEW -> pendingResolve = ResolveCollectionCommand.create(newOperationId(), review,
                state.form.outcome, state.form.amount, state.form.evidence) ?: return
            CollectionReviewKind.EXCESS_PENDING_REFUND -> pendingRefund = RecordExcessRefundCommand.create(newOperationId(), review,
                state.refundEvidence) ?: return
        }
        mutable.update { it.copy(phase = ReconciliationPhase.CONFIRMING) }
    }

    fun cancelConfirm() {
        val state = mutable.value
        if (state.phase != ReconciliationPhase.CONFIRMING || state.uncertain) return
        pendingResolve = null; pendingRefund = null
        mutable.update { it.copy(phase = ReconciliationPhase.EDITING) }
    }

    fun confirm() { if (mutable.value.phase == ReconciliationPhase.CONFIRMING && !mutable.value.uncertain) send() }
    /** Only after a connection error: the SAME operation, so a confirmed operation is never duplicated. */
    fun retry() { if (mutable.value.uncertain) send() }

    private fun send() {
        val review = mutable.value.selected ?: return
        if (sendJob?.isActive == true || mutable.value.phase == ReconciliationPhase.SENDING) return
        val resolve = pendingResolve; val refund = pendingRefund
        if (resolve == null && refund == null) return
        mutable.update { it.copy(phase = ReconciliationPhase.SENDING) }
        sendJob = viewModelScope.launch {
            val result = try {
                if (resolve != null) repository.resolve(review.orderId, resolve) else repository.recordRefund(review.orderId, refund!!)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { ReconciliationResult.Unavailable }
            if (result != ReconciliationResult.Unavailable) { pendingResolve = null; pendingRefund = null }
            mutable.update { s ->
                when (result) {
                    is ReconciliationResult.Resolved, is ReconciliationResult.Refunded ->
                        s.copy(phase = ReconciliationPhase.EDITING, error = null, done = result)
                    ReconciliationResult.Unavailable -> s.copy(phase = ReconciliationPhase.CONFIRMING, error = ReconciliationError.CONNECTION)
                    ReconciliationResult.AccessDenied, ReconciliationResult.AuthenticationRequired ->
                        CollectionReviewsUiState(loading = false, accessDenied = true)
                    else -> s.copy(phase = ReconciliationPhase.EDITING, error = when (result) {
                        ReconciliationResult.ReviewNotOpen -> ReconciliationError.REVIEW_NOT_OPEN
                        ReconciliationResult.ReviewNotEligible, ReconciliationResult.NotFound -> ReconciliationError.NOT_ELIGIBLE
                        ReconciliationResult.ExcessNotPending -> ReconciliationError.EXCESS_NOT_PENDING
                        ReconciliationResult.OperationConflict -> ReconciliationError.CONFLICT
                        else -> ReconciliationError.REJECTED
                    })
                }
            }
        }
    }

    companion object { const val EVIDENCE_INPUT_MAX = CollectionReconciliationRules.EVIDENCE_MAX + 20 }
}
