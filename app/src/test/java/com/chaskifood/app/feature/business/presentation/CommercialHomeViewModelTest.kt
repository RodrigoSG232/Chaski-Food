package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.business.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommercialHomeViewModelTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class Driver : AccessIdentityDriver {
        val events = MutableSharedFlow<IdentityObservation>(extraBufferCapacity = 10)
        override fun observe(): Flow<IdentityObservation> = events
        override suspend fun refresh() = Unit
        override fun runIfCurrent(state: AccessIdentityState, publish: () -> Unit): Boolean { publish(); return true }
        suspend fun login(uid: String = "owner", key: String = "login", revision:Long=0) { events.emit(IdentityObservation.Authenticated(key, uid, "$uid@example.test", EmailVerification.VERIFIED, credentialRevision=revision)) }
    }
    private class Fixture(val test: TestScope, repository: BusinessRepository? = null) {
        val driver = Driver()
        val source = SharedAccessIdentitySource(driver, test.backgroundScope)
        var status = AccessBusinessStatus.APPROVED
        var manager = false
        var assignedIds = listOf("a", "b")
        var inactive = false
        val reader = object : AccessFactsReader {
            override suspend fun read(identity: AccessIdentityState): AccessFactsResult {
                val uid = (identity.identity as AccessIdentity.Authenticated).session
                val assignments = if (manager) assignedIds.map { AccessAssignment("business", it, uid.uid, "${uid.uid}@example.test", AccessBinding.VERIFIED) } else emptyList()
                val stores = if (manager || status == AccessBusinessStatus.APPROVED) assignedIds.map {
                    AccessStore("business", it, AccessBusinessStatus.APPROVED, if (inactive) AccessStoreStatus.INACTIVE else AccessStoreStatus.ACTIVE, AccessOperationalStatus.OPEN)
                } else emptyList()
                return AccessFactsResult(AccessFacts.Checked(uid, if (manager) null else AccessOwnership("business", uid.uid, status), assignments, stores, AccessExclusivity.VERIFIED), 1)
            }
        }
        val context = SharedAccessContext(source, reader, test.backgroundScope, true, 60_000)
        val business = BusinessTestRepository()
        val stores = OperationTestStores()
        val vm = BusinessViewModel(repository ?: business, BusinessTestAuth(), stores, context)
        val store = ViewModelStore().also { it.put("vm", vm) }
        suspend fun start() {
            business.existing = BusinessRequest(id = "business", ownerUid = "owner", businessName = "Real test business", status = BusinessStatus.valueOf(status.name))
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { vm.commercialHome.collect {} }
            test.runCurrent(); driver.login(); test.runCurrent()
        }
        fun clear() { store.clear() }
    }
    @Test fun `propietario sin locales conserva catalogo y no inventa datos`() = runTest {
        val f = Fixture(this); f.start()
        val state = f.vm.commercialHome.value as CommercialHomeState.Owner
        assertTrue(state.stores!!.isEmpty()); assertTrue(state.canManageCatalog); assertTrue(state.canManageStores)
        assertEquals("Real test business", state.business.businessName); assertEquals(1, f.stores.ownerQueries)
        f.clear()
    }
    @Test fun `suspendido conserva identidad pero no consulta locales`() = runTest {
        val f = Fixture(this); f.status = AccessBusinessStatus.SUSPENDED; f.start()
        val state = f.vm.commercialHome.value as CommercialHomeState.Owner
        assertTrue(state.suspended); assertFalse(state.canOperate); assertFalse(state.canManageStores)
        assertEquals(0, f.stores.ownerQueries); assertEquals(0, f.stores.managerQueries)
        f.clear()
    }
    @Test fun `responsable filtra locales ajenos y cambia seleccion sin otra consulta`() = runTest {
        val f = Fixture(this); f.manager = true
        f.stores.assigned.value = ApiResult.Success(listOf(BusinessStore(id="a",businessId="business"),BusinessStore(id="b",businessId="business"),BusinessStore(id="foreign",businessId="other")))
        f.start(); f.vm.selectCommercialStore("b"); runCurrent()
        val state = f.vm.commercialHome.value as CommercialHomeState.Manager
        assertEquals(listOf("a","b"),state.locals.map { it.store.id }); assertEquals("b",state.selectedId)
        f.vm.selectCommercialStore("foreign"); runCurrent(); assertEquals("b",(f.vm.commercialHome.value as CommercialHomeState.Manager).selectedId)
        assertEquals(1,f.stores.managerQueries); assertEquals(0,f.stores.ownerQueries)
        f.clear()
    }
    @Test fun `inactivo no equivale a asignacion revocada`() = runTest {
        val f = Fixture(this); f.manager = true; f.inactive = true
        f.stores.assigned.value = ApiResult.Success(listOf(BusinessStore(id="a",businessId="business",status=StoreStatus.INACTIVE)))
        f.start(); val state = f.vm.commercialHome.value as CommercialHomeState.Manager
        assertEquals("a",state.selectedId); assertFalse(state.locals.single().canOperate)
        f.clear()
    }
    @Test fun `revocar seleccionado borra datos anteriores y no permite seleccionarlo`() = runTest {
        val f = Fixture(this); f.manager = true
        f.stores.assigned.value = ApiResult.Success(listOf(BusinessStore(id="a",businessId="business"),BusinessStore(id="b",businessId="business")))
        f.start(); f.vm.selectCommercialStore("b"); runCurrent()
        f.assignedIds = listOf("a"); f.driver.login(revision=1); runCurrent()
        val state = f.vm.commercialHome.value as CommercialHomeState.Manager
        assertEquals(listOf("a"),state.locals.map { it.store.id }); assertEquals("a",state.selectedId); assertTrue(state.permissionsChanged)
        f.vm.selectCommercialStore("b"); runCurrent(); assertEquals("a",(f.vm.commercialHome.value as CommercialHomeState.Manager).selectedId)
        f.clear()
    }
    @Test fun `logout y reingreso mismo UID elimina estado y cambia origen`() = runTest {
        val f = Fixture(this); f.start(); val origin=(f.vm.commercialHome.value as CommercialHomeState.Owner).origin
        f.driver.events.emit(IdentityObservation.SignedOut); runCurrent(); assertEquals(CommercialHomeState.Loading,f.vm.commercialHome.value)
        f.driver.login(key="again"); runCurrent(); assertNotEquals(origin,(f.vm.commercialHome.value as CommercialHomeState.Owner).origin)
        f.clear()
    }
    @Test fun `fallo de locales no se representa como negocio vacio`() = runTest {
        val f = Fixture(this); f.stores.owned.value = ApiResult.Failure("failure"); f.start()
        val state=f.vm.commercialHome.value as CommercialHomeState.Owner
        assertNull(state.stores); assertNotNull(state.storeError); f.clear()
    }
    @Test fun `horario independiente no concede disponibilidad ni autorizacion`() {
        val store=BusinessStore(status=StoreStatus.INACTIVE, operationalStatus=OperationalStatus.CLOSED,
            operatingHours=listOf(DayOperatingHours(DayOfWeekEnum.MONDAY,"22:00","02:00")))
        assertTrue(store.isWithinOperatingHours("01:00",DayOfWeekEnum.TUESDAY))
        assertFalse(store.isAvailableForOrders("01:00",DayOfWeekEnum.TUESDAY))
        assertFalse(store.isWithinOperatingHours("03:00",DayOfWeekEnum.TUESDAY))
    }
    @Test fun `respuesta y error tardios de otra cuenta no reemplazan el negocio actual`() = runTest {
        val first=MutableSharedFlow<ApiResult<BusinessRequest?>>(replay=1)
        val second=MutableStateFlow<ApiResult<BusinessRequest?>>(ApiResult.Success(BusinessRequest(id="business",ownerUid="second",businessName="Segundo negocio",status=BusinessStatus.APPROVED)))
        val repository=object : BusinessRepository by BusinessTestRepository() {
            override fun getBusinessRequest(ownerUid:String):Flow<ApiResult<BusinessRequest?>> = if(ownerUid=="owner")first else second
        }
        val f=Fixture(this,repository);f.start();assertEquals(CommercialHomeState.Loading,f.vm.commercialHome.value)
        f.driver.login("second","other-login");runCurrent()
        assertEquals("Segundo negocio",(f.vm.commercialHome.value as CommercialHomeState.Owner).business.businessName)
        assertEquals(0,first.subscriptionCount.value)
        first.emit(ApiResult.Success(BusinessRequest(id="business",ownerUid="owner",businessName="Antiguo",status=BusinessStatus.APPROVED)))
        first.emit(ApiResult.Failure("Error anterior"));runCurrent()
        assertEquals("Segundo negocio",(f.vm.commercialHome.value as CommercialHomeState.Owner).business.businessName)
        f.clear();runCurrent();assertEquals(0,second.subscriptionCount.value)
    }
    @Test fun `reloj local y seleccion no multiplican observadores remotos`() = runTest {
        val f=Fixture(this);f.manager=true
        f.stores.assigned.value=ApiResult.Success(listOf(BusinessStore(id="a",businessId="business"),BusinessStore(id="b",businessId="business")))
        f.start();advanceTimeBy(30_001);runCurrent();f.vm.selectCommercialStore("b");runCurrent()
        assertEquals(1,f.stores.managerQueries);assertEquals("b",(f.vm.commercialHome.value as CommercialHomeState.Manager).selectedId)
        f.clear()
    }}