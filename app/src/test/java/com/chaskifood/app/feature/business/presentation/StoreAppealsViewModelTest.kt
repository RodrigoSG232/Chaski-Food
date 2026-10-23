package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.StoreAppeal
import com.chaskifood.app.feature.business.domain.StoreAppealRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreAppealsViewModelTest {
    @Test fun `double tap sends once and failure keeps editor open for retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = AppealsFake()
        val model = StoreAppealsViewModel(repository)
        var editorClosed = 0
        try {
            repeat(2) { model.submit("store", "Correcciones realizadas") { editorClosed++ } }
            runCurrent()
            assertEquals(1, repository.writes)
            assertTrue(model.busy.value)
            repository.result.complete(ApiResult.Failure("Sin conexión"))
            runCurrent()
            assertEquals(0, editorClosed)
            assertFalse(model.busy.value)
            assertEquals("Sin conexión", model.message.value)
            repository.result = CompletableDeferred(ApiResult.Success(Unit))
            model.submit("store", "Correcciones realizadas") { editorClosed++ }
            runCurrent()
            assertEquals(2, repository.writes)
            assertEquals(1, editorClosed)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun `closing panel releases appeal listener`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = AppealsFake()
        val model = StoreAppealsViewModel(repository)
        try {
            model.load(false)
            runCurrent()
            assertEquals(1, repository.appeals.subscriptionCount.value)
            model.stopObserving()
            runCurrent()
            assertEquals(0, repository.appeals.subscriptionCount.value)
            assertNull(model.appeals.value)
            model.load(true)
            runCurrent()
            assertEquals(1, repository.appeals.subscriptionCount.value)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class AppealsFake : StoreAppealRepository {
        val appeals = MutableStateFlow<ApiResult<List<StoreAppeal>>>(ApiResult.Success(emptyList()))
        var result = CompletableDeferred<ApiResult<Unit>>()
        var writes = 0
        override fun observeAppeals(admin: Boolean) = appeals
        override suspend fun submit(storeId: String, explanation: String): ApiResult<Unit> {
            writes++
            return result.await()
        }
        override suspend fun resolve(id: String, accepted: Boolean, response: String) = result.await()
    }
}
