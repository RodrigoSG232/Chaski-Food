package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.business.testing.BusinessTestAuth
import com.chaskifood.app.feature.business.testing.OperationTestStores
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreOperationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repo = OperationTestStores()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model() = StoreOperationsViewModel(repo, BusinessTestAuth()).also { store.put("operations", it) }

    @Test fun `doble toque y horario simultaneo del mismo local envian una sola operacion`() = runTest {
        repo.gates["a"] = CompletableDeferred()
        val vm = model(); runCurrent()
        vm.updateStatus("a", OperationalStatus.PAUSED, "  Sin insumos  ")
        vm.updateStatus("a", OperationalStatus.CLOSED)
        vm.updateHours("a", defaultWeeklyOperatingHours())
        assertTrue(vm.operations.value.getValue("a").saving)
        runCurrent()
        assertEquals(1, repo.calls)
        assertEquals("Sin insumos", repo.savedReason)
        assertEquals(0L, vm.operations.value.getValue("a").successRevision)
        repo.gates.getValue("a").complete(Unit); runCurrent()
        assertFalse(vm.operations.value.getValue("a").saving)
        assertEquals(1L, vm.operations.value.getValue("a").successRevision)
    }
    @Test fun `un local guardando no bloquea otros locales`() = runTest {
        repo.gates["a"] = CompletableDeferred()
        val vm = model(); runCurrent()
        vm.updateStatus("a", OperationalStatus.CLOSED)
        vm.updateHours("b", defaultWeeklyOperatingHours()); runCurrent()
        assertEquals(2, repo.calls)
        assertTrue(vm.operations.value.getValue("a").saving)
        assertFalse(vm.operations.value.getValue("b").saving)
        repo.gates.getValue("a").complete(Unit); runCurrent()
    }
    @Test fun `fallo de pausa no genera exito y permite reintentar el mismo motivo`() = runTest {
        repo.result = ApiResult.Failure("Sin conexión")
        val vm = model(); runCurrent()
        vm.updateStatus("a", OperationalStatus.PAUSED, "Sin insumos"); runCurrent()
        assertEquals("Sin conexión", vm.operations.value.getValue("a").error)
        assertEquals(0L, vm.operations.value.getValue("a").successRevision)
        assertFalse(vm.operations.value.getValue("a").saving)
        repo.result = ApiResult.Success(Unit)
        vm.updateStatus("a", OperationalStatus.PAUSED, "Sin insumos"); runCurrent()
        assertEquals(2, repo.calls)
        assertNull(vm.operations.value.getValue("a").error)
        assertEquals(1L, vm.operations.value.getValue("a").successRevision)
    }
    @Test fun `error sin mensaje y excepcion inesperada tienen aviso y desbloquean el local`() = runTest {
        val vm = model(); runCurrent()
        repo.result = ApiResult.Failure(null)
        vm.updateHours("a", defaultWeeklyOperatingHours()); runCurrent()
        assertNotNull(vm.operations.value.getValue("a").error)
        repo.exception = IllegalStateException("Fallo")
        vm.updateHours("a", defaultWeeklyOperatingHours()); runCurrent()
        assertNotNull(vm.operations.value.getValue("a").error)
        assertFalse(vm.operations.value.getValue("a").saving)
    }
    @Test fun `horario invalido no escribe y el error pertenece al horario de ese local`() = runTest {
        val vm = model(); runCurrent()
        vm.updateHours("a", defaultWeeklyOperatingHours().map { it.copy(openTime = "25:00") })
        assertEquals(0, repo.calls)
        assertEquals(StoreOperation.HOURS, vm.operations.value.getValue("a").operation)
        assertNotNull(vm.operations.value.getValue("a").error)
    }
    @Test fun `un local ajeno no se envia al repositorio`() = runTest {
        val vm = model(); runCurrent()
        vm.updateStatus("ajeno", OperationalStatus.CLOSED); runCurrent()
        assertEquals(0, repo.calls)
        assertNotNull(vm.operations.value.getValue("ajeno").error)
    }
    @Test fun `borrador de horario enviado es estable mientras guarda`() = runTest {
        repo.gates["a"] = CompletableDeferred()
        val vm = model(); runCurrent()
        val draft = defaultWeeklyOperatingHours().toMutableList()
        vm.updateHours("a", draft)
        draft[0] = draft[0].copy(openTime = "12:00")
        runCurrent()
        assertEquals("08:00", repo.savedHours!![0].openTime)
        repo.gates.getValue("a").complete(Unit); runCurrent()
    }
}
