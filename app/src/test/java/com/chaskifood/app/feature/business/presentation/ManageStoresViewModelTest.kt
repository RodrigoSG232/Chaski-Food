package com.chaskifood.app.feature.business.presentation

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.*
import com.chaskifood.app.feature.business.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageStoresViewModelTest {
    @Test fun `doble toque crea un solo local y confirma una sola vez`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores().apply { saveGate = CompletableDeferred() }
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        var confirmations = 0
        try {
            runCurrent()
            repeat(2) {
                model.saveStore("", "owner", "Local", "Dirección", "-12", "-77", "9999999", StoreStatus.ACTIVE) { confirmations++ }
            }
            runCurrent()
            assertEquals(1, stores.writeCount)
            assertTrue(model.saving.value)
            assertEquals(0, confirmations)
            stores.saveGate!!.complete(Unit)
            runCurrent()
            assertFalse(model.saving.value)
            assertEquals(1, confirmations)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `fallo de guardado conserva dialogo y permite reintentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores().apply { writeFailure = "Sin conexión" }
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        var confirmations = 0
        try {
            runCurrent()
            fun save() = model.saveStore("", "owner", "Local", "Dirección", "-12", "-77", "9999999", StoreStatus.ACTIVE) { confirmations++ }
            save(); runCurrent()
            assertEquals(0, confirmations)
            assertEquals("Sin conexión", model.actionMessage.value)
            assertFalse(model.saving.value)
            stores.writeFailure = null
            save(); runCurrent()
            assertEquals(1, confirmations)
            assertEquals(2, stores.writeCount)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun `editar datos conserva pausa horarios y fecha original`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores()
        val original = BusinessStore(id = "local", businessId = "owner", phone = "1111111",
            operationalStatus = OperationalStatus.PAUSED, pauseReason = "Sin insumos", createdAt = 123,
            operatingHours = defaultWeeklyOperatingHours().map { it.copy(openTime = "12:00", closeTime = "23:00") })
        stores.data.value = ApiResult.Success(listOf(original))
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        try {
            runCurrent()
            model.saveStore("local", "owner", "Nuevo nombre", "Nueva dirección", "-12", "-77", "9999999", StoreStatus.ACTIVE) {}
            runCurrent()
            val saved = requireNotNull(stores.saved)
            assertEquals("9999999", saved.phone)
            assertEquals(original.operationalStatus, saved.operationalStatus)
            assertEquals(original.pauseReason, saved.pauseReason)
            assertEquals(original.operatingHours, saved.operatingHours)
            assertEquals(original.createdAt, saved.createdAt)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `recargar no acumula observadores y fallo no se muestra como lista vacia`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores()
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        try {
            runCurrent()
            repeat(3) { model.loadData(); runCurrent() }
            assertEquals(1, stores.active)
            stores.data.value = ApiResult.Failure("Sin conexión")
            runCurrent()
            assertEquals(ManageStoresUiState.Error("Sin conexión"), model.uiState.value)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `local ajeno y coordenadas no finitas no se envian al repositorio`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores()
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        try {
            runCurrent()
            model.saveStore("ajeno", "owner", "Local", "Dirección", "-12", "-77", "9999999", StoreStatus.ACTIVE) {}
            model.saveStore("", "owner", "Local", "Dirección", "NaN", "-77", "9999999", StoreStatus.ACTIVE) {}
            runCurrent()
            assertNull(stores.saved)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `editar local suspendido no permite al propietario reactivarlo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stores = TestStores()
        stores.data.value = ApiResult.Success(listOf(BusinessStore(id = "local", businessId = "owner", status = StoreStatus.SUSPENDED)))
        val model = ManageStoresViewModel(stores, TestBusinesses(), TestAuth())
        try {
            runCurrent()
            model.saveStore("local", "owner", "Local", "Dirección", "-12", "-77", "9999999", StoreStatus.ACTIVE) {}
            runCurrent()
            assertEquals(StoreStatus.SUSPENDED, stores.saved?.status)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}

private class TestAuth : AuthRepository {
    override val currentUserFlow = MutableStateFlow<AuthUser?>(AuthUser(uid = "owner", email = "owner@example.com", displayName = "Owner"))
    override suspend fun loginWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): ApiResult<AuthUser> = error("unused")
    override suspend fun signInWithGoogle(idToken: String): ApiResult<AuthUser> = error("unused")
    override suspend fun sendPhoneVerificationCode(activity: Activity, phoneNumber: String): ApiResult<String> = error("unused")
    override suspend fun verifyPhoneCode(verificationId: String, code: String): ApiResult<Unit> = error("unused")
    override suspend fun sendPasswordResetEmail(email: String): ApiResult<Unit> = error("unused")
    override suspend fun updateProfile(displayName: String): ApiResult<Unit> = error("unused")
    override suspend fun signOut() { currentUserFlow.value = null }
}

private class TestBusinesses : BusinessRepository {
    override fun getBusinessRequest(ownerUid: String) = flowOf(ApiResult.Success<BusinessRequest?>(
        BusinessRequest(id = "owner", ownerUid = "owner", status = BusinessStatus.APPROVED)))
    override fun getAllBusinessRequests(): Flow<ApiResult<List<BusinessRequest>>> = error("unused")
    override suspend fun submitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> = error("unused")
    override suspend fun resubmitBusinessRequest(request: BusinessRequest): ApiResult<BusinessRequest> = error("unused")
    override suspend fun evaluateBusinessRequest(requestId: String, status: BusinessStatus, observations: String?, reviewerEmail: String): ApiResult<Unit> = error("unused")
    override suspend fun suspendBusiness(requestId: String, reason: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override suspend fun reactivateBusiness(requestId: String, adminEmail: String, businessName: String, ruc: String): ApiResult<Unit> = error("unused")
    override fun getAuditLogs(): Flow<ApiResult<List<AuditLog>>> = error("unused")
}

private class TestStores : StoreRepository {
    val data = MutableStateFlow<ApiResult<List<BusinessStore>>>(ApiResult.Success(emptyList()))
    var active = 0
    var saved: BusinessStore? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var writeCount = 0
    var writeFailure: String? = null
    override fun getStoresByBusiness(businessId: String) = flow {
        active++
        try { emitAll(data) } finally { active-- }
    }
    override fun getManagersByBusiness(businessId: String) = flowOf(ApiResult.Success(emptyList<StoreManager>()))
    private suspend fun write(store: BusinessStore): ApiResult<BusinessStore> {
        writeCount++
        saveGate?.await()
        writeFailure?.let { return ApiResult.Failure(it) }
        saved = store
        return ApiResult.Success(store)
    }
    override suspend fun createStore(store: BusinessStore) = write(store)
    override suspend fun updateStore(store: BusinessStore) = write(store)
    override suspend fun assignManager(manager: StoreManager): ApiResult<StoreManager> = error("unused")
    override fun getStoresForManager(managerEmailOrUid: String): Flow<ApiResult<List<BusinessStore>>> = error("unused")
    override suspend fun updateOperationalStatus(storeId: String, status: OperationalStatus, pauseReason: String?): ApiResult<Unit> = error("unused")
    override suspend fun updateOperatingHours(storeId: String, hours: List<DayOperatingHours>): ApiResult<Unit> = error("unused")
    override suspend fun setAdministrativeStatus(storeId: String, status: StoreStatus, reason: String?): ApiResult<Unit> = error("unused")
}
