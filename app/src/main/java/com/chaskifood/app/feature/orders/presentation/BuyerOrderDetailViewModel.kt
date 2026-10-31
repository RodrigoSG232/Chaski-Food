package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.*
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BuyerOrderDetailUiState(
    val loading: Boolean = true, val detail: BuyerOrderDetail? = null, val notFound: Boolean = false,
    val signedIn: Boolean = true, val message: String? = null, val cancelling: Boolean = false, val notice: String? = null,
)

@HiltViewModel
class BuyerOrderDetailViewModel @Inject constructor(private val repository: BuyerOrdersRepository, savedState: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(BuyerOrderDetailUiState())
    val state = mutable.asStateFlow()
    private val orderId: String? = savedState.get<String>("orderId")?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    private var session: BuyerOrdersSession? = null
    private var job: Job? = null
    private var cancellationJob: Job? = null
    private var ticket = 0L

    init {
        if (orderId == null) mutable.value = BuyerOrderDetailUiState(loading = false, notFound = true)
        else viewModelScope.launch {
            repository.observeSession().catch { e -> if (e is CancellationException) throw e; emit(null) }.collect { next ->
                if (next == session) return@collect
                job?.cancel(); cancellationJob?.cancel(); ticket++; session = next
                if (next == null) mutable.value = BuyerOrderDetailUiState(loading = false, signedIn = false)
                else load(next, orderId)
            }
        }
    }

    /** Al volver a la pantalla o al deslizar; nunca reutiliza una respuesta de otra sesión. */
    fun refresh() {
        val id = orderId ?: return
        val current = session ?: return
        if (job?.isActive == true || mutable.value.cancelling) return
        if (!repository.isCurrent(current)) { session = null; mutable.value = BuyerOrderDetailUiState(loading = false, signedIn = false); return }
        load(current, id)
    }

    fun cancel() {
        val id = orderId ?: return
        val expected = session ?: return
        val detail = mutable.value.detail ?: return
        if (mutable.value.cancelling || detail.summary.status != "PENDING_CONFIRMATION") return
        val mine = ++ticket
        job?.cancel()
        mutable.update { it.copy(cancelling = true, message = null, notice = null) }
        cancellationJob = viewModelScope.launch {
            val result = try { repository.cancel(expected, id) } catch (e: CancellationException) { throw e }
                catch (_: Exception) { BuyerOrderCancellationResult.Unavailable }
            currentCoroutineContext().ensureActive()
            if (mine != ticket || session != expected) return@launch
            mutable.update { it.copy(cancelling = false) }
            when (result) {
                BuyerOrderCancellationResult.Cancelled -> { load(expected, id); mutable.update { it.copy(notice = "Pedido cancelado.") } }
                BuyerOrderCancellationResult.NotAllowed -> { load(expected, id); mutable.update { it.copy(notice = "El estado del pedido cambió. Ya no se puede cancelar desde aquí.") } }
                BuyerOrderCancellationResult.NotFound -> mutable.update { it.copy(detail = null, notFound = true) }
                BuyerOrderCancellationResult.AuthenticationRequired -> mutable.update { it.copy(message = "Vuelve a iniciar sesión.") }
                BuyerOrderCancellationResult.StaleSession -> { session = null; mutable.value = BuyerOrderDetailUiState(loading = false, signedIn = false) }
                BuyerOrderCancellationResult.Unavailable -> mutable.update { it.copy(message = "No pudimos procesar la cancelación. Comprueba la conexión y vuelve a intentarlo.") }
            }
        }
    }

    private fun load(expected: BuyerOrdersSession, id: String) {
        val mine = ++ticket
        mutable.update { it.copy(loading = true, message = null) }
        job = viewModelScope.launch {
            val result = try { repository.detail(expected, id) } catch (e: CancellationException) { throw e } catch (_: Exception) { BuyerOrderDetailResult.Unavailable }
            currentCoroutineContext().ensureActive()
            if (mine != ticket || session != expected) return@launch
            mutable.value = when (result) {
                is BuyerOrderDetailResult.Loaded -> BuyerOrderDetailUiState(loading = false, detail = result.detail, notice = mutable.value.notice)
                BuyerOrderDetailResult.NotFound -> BuyerOrderDetailUiState(loading = false, notFound = true)
                BuyerOrderDetailResult.AuthenticationRequired -> mutable.value.copy(loading = false, message = "Vuelve a iniciar sesión para ver este pedido.")
                BuyerOrderDetailResult.StaleSession -> { session = null; BuyerOrderDetailUiState(loading = false, signedIn = false) }
                BuyerOrderDetailResult.Unavailable -> mutable.value.copy(loading = false, message = "No pudimos cargar el pedido. Comprueba la conexión y vuelve a intentarlo.")
            }
        }
    }
}
