package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.StoreAppeal
import com.chaskifood.app.feature.business.domain.StoreAppealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreAppealsViewModel @Inject constructor(
    private val repository: StoreAppealRepository,
) : ViewModel() {
    private val _appeals = MutableStateFlow<ApiResult<List<StoreAppeal>>?>(null)
    val appeals = _appeals.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var observation: Job? = null

    fun load(admin: Boolean) {
        stopObserving()
        observation = viewModelScope.launch {
            launch { repository.observeAppeals(admin).collect { _appeals.value = it } }
        }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
        _appeals.value = null
    }

    fun clearMessage() { _message.value = null }

    fun submit(id: String, text: String, done: () -> Unit) = act(
        "Apelación enviada. Tu local seguirá suspendido hasta la decisión administrativa.", done,
    ) { repository.submit(id, text) }

    fun resolve(id: String, accepted: Boolean, text: String, done: () -> Unit) = act(
        if (accepted) "Apelación aceptada y local reactivado." else "Apelación rechazada. Se avisó al dueño.", done,
    ) { repository.resolve(id, accepted, text) }

    private fun act(successMessage: String, done: () -> Unit, operation: suspend () -> ApiResult<Unit>) {
        if (_busy.value) return
        _busy.value = true
        clearMessage()
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is ApiResult.Success -> {
                        _message.value = successMessage
                        done()
                    }
                    is ApiResult.Failure -> _message.value = result.message ?: "No se pudo guardar. Reintenta."
                }
            } finally {
                _busy.value = false
            }
        }
    }
}
