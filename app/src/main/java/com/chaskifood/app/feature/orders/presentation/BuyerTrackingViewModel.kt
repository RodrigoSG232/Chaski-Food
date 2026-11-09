package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class BuyerTrackingUiState(
    val loading: Boolean = true, val tracking: BuyerTracking? = null, val message: String? = null,
    val receivedAt: TimeMark? = null,
)

/** Poll only while resumed. A generation change or authorization failure discards all private samples. */
@HiltViewModel
class BuyerTrackingViewModel @Inject constructor(
    private val repository: BuyerOrdersRepository, savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(BuyerTrackingUiState())
    val state = mutable.asStateFlow()
    private val orderId = savedState.get<String>("orderId")?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    private var session: BuyerOrdersSession? = null
    private var visible = false
    private var poll: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            repository.observeSession().catch { e -> if (e is CancellationException) throw e; emit(null) }.collect { next ->
                if (session == next) return@collect
                stopPolling()
                session = next
                mutable.value = BuyerTrackingUiState(loading = next != null)
                if (visible) startPolling()
            }
        }
    }

    fun resume() { visible = true; startPolling() }
    fun pause() { visible = false; stopPolling() }
    fun refresh() { if (visible) { stopPolling(); startPolling() } }

    private fun stopPolling() { generation++; poll?.cancel(); poll = null }

    private fun startPolling() {
        val expected = session ?: return
        val id = orderId ?: return
        if (!visible || poll?.isActive == true) return
        val mine = ++generation
        poll = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                if (!repository.isCurrent(expected)) {
                    mutable.value = BuyerTrackingUiState(false, message = "Vuelve a iniciar sesión para consultar el seguimiento.")
                    return@launch
                }
                val result = try { repository.tracking(expected, id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { BuyerTrackingResult.Unavailable }
                ensureActive()
                if (mine != generation || expected != session) return@launch
                if (!repository.isCurrent(expected)) {
                    mutable.value = BuyerTrackingUiState(false, message = "La sesión cambió.")
                    return@launch
                }
                when (result) {
                    is BuyerTrackingResult.Loaded -> {
                        failures = 0
                        mutable.value = BuyerTrackingUiState(false, result.tracking, receivedAt = TimeSource.Monotonic.markNow())
                        if (result.tracking.terminal) return@launch
                    }
                    BuyerTrackingResult.Unavailable -> {
                        failures++
                        mutable.update { it.copy(loading = false,
                            message = "No pudimos actualizar el seguimiento. La información mostrada es la última recibida.") }
                    }
                    BuyerTrackingResult.NotFound -> {
                        mutable.value = BuyerTrackingUiState(false, message = "El seguimiento no está disponible para esta cuenta.")
                        return@launch
                    }
                    BuyerTrackingResult.AuthenticationRequired, BuyerTrackingResult.StaleSession -> {
                        mutable.value = BuyerTrackingUiState(false, message = "Vuelve a iniciar sesión para consultar el seguimiento.")
                        return@launch
                    }
                }
                delay(if (failures == 0) 10_000 else if (failures == 1) 20_000 else 30_000)
            }
        }
    }
}
