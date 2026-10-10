package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.feature.business.testing.BusinessTestAuth
import com.chaskifood.app.feature.business.testing.BusinessTestRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterBusinessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repo = BusinessTestRepository()
    private val normalizer = object : BusinessPhoneNormalizer {
        override fun toInternational(dialCode: String, nationalNumber: String): String? =
            if (nationalNumber.length in 8..12) dialCode + nationalNumber else null
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        RegisterBusinessViewModel(repo, BusinessTestAuth(), normalizer, handle).also {
            store.put("register", it)
            it.businessName.value = "Negocio"
            it.ruc.value = "20123456789"
            it.legalAddress.value = "Dirección"
            it.email.value = "contacto@example.com"
        }

    @Test fun `guarda el prefijo seleccionado y no solo los digitos del campo`() = runTest {
        val vm = model()
        vm.selectPhoneCountry("+52"); vm.updatePhone("5512345678")
        vm.submitRequest {}; runCurrent()
        assertEquals("+525512345678", repo.submitted?.phone)
    }
    @Test fun `un numero corto muestra error junto al telefono y no guarda`() = runTest {
        val vm = model()
        vm.updatePhone("123")
        vm.submitRequest {}; runCurrent()
        assertNotNull(vm.phoneError.value)
        assertTrue(vm.uiState.value is UiState.Error)
        assertEquals(0, repo.submissionCount)
        vm.updatePhone("987654321")
        assertNull(vm.phoneError.value)
        vm.submitRequest {}; runCurrent()
        assertEquals("+51987654321", repo.submitted?.phone)
    }
    @Test fun `corregir solicitud internacional restaura pais y numero sin duplicar prefijo`() = runTest {
        repo.existing = BusinessRequest(id = "owner", ownerUid = "owner", businessName = "Negocio",
            ruc = "20123456789", legalAddress = "Dirección", email = "contacto@example.com",
            phone = "+573123456789", status = BusinessStatus.OBSERVED)
        val vm = model()
        vm.loadExistingForResubmission(); runCurrent()
        assertEquals("+57", vm.countryDialCode.value)
        assertEquals("3123456789", vm.phone.value)
        vm.submitRequest(isResubmission = true) {}; runCurrent()
        assertEquals("+573123456789", repo.submitted?.phone)
        assertEquals(1, repo.resubmissions)
    }
    @Test fun `registro anterior sin prefijo conserva numero nacional peruano`() = runTest {
        val vm = model()
        vm.populateFromExisting(BusinessRequest(phone = "987654321"))
        assertEquals("+51", vm.countryDialCode.value)
        assertEquals("987654321", vm.phone.value)
    }
    @Test fun `pais y telefono se recuperan desde estado guardado`() = runTest {
        val handle = SavedStateHandle()
        val first = model(handle)
        first.selectPhoneCountry("+593"); first.updatePhone("991234567")
        val restored = model(SavedStateHandle(mapOf(
            "business_phone_country" to handle.get<String>("business_phone_country"),
            "business_phone" to handle.get<String>("business_phone"),
        )))
        assertEquals("+593", restored.countryDialCode.value)
        assertEquals("991234567", restored.phone.value)
    }
    @Test fun `pegar telefono internacional separa el pais del numero`() = runTest {
        val vm = model()
        vm.updatePhone("+593 99 123 4567")
        assertEquals("+593", vm.countryDialCode.value)
        assertEquals("991234567", vm.phone.value)
    }
    @Test fun `doble toque no envia dos solicitudes ni cambia contacto durante el guardado`() = runTest {
        repo.gate = CompletableDeferred()
        val vm = model()
        vm.updatePhone("987654321")
        repeat(2) { vm.submitRequest {} }
        vm.selectPhoneCountry("+52"); vm.updatePhone("5512345678")
        runCurrent()
        assertEquals(1, repo.submissionCount)
        assertEquals("+51", vm.countryDialCode.value)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals("+51987654321", repo.submitted?.phone)
    }
    @Test fun `fallo de envio conserva pais y telefono para reintentar`() = runTest {
        repo.result = ApiResult.Failure("Sin conexión")
        val vm = model()
        vm.selectPhoneCountry("+57"); vm.updatePhone("3123456789")
        vm.submitRequest {}; runCurrent()
        assertTrue(vm.uiState.value is UiState.Error)
        assertEquals("+57", vm.countryDialCode.value)
        assertEquals("3123456789", vm.phone.value)
        repo.result = ApiResult.Success(Unit)
        vm.submitRequest {}; runCurrent()
        assertEquals("+573123456789", repo.submitted?.phone)
    }
}
