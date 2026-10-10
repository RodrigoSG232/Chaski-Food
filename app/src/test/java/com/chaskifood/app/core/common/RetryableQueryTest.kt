package com.chaskifood.app.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryableQueryTest {
    @Test fun `reintento elimina listener fallido y vuelve a cargar resultados`() = runTest {
        val retries = MutableStateFlow(0)
        val received = mutableListOf<ApiResult<List<String>>?>()
        var subscriptions = 0
        var closed = 0
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            retryableQuery(retries) {
                flow {
                    subscriptions++
                    try {
                        emit(if (subscriptions == 1) ApiResult.Failure("Sin permisos") else ApiResult.Success(listOf("negocio")))
                        awaitCancellation()
                    } finally { closed++ }
                }
            }.collect { received += it }
        }
        runCurrent()
        assertTrue(received.last() is ApiResult.Failure)
        retries.value++
        runCurrent()
        assertEquals(2, subscriptions)
        assertEquals(1, closed)
        assertEquals(listOf(null, ApiResult.Failure("Sin permisos"), null, ApiResult.Success(listOf("negocio"))), received)
        collector.cancel()
        runCurrent()
        assertEquals(2, closed)
    }

    @Test fun `excepcion de carga se muestra y reintento sigue disponible`() = runTest {
        val retries = MutableStateFlow(0)
        var result: ApiResult<String>? = null
        var attempts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            retryableQuery(retries) {
                flow { if (++attempts == 1) error("Network failure") else emit(ApiResult.Success("loaded")) }
            }.collect { result = it }
        }
        runCurrent()
        assertTrue(result is ApiResult.Failure)
        retries.value++
        runCurrent()
        assertEquals(ApiResult.Success("loaded"), result)
    }
}
