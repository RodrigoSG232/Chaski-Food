package com.chaskifood.app.feature.orders.presentation

import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.orders.domain.CollectionReconciliationRules as Rules
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** E5.4b: admin reconciliation (HU22 / Conc). Amounts are céntimos; S/87.00 = 8700. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReviewsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private val open = CollectionReview("a".repeat(64), CollectionReviewKind.OPEN_REVIEW, "Local ficticio", 8700, 4000, "CASH", 1_800_000_000_000, null)
    private val excess = CollectionReview("b".repeat(64), CollectionReviewKind.EXCESS_PENDING_REFUND, "Local ficticio", 8700, 9000, "YAPE", null,
        CollectionResolution(CollectionOutcome.FULLY_COLLECTED, 9000, 0, 300, ExcessStatus.PENDING_REFUND))
    private val evidence = "Arqueo de caja revisado con el local"

    private class Repo : CollectionReviewsRepository {
        var list: CollectionReviewsResult = CollectionReviewsResult.Loaded(emptyList(), false); var loads = 0
        var result: ReconciliationResult = ReconciliationResult.Unavailable
        val resolves = mutableListOf<ResolveCollectionCommand>(); val refunds = mutableListOf<RecordExcessRefundCommand>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun reviews(): CollectionReviewsResult { loads++; return list }
        override suspend fun resolve(orderId: String, command: ResolveCollectionCommand): ReconciliationResult { resolves += command; gate?.await(); return result }
        override suspend fun recordRefund(orderId: String, command: RecordExcessRefundCommand): ReconciliationResult { refunds += command; return result }
    }
    private fun TestScope.vm(repo: Repo, vararg reviews: CollectionReview, truncated: Boolean = false): CollectionReviewsViewModel {
        repo.list = CollectionReviewsResult.Loaded(reviews.toList(), truncated)
        var n = 0
        return CollectionReviewsViewModel(repo).also { it.newOperationId = { "0b8f1a52-3c4d-4e5f-8a6b-7c8d9e0f1a2${n++}" }; runCurrent() }
    }
    private fun CollectionReviewsViewModel.fill(outcome: CollectionOutcome, amount: String, text: String = evidence) {
        setOutcome(outcome); setAmount(amount); setEvidence(text)
    }
    private val resolvedFull = ReconciliationResult.Resolved(CollectionResolution(CollectionOutcome.FULLY_COLLECTED, 8700, 0, 0, ExcessStatus.NONE), 8700, 4000, false)

    // ---- Domain rules
    @Test fun amountsParseToCentimosWithoutFloatingPoint() {
        assertEquals(8700L, Rules.parseAmountMinor("87")); assertEquals(8750L, Rules.parseAmountMinor("87.5"))
        assertEquals(8705L, Rules.parseAmountMinor(" 87,05 ")); assertEquals(0L, Rules.parseAmountMinor("0"))
        assertEquals(10_000_000L, Rules.parseAmountMinor("100000"))
        for (bad in listOf("", "abc", "87.123", "-1", "1e3", "87.", ".5", "100000.01", "1 000")) assertNull(bad, Rules.parseAmountMinor(bad))
        assertEquals("S/ 87.00", Rules.formatSoles(8700)); assertEquals("S/ 0.05", Rules.formatSoles(5)); assertEquals("S/ 3.00", Rules.formatSoles(300))
    }
    @Test fun outcomeMustMatchTheAmountLikeTheBackend() {
        assertNull(Rules.amountError(CollectionOutcome.FULLY_COLLECTED, 8700, 8700))
        assertNull(Rules.amountError(CollectionOutcome.FULLY_COLLECTED, 9000, 8700))
        assertEquals(AmountError.BELOW_TOTAL, Rules.amountError(CollectionOutcome.FULLY_COLLECTED, 8699, 8700))
        assertNull(Rules.amountError(CollectionOutcome.NOT_FULLY_COLLECTED, 0, 8700))
        assertEquals(AmountError.COVERS_TOTAL, Rules.amountError(CollectionOutcome.NOT_FULLY_COLLECTED, 8700, 8700))
        assertEquals(AmountError.INVALID, Rules.amountError(CollectionOutcome.FULLY_COLLECTED, null, 8700))
    }
    @Test fun evidenceIsTrimmedAndBounded() {
        assertTrue(Rules.evidenceValid("  Cinco  ")); assertFalse(Rules.evidenceValid("  abcd  "))
        assertTrue(Rules.evidenceValid("x".repeat(300))); assertFalse(Rules.evidenceValid("x".repeat(301)))
        assertFalse(Rules.evidenceValid("línea\nsalto"))
        val op = "0b8f1a52-3c4d-4e5f-8a6b-7c8d9e0f1a2b"
        assertEquals("Cinco", ResolveCollectionCommand.create(op, open, CollectionOutcome.FULLY_COLLECTED, "87", "  Cinco  ")!!.evidence)
        assertNull(ResolveCollectionCommand.create("not-uuid", open, CollectionOutcome.FULLY_COLLECTED, "87", evidence))
        assertNull(ResolveCollectionCommand.create(op, excess, CollectionOutcome.FULLY_COLLECTED, "87", evidence))
        assertEquals(300L, RecordExcessRefundCommand.create(op, excess, evidence)!!.refundedMinor)
        assertNull(RecordExcessRefundCommand.create(op, open, evidence))
    }

    // ---- List
    @Test fun listEmptyTruncatedAndPermissionStates() = runTest {
        val repo = Repo(); val empty = vm(repo)
        assertFalse(empty.state.value.loading); assertTrue(empty.state.value.reviews.isEmpty()); assertFalse(empty.state.value.truncated)
        assertTrue(vm(Repo(), open, excess, truncated = true).state.value.truncated)
        val denied = Repo().apply { list = CollectionReviewsResult.AccessDenied }
        assertTrue(CollectionReviewsViewModel(denied).also { runCurrent() }.state.value.accessDenied)
        val offline = Repo().apply { list = CollectionReviewsResult.Unavailable }
        assertNotNull(CollectionReviewsViewModel(offline).also { runCurrent() }.state.value.loadError)
    }

    // ---- Form (Conc 04a/04/05/06)
    @Test fun initialFormSelectsNothingAndCannotContinue() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        val form = vm.state.value.form
        assertNull(form.outcome); assertEquals("", form.amount); assertEquals("", form.evidence)
        assertFalse(form.complete(open.expectedMinor))
        vm.requestConfirm(); assertEquals(ReconciliationPhase.EDITING, vm.state.value.phase)
        // Typing the expected total alone never validates anything.
        vm.setAmount("87.00"); vm.setEvidence(evidence); assertFalse(vm.state.value.form.complete(open.expectedMinor))
        vm.requestConfirm(); runCurrent(); assertTrue(repo.resolves.isEmpty())
    }
    @Test fun fullyCollectedNeedsTheWholeTotal() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "86.99")
        assertEquals(AmountError.BELOW_TOTAL, vm.state.value.form.amountError(8700)); vm.requestConfirm()
        assertEquals(ReconciliationPhase.EDITING, vm.state.value.phase)
        vm.setAmount("90"); vm.requestConfirm()
        val c = vm.state.value.confirmation!!
        assertEquals(ReconciliationPhase.CONFIRMING, vm.state.value.phase)
        assertEquals(listOf(8700L, 4000L, 9000L, 300L, 0L), listOf(c.expectedMinor, c.declaredMinor, c.confirmedMinor, c.excessMinor, c.shortfallMinor))
    }
    @Test fun notFullyCollectedConfirmationShowsTheShortfallFromTheForm() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.NOT_FULLY_COLLECTED, "87"); assertEquals(AmountError.COVERS_TOTAL, vm.state.value.form.amountError(8700))
        vm.setAmount("40.00"); vm.requestConfirm()
        val c = vm.state.value.confirmation!!
        assertEquals(CollectionOutcome.NOT_FULLY_COLLECTED, c.outcome); assertEquals(4700L, c.shortfallMinor); assertEquals(0L, c.excessMinor)
        vm.cancelConfirm(); assertEquals(ReconciliationPhase.EDITING, vm.state.value.phase)
    }
    @Test fun invalidEvidenceNeverReachesTheServer() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "87", "  ok  "); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertTrue(repo.resolves.isEmpty()); assertEquals(ReconciliationPhase.EDITING, vm.state.value.phase)
    }

    // ---- Idempotency, double submit and errors
    @Test fun aSecondTapWhileSendingIsIgnored() = runTest {
        val repo = Repo().apply { result = resolvedFull; gate = CompletableDeferred() }; val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertEquals(ReconciliationPhase.SENDING, vm.state.value.phase)
        vm.confirm(); vm.retry(); vm.requestConfirm(); vm.setAmount("1"); runCurrent()
        assertEquals(1, repo.resolves.size); assertEquals("87", vm.state.value.form.amount)
        repo.gate!!.complete(Unit); runCurrent(); assertEquals(resolvedFull, vm.state.value.done)
    }
    @Test fun anUnknownOutcomeRetriesTheSameOperationAndFreezesTheForm() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertTrue(vm.state.value.uncertain); assertEquals(ReconciliationPhase.CONFIRMING, vm.state.value.phase)
        vm.setAmount("50"); vm.cancelConfirm(); vm.confirm(); runCurrent()
        assertEquals("87", vm.state.value.form.amount); assertEquals(ReconciliationPhase.CONFIRMING, vm.state.value.phase)
        assertEquals(1, repo.resolves.size) // confirm() does not create a new operation while uncertain
        repo.result = resolvedFull.copy(replayed = true); vm.retry(); runCurrent()
        assertEquals(2, repo.resolves.size); assertEquals(repo.resolves[0].operationId, repo.resolves[1].operationId)
        assertEquals(8700L, repo.resolves[1].confirmedMinor); assertTrue((vm.state.value.done as ReconciliationResult.Resolved).replayed)
    }
    @Test fun leavingAnUncertainOperationReloadsBeforeAnyNewAction() = runTest {
        val repo = Repo(); val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
        val loads = repo.loads; vm.backToList()
        assertTrue(vm.state.value.loading); vm.select(open.orderId); assertNull(vm.state.value.selected) // blocked until reloaded
        runCurrent(); assertTrue(repo.loads > loads)
        vm.select(open.orderId); vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertNotEquals(repo.resolves[0].operationId, repo.resolves[1].operationId) // a NEW action gets a NEW operation
    }
    @Test fun reviewResolvedByAnotherAdminReloadsTheList() = runTest {
        val repo = Repo().apply { result = ReconciliationResult.ReviewNotOpen }; val vm = vm(repo, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.NOT_FULLY_COLLECTED, "40"); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertEquals(ReconciliationError.REVIEW_NOT_OPEN, vm.state.value.error)
        vm.setAmount("10"); assertEquals("40", vm.state.value.form.amount) // frozen
        val loads = repo.loads; vm.backToList(); runCurrent(); assertTrue(repo.loads > loads)
    }
    @Test fun errorsAreDistinctAndPermissionLossClosesAccess() = runTest {
        for ((result, error) in listOf(ReconciliationResult.OperationConflict to ReconciliationError.CONFLICT,
            ReconciliationResult.ReviewNotEligible to ReconciliationError.NOT_ELIGIBLE, ReconciliationResult.Invalid to ReconciliationError.REJECTED)) {
            val repo = Repo().apply { this.result = result }; val vm = vm(repo, open); vm.select(open.orderId)
            vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
            assertEquals(error, vm.state.value.error)
        }
        // A server-side content rejection keeps the form editable to correct it.
        val rejected = Repo().apply { result = ReconciliationResult.Invalid }; val vm = vm(rejected, open); vm.select(open.orderId)
        vm.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm.requestConfirm(); vm.confirm(); runCurrent()
        vm.setEvidence("Evidencia corregida por el admin"); assertNull(vm.state.value.error)
        val lost = Repo().apply { result = ReconciliationResult.AccessDenied }; val vm2 = vm(lost, open); vm2.select(open.orderId)
        vm2.fill(CollectionOutcome.FULLY_COLLECTED, "87"); vm2.requestConfirm(); vm2.confirm(); runCurrent()
        assertTrue(vm2.state.value.accessDenied); assertNull(vm2.state.value.selected)
    }

    // ---- Refund (Conc 11/12)
    @Test fun refundSendsTheFullExcessOnceAndCannotBeRepeated() = runTest {
        val repo = Repo().apply { result = ReconciliationResult.Refunded(300, false) }; val vm = vm(repo, excess); vm.select(excess.orderId)
        vm.requestConfirm(); assertEquals(ReconciliationPhase.EDITING, vm.state.value.phase) // evidence is mandatory
        vm.setRefundEvidence("El local devolvió S/ 3.00 en efectivo"); vm.requestConfirm(); vm.confirm(); runCurrent()
        assertEquals(300L, repo.refunds.single().refundedMinor); assertEquals(ReconciliationResult.Refunded(300, false), vm.state.value.done)
        vm.requestConfirm(); vm.confirm(); runCurrent(); assertEquals(1, repo.refunds.size)
        val again = Repo().apply { result = ReconciliationResult.ExcessNotPending }; val vm2 = vm(again, excess); vm2.select(excess.orderId)
        vm2.setRefundEvidence("Devuelto en efectivo"); vm2.requestConfirm(); vm2.confirm(); runCurrent()
        assertEquals(ReconciliationError.EXCESS_NOT_PENDING, vm2.state.value.error)
        vm2.requestConfirm(); runCurrent(); assertEquals(1, again.refunds.size)
    }
}
