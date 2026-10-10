package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.business.testing.BusinessTestAuth
import com.chaskifood.app.feature.business.testing.BusinessTestRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBusinessReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repo = BusinessTestRepository()
    private val auth = BusinessTestAuth()
    private val pending = BusinessRequest(id = "request", status = BusinessStatus.PENDING_REVIEW)
    @Before fun setUp() { Dispatchers.setMain(dispatcher); repo.requests.value = ApiResult.Success(listOf(pending)) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun TestScope.model() = AdminBusinessReviewViewModel(repo, auth).also { vm ->
        store.put("review", vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.filteredRequests.collect {} }
        runCurrent()
    }

    @Test fun `solicitudes evaluadas no ofrecen una nueva evaluacion ni escriben`() = runTest {
        val vm = model()
        for (status in BusinessStatus.entries.filter { !it.canBeEvaluated }) {
            repo.requests.value = ApiResult.Success(listOf(pending.copy(status = status))); runCurrent()
            vm.evaluateRequest("request", BusinessStatus.APPROVED) { fail("No debe guardar") }
            assertTrue(vm.actionState.value is UiState.Error)
        }
        assertEquals(0, repo.evaluations)
    }
    @Test fun `si cambia el estado antes del toque bloquea la accion de la tarjeta anterior`() = runTest {
        val vm = model()
        repo.requests.value = ApiResult.Success(listOf(pending.copy(status = BusinessStatus.APPROVED))); runCurrent()
        vm.evaluateRequest("request", BusinessStatus.REJECTED, "Motivo") {}
        assertEquals(0, repo.evaluations)
        assertTrue(vm.actionState.value is UiState.Error)
    }
    @Test fun `sin datos de una solicitud y con destino invalido no escribe`() = runTest {
        val vm = model()
        vm.evaluateRequest("ajena", BusinessStatus.APPROVED) {}
        vm.evaluateRequest("request", BusinessStatus.SUSPENDED, "Motivo") {}
        assertEquals(0, repo.evaluations)
    }
    @Test fun `motivo vacio no envia observacion ni rechazo`() = runTest {
        val vm = model()
        vm.evaluateRequest("request", BusinessStatus.OBSERVED, "  ") {}
        vm.evaluateRequest("request", BusinessStatus.REJECTED, null) {}
        assertEquals(0, repo.evaluations)
        assertTrue(vm.actionState.value is UiState.Error)
    }
    @Test fun `doble toque envia una sola evaluacion y conserva lista durante la carga`() = runTest {
        repo.gate = CompletableDeferred()
        val vm = model()
        var callbacks = 0
        repeat(2) { vm.evaluateRequest("request", BusinessStatus.APPROVED) { callbacks++ } }
        runCurrent()
        assertEquals(1, repo.evaluations)
        assertTrue(vm.actionState.value is UiState.Loading)
        assertTrue(vm.filteredRequests.value is ApiResult.Success)
        assertEquals("owner@example.com", repo.reviewer)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(1, callbacks)
    }
    @Test fun `error mantiene accion recuperable y el reintento confirma solo tras exito`() = runTest {
        repo.result = ApiResult.Failure("Sin conexión")
        val vm = model()
        var callbacks = 0
        vm.evaluateRequest("request", BusinessStatus.OBSERVED, "Motivo") { callbacks++ }; runCurrent()
        assertEquals(0, callbacks)
        assertTrue(vm.actionState.value is UiState.Error)
        repo.result = ApiResult.Success(Unit)
        vm.evaluateRequest("request", BusinessStatus.OBSERVED, "Motivo") { callbacks++ }; runCurrent()
        assertEquals(1, callbacks)
        assertEquals(2, repo.evaluations)
    }
    @Test fun `sesion cerrada no envia evaluacion con identidad desconocida`() = runTest {
        val vm = model()
        auth.currentUserFlow.value = null
        vm.evaluateRequest("request", BusinessStatus.APPROVED) {}; runCurrent()
        assertEquals(0, repo.evaluations)
        assertTrue(vm.actionState.value is UiState.Error)
    }
}
