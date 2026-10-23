package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.BusinessSuspensionResult
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.business.domain.StoreAppeal
import com.chaskifood.app.feature.business.domain.StoreAppealRepository
import com.chaskifood.app.feature.business.testing.BusinessTestAuth
import com.chaskifood.app.feature.business.testing.BusinessTestRepository
import com.chaskifood.app.feature.business.testing.OperationTestStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBusinessSupervisionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val businesses = BusinessTestRepository()
    private val stores = OperationTestStores()
    private val auth = BusinessTestAuth()
    private val approved = BusinessRequest(id = "business", status = BusinessStatus.APPROVED)
    private val appealUpdates = MutableStateFlow<ApiResult<List<StoreAppeal>>>(ApiResult.Success(emptyList()))
    private val appeals = object : StoreAppealRepository {
        override fun observeAppeals(admin: Boolean) = appealUpdates
        override suspend fun submit(storeId: String, explanation: String) = ApiResult.Success(Unit)
        override suspend fun resolve(id: String, accepted: Boolean, response: String) = ApiResult.Success(Unit)
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        businesses.requests.value = ApiResult.Success(listOf(approved))
    }

    @After fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.model() = AdminBusinessSupervisionViewModel(businesses, auth, stores, appeals).also { model ->
        viewModelStore.put("supervision", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.filteredRequests.collect {} }
        runCurrent()
    }

    @Test fun `appeal indicator updates when a new pending appeal arrives`() = runTest {
        val model = model()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.pendingAppeals.collect {} }
        appealUpdates.value = ApiResult.Success(listOf(
            StoreAppeal(id = "one", status = "PENDING"),
            StoreAppeal(id = "two", status = "ACCEPTED"),
        ))
        runCurrent()
        assertEquals(1, (model.pendingAppeals.value as ApiResult.Success).data)
    }

    @Test fun `suspension verificada cierra el dialogo y presenta exito`() = runTest {
        val model = model()
        var closed = 0

        model.suspendBusiness(approved, "Motivo") { closed++ }
        runCurrent()

        assertEquals(1, businesses.suspensions)
        assertEquals(1, closed)
        assertEquals(UiState.Success(Unit), model.actionState.value)
    }

    @Test fun `commit sin verificacion cierra el dialogo y conserva la advertencia`() = runTest {
        businesses.suspensionResult = ApiResult.Success(BusinessSuspensionResult.COMMITTED_UNVERIFIED)
        val model = model()
        var closed = 0

        model.suspendBusiness(approved, "Motivo") { closed++ }
        runCurrent()

        assertEquals(1, closed)
        val warning = model.actionState.value as UiState.Error
        assertTrue(warning.message.orEmpty().contains("quedó suspendido"))
        businesses.requests.value = ApiResult.Success(emptyList())
        model.selectedFilter.value = BusinessStatus.SUSPENDED
        runCurrent()
        assertEquals(warning, model.actionState.value)
        assertEquals(1, businesses.suspensions)
    }

    @Test fun `commit con locales activos cierra el dialogo y pide revision`() = runTest {
        businesses.suspensionResult = ApiResult.Success(BusinessSuspensionResult.COMMITTED_ACTIVE_STORES)
        val model = model()
        var closed = 0

        model.suspendBusiness(approved, "Motivo") { closed++ }
        runCurrent()

        assertEquals(1, closed)
        val warning = model.actionState.value as UiState.Error
        assertTrue(warning.message.orEmpty().contains("locales activos"))
        businesses.requests.value = ApiResult.Success(emptyList())
        model.selectedFilter.value = BusinessStatus.SUSPENDED
        runCurrent()
        assertEquals(warning, model.actionState.value)
        assertEquals(1, businesses.suspensions)
    }

    @Test fun `error previo no cierra el dialogo ni reintenta automaticamente`() = runTest {
        businesses.suspensionResult = ApiResult.Failure("Permiso denegado")
        val model = model()
        var closed = 0

        model.suspendBusiness(approved, "Motivo") { closed++ }
        runCurrent()

        assertEquals(0, closed)
        assertEquals("Permiso denegado", (model.actionState.value as UiState.Error).message)
        businesses.requests.value = ApiResult.Success(emptyList())
        runCurrent()
        assertEquals(1, businesses.suspensions)
    }

    @Test fun `resultado incierto del SDK pide consultar el estado antes de repetir`() = runTest {
        businesses.suspensionResult = ApiResult.Failure(
            "No se pudo confirmar si la suspensión se aplicó. Actualiza el negocio y sus locales antes de repetir la operación."
        )
        val model = model()
        var closed = 0

        model.suspendBusiness(approved, "Motivo") { closed++ }
        runCurrent()

        assertEquals(0, closed)
        assertTrue((model.actionState.value as UiState.Error).message.orEmpty().contains("antes de repetir"))
        assertEquals(1, businesses.suspensions)
    }

    @Test fun `suspension individual sigue enviando el estado administrativo`() = runTest {
        val model = model()

        model.changeStoreStatus(BusinessStore(id = "store", status = StoreStatus.ACTIVE), "Motivo")
        runCurrent()

        assertEquals(1, stores.administrativeStatusCalls)
        assertEquals(StoreStatus.SUSPENDED, stores.lastAdministrativeStatus)
        assertEquals(UiState.Success(Unit), model.actionState.value)
    }
}
