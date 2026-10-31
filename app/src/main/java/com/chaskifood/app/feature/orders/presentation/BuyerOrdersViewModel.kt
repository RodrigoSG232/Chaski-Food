package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.*
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Estado de una pestaña. `canSearchMore` con lista vacía significa «aún no encontramos pedidos», nunca «no hay pedidos». */
data class BuyerOrdersPhaseState(
    val orders: List<BuyerOrderSummary> = emptyList(), val nextCursor: String? = null, val loaded: Boolean = false,
    val loading: Boolean = false, val loadingMore: Boolean = false, val message: String? = null,
) {
    val canSearchMore: Boolean get() = nextCursor != null && !loading && !loadingMore
    val confirmedEmpty: Boolean get() = loaded && orders.isEmpty() && nextCursor == null && message == null
}

data class BuyerOrdersUiState(
    val signedIn: Boolean = true, val selected: BuyerOrderPhase = BuyerOrderPhase.ACTIVE,
    val phases: Map<BuyerOrderPhase, BuyerOrdersPhaseState> = BuyerOrderPhase.entries.associateWith { BuyerOrdersPhaseState() },
) {
    val current: BuyerOrdersPhaseState get() = phases.getValue(selected)
}

@HiltViewModel
class BuyerOrdersViewModel @Inject constructor(private val repository: BuyerOrdersRepository) : ViewModel() {
    private val mutable = MutableStateFlow(BuyerOrdersUiState())
    val state = mutable.asStateFlow()
    private var session: BuyerOrdersSession? = null
    private val jobs = mutableMapOf<BuyerOrderPhase, Job>()
    private val tickets = mutableMapOf<BuyerOrderPhase, Long>()

    init {
        viewModelScope.launch {
            repository.observeSession().catch { e -> if (e is CancellationException) throw e; emit(null) }.collect { next ->
                if (next == session) return@collect
                jobs.values.forEach { it.cancel() }; jobs.clear(); BuyerOrderPhase.entries.forEach { bump(it) }
                session = next
                mutable.value = BuyerOrdersUiState(signedIn = next != null, selected = mutable.value.selected)
                if (next != null) fetch(next, mutable.value.selected, null, AUTO_CONTINUE)
            }
        }
    }

    fun select(phase: BuyerOrderPhase) {
        mutable.update { it.copy(selected = phase) }
        val s = session ?: return
        if (!mutable.value.phases.getValue(phase).loaded && jobs[phase]?.isActive != true) fetch(s, phase, null, AUTO_CONTINUE)
    }

    /** Al entrar, al volver a la app y al deslizar: recarga la pestaña visible desde el principio. */
    fun refresh() {
        val s = usableSession() ?: return
        val phase = mutable.value.selected
        if (jobs[phase]?.isActive == true) return
        fetch(s, phase, null, AUTO_CONTINUE)
    }

    /** Siguiente página o «seguir buscando» tras páginas vacías; una sola petición por acción del usuario. */
    fun loadMore() {
        val s = usableSession() ?: return
        val phase = mutable.value.selected
        val cursor = mutable.value.phases.getValue(phase).nextCursor ?: return
        if (jobs[phase]?.isActive == true) return
        fetch(s, phase, cursor, 0)
    }

    private fun usableSession(): BuyerOrdersSession? {
        val current = session ?: return null
        if (repository.isCurrent(current)) return current
        jobs.values.forEach { it.cancel() }; jobs.clear(); BuyerOrderPhase.entries.forEach { bump(it) }
        session = null
        mutable.value = BuyerOrdersUiState(signedIn = false, selected = mutable.value.selected)
        return null
    }

    private fun bump(phase: BuyerOrderPhase): Long = (tickets.getOrElse(phase) { 0L } + 1).also { tickets[phase] = it }
    private fun update(phase: BuyerOrderPhase, change: (BuyerOrdersPhaseState) -> BuyerOrdersPhaseState) =
        mutable.update { it.copy(phases = it.phases + (phase to change(it.phases.getValue(phase)))) }

    /** [autoContinue]: páginas vacías con cursor que se siguen solas cuando aún no hay resultados (acotado). */
    private fun fetch(expected: BuyerOrdersSession, phase: BuyerOrderPhase, cursor: String?, autoContinue: Int, resetOnInvalid: Boolean = true) {
        jobs[phase]?.cancel()
        val ticket = bump(phase)
        update(phase) { if (cursor == null) it.copy(loading = true, loadingMore = false, message = null) else it.copy(loadingMore = true, message = null) }
        jobs[phase] = viewModelScope.launch {
            val result = try { repository.page(expected, phase, cursor) } catch (e: CancellationException) { throw e } catch (_: Exception) { BuyerOrdersResult.Unavailable }
            currentCoroutineContext().ensureActive()
            if (tickets[phase] != ticket || session != expected) return@launch
            if (!repository.isCurrent(expected)) { usableSession(); return@launch }
            when (result) {
                is BuyerOrdersResult.Loaded -> {
                    val previous = if (cursor == null) emptyList() else mutable.value.phases.getValue(phase).orders
                    if (previous.any { old -> result.page.orders.any { it.orderId == old.orderId } }) {
                        update(phase) { it.copy(loading = false, loadingMore = false, nextCursor = null, message = "La lista cambió. Desliza para actualizar.") }
                        return@launch
                    }
                    val merged = previous + result.page.orders
                    update(phase) { BuyerOrdersPhaseState(merged, result.page.nextCursor, loaded = true) }
                    if (merged.isEmpty() && result.page.nextCursor != null && autoContinue > 0)
                        fetch(expected, phase, result.page.nextCursor, autoContinue - 1)
                }
                BuyerOrdersResult.InvalidCursor ->
                    if (resetOnInvalid) fetch(expected, phase, null, AUTO_CONTINUE, resetOnInvalid = false)
                    else update(phase) { it.copy(loading = false, loadingMore = false, nextCursor = null, message = "La lista cambió. Desliza para actualizar.") }
                BuyerOrdersResult.AuthenticationRequired ->
                    update(phase) { it.copy(loading = false, loadingMore = false, message = "Vuelve a iniciar sesión para ver tus pedidos.") }
                BuyerOrdersResult.StaleSession -> usableSession()
                BuyerOrdersResult.Unavailable ->
                    update(phase) { it.copy(loading = false, loadingMore = false, message = "No pudimos cargar tus pedidos. Comprueba la conexión y vuelve a intentarlo.") }
            }
        }
    }

    companion object {
        /** Páginas vacías seguidas automáticamente antes de pedir confirmación al usuario (cada una lee como máximo 200 documentos). */
        const val AUTO_CONTINUE = 2
    }
}
