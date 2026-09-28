package com.chaskifood.app.feature.address.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.Address
import com.chaskifood.app.feature.address.domain.AddressBook
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.chaskifood.app.feature.address.domain.AddressField
import com.chaskifood.app.feature.address.domain.AddressRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model(repo: FakeRepository, handle: SavedStateHandle = SavedStateHandle()) =
        AddressViewModel(repo, handle).also { store.put((nextKey++).toString(), it) }

    @Test fun `carga no se presenta como vacio y solo Ready muestra datos`() = runTest {
        val repo = FakeRepository(AddressBookState.Loading("a"))
        val vm = model(repo)
        assertNull(vm.uiState.value.book)
        runCurrent()
        assertTrue(vm.uiState.value.source is AddressBookState.Loading)
        repo.states.value = ready(emptyList())
        runCurrent()
        assertEquals(emptyList<Address>(), vm.uiState.value.book!!.addresses)
    }

    @Test fun `error no es vacio y reintentar renueva la observacion`() = runTest {
        val repo = FakeRepository(AddressBookState.Error("a", "Error"))
        val vm = model(repo)
        runCurrent()
        assertNull(vm.uiState.value.book)
        vm.onAction(AddressAction.Retry)
        runCurrent()
        assertEquals(2, repo.observations)
        repo.states.value = ready()
        runCurrent()
        assertEquals(2, vm.uiState.value.book!!.addresses.size)
    }

    @Test fun `radio es propuesta local y usar confirma sin cambiar predeterminada`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Choose("b"))
        assertTrue(repo.selected.isEmpty())
        assertEquals("a", vm.uiState.value.book!!.selectedAddressId)
        vm.onAction(AddressAction.UseChosen)
        runCurrent()
        assertEquals(listOf("b"), repo.selected)
        assertTrue(vm.uiState.value.exitRequested)
        assertEquals("a", vm.uiState.value.book!!.defaultAddressId)
        vm.acknowledgeExit()
        assertFalse(vm.uiState.value.exitRequested)
    }

    @Test fun `seleccion fallida no navega ni simula exito`() = runTest {
        val repo = FakeRepository().apply { selectResult = ApiResult.Failure("falla") }
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Choose("b"))
        vm.onAction(AddressAction.UseChosen)
        runCurrent()
        assertFalse(vm.uiState.value.exitRequested)
        assertNotNull(vm.uiState.value.message)
        assertEquals("a", vm.uiState.value.book!!.selectedAddressId)
    }

    @Test fun `doble pulsacion solo envia una operacion`() = runTest {
        val repo = FakeRepository()
        val gate = CompletableDeferred<Unit>()
        repo.beforeWrite = { gate.await() }
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.UseChosen)
        vm.onAction(AddressAction.UseChosen)
        runCurrent()
        assertTrue(vm.uiState.value.busy)
        assertEquals(1, repo.selected.size)
        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun `predeterminada usa repositorio y no altera seleccion anticipadamente`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.SetDefault("b"))
        runCurrent()
        assertEquals(listOf("b"), repo.defaults)
        assertEquals("a", vm.uiState.value.book!!.defaultAddressId)
        repo.states.value = ready(defaultId = "b")
        runCurrent()
        assertEquals("a", vm.uiState.value.book!!.selectedAddressId)
        assertEquals("b", vm.uiState.value.book!!.defaultAddressId)
    }

    @Test fun `eliminar predeterminada exige reemplazo propio explicito`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.AskDelete("a"))
        vm.onAction(AddressAction.ConfirmDelete)
        runCurrent()
        assertTrue(repo.deleted.isEmpty())
        vm.onAction(AddressAction.ChooseReplacement("ajena"))
        vm.onAction(AddressAction.ChooseReplacement("a"))
        assertNull(vm.uiState.value.deletion!!.replacementId)
        vm.onAction(AddressAction.ChooseReplacement("b"))
        vm.onAction(AddressAction.ConfirmDelete)
        runCurrent()
        assertEquals(listOf("a" to "b"), repo.deleted)
    }

    @Test fun `eliminar ultima no manda reemplazo y espera nueva lectura`() = runTest {
        val repo = FakeRepository(ready(listOf(address("a"))))
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.AskDelete("a"))
        vm.onAction(AddressAction.ConfirmDelete)
        runCurrent()
        assertEquals(listOf("a" to null), repo.deleted)
        assertEquals(1, vm.uiState.value.book!!.addresses.size)
        repo.states.value = ready(emptyList(), null, null)
        runCurrent()
        assertNull(vm.uiState.value.choiceId)
        assertTrue(vm.uiState.value.message!!.contains("Ya no tienes"))
    }

    @Test fun `borrado fallido conserva dialogo para reintentar`() = runTest {
        val repo = FakeRepository().apply { deleteResult = ApiResult.Failure("falla") }
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.AskDelete("b"))
        vm.onAction(AddressAction.ConfirmDelete)
        runCurrent()
        assertNotNull(vm.uiState.value.deletion)
        assertNotNull(vm.uiState.value.message)
        assertEquals(2, vm.uiState.value.book!!.addresses.size)
    }

    @Test fun `borrador invalido o sin punto confirmado no se envia`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Add)
        vm.onAction(AddressAction.Save)
        vm.onAction(AddressAction.ChangeText(AddressField.ADDRESS_TEXT, "Texto de prueba"))
        vm.changeLocation(AddressCoordinates(0.0, 0.0))
        vm.onAction(AddressAction.Save)
        runCurrent()
        assertTrue(repo.creates.isEmpty())
        assertTrue(vm.uiState.value.editor!!.errors.containsKey(AddressField.LOCATION))
    }

    @Test fun `cambiar punto invalida confirmacion`() = runTest {
        val vm = model(FakeRepository())
        runCurrent()
        vm.onAction(AddressAction.Edit("a"))
        assertTrue(vm.uiState.value.editor!!.draft.isLocationConfirmed)
        vm.changeLocation(AddressCoordinates(1.0, 1.0))
        assertFalse(vm.uiState.value.editor!!.draft.isLocationConfirmed)
        vm.confirmLocation()
        assertTrue(vm.uiState.value.editor!!.draft.isLocationConfirmed)
    }

    @Test fun `alta fallida congela payload y reintenta con mismo identificador`() = runTest {
        val repo = FakeRepository().apply { createResult = ApiResult.Failure("incierto") }
        val vm = model(repo)
        runCurrent()
        completeDraft(vm)
        vm.onAction(AddressAction.Save)
        runCurrent()
        val pending = vm.uiState.value.editor!!
        assertTrue(pending.pendingCreate)
        vm.onAction(AddressAction.ChangeText(AddressField.ADDRESS_TEXT, "Otro contenido"))
        vm.onAction(AddressAction.Back)
        vm.onAction(AddressAction.Discard)
        assertEquals(pending.draft, vm.uiState.value.editor!!.draft)
        vm.onAction(AddressAction.Save)
        runCurrent()
        assertEquals(2, repo.creates.size)
        assertEquals(repo.creates[0], repo.creates[1])
        assertEquals("Texto de prueba", repo.creates[0].first.addressText)
    }

    @Test fun `recreacion conserva id y datos pendientes solo para mismo propietario`() = runTest {
        val handle = SavedStateHandle()
        val repo = FakeRepository().apply { createResult = ApiResult.Failure("incierto") }
        val first = model(repo, handle)
        runCurrent()
        completeDraft(first)
        first.onAction(AddressAction.Save)
        runCurrent()
        val saved = handle.keys().associateWith { handle.get<Any>(it) }
        val next = model(repo, SavedStateHandle(saved))
        runCurrent()
        assertEquals(first.uiState.value.editor, next.uiState.value.editor)
        next.onAction(AddressAction.Save)
        runCurrent()
        assertEquals(repo.creates[0], repo.creates[1])
        val another = model(FakeRepository(ready(owner = "otra")), SavedStateHandle(saved))
        runCurrent()
        assertNull(another.uiState.value.editor)
    }

    @Test fun `alta confirmada cierra formulario`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        completeDraft(vm)
        vm.onAction(AddressAction.Save)
        runCurrent()
        assertNull(vm.uiState.value.editor)
        assertEquals("Dirección guardada.", vm.uiState.value.message)
        assertEquals(1, repo.creates.size)
    }

    @Test fun `editar conserva id y punto y solo llama update`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Edit("a"))
        vm.onAction(AddressAction.ChangeText(AddressField.REFERENCE, "Frente al ejemplo"))
        vm.onAction(AddressAction.Save)
        runCurrent()
        assertEquals("a", repo.updates.single().first)
        assertEquals(address("a").location, repo.updates.single().second.location)
        assertEquals("Frente al ejemplo", repo.updates.single().second.reference)
        assertTrue(repo.creates.isEmpty())
    }

    @Test fun `editar fallido conserva cambios y permite corregir`() = runTest {
        val repo = FakeRepository().apply { updateResult = ApiResult.Failure("falla") }
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Edit("a"))
        vm.onAction(AddressAction.ChangeText(AddressField.LABEL, "Etiqueta nueva"))
        vm.onAction(AddressAction.Save)
        runCurrent()
        assertEquals("Etiqueta nueva", vm.uiState.value.editor!!.draft.label)
        vm.onAction(AddressAction.ChangeText(AddressField.LABEL, "Corregida"))
        assertEquals("Corregida", vm.uiState.value.editor!!.draft.label)
    }

    @Test fun `atras pide confirmar descarte y cancelar conserva formulario`() = runTest {
        val vm = model(FakeRepository())
        runCurrent()
        vm.onAction(AddressAction.Add)
        vm.onAction(AddressAction.ChangeText(AddressField.LABEL, "Borrador"))
        vm.onAction(AddressAction.Back)
        assertTrue(vm.uiState.value.showDiscard)
        vm.onAction(AddressAction.CloseDialog)
        assertEquals("Borrador", vm.uiState.value.editor!!.draft.label)
        vm.onAction(AddressAction.Back)
        vm.onAction(AddressAction.Discard)
        assertNull(vm.uiState.value.editor)
        assertFalse(vm.uiState.value.exitRequested)
    }

    @Test fun `cambio de sesion borra datos formulario y respuesta tardia`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepository().apply {
            beforeWrite = { withContext(NonCancellable) { gate.await() } }
        }
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.UseChosen)
        runCurrent()
        repo.states.value = AddressBookState.Loading("otra")
        runCurrent()
        assertNull(vm.uiState.value.book)
        assertFalse(vm.uiState.value.busy)
        repo.states.value = ready(owner = "otra", addresses = emptyList())
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.exitRequested)
        assertNull(vm.uiState.value.message)
        assertEquals("otra", vm.uiState.value.book!!.ownerUid)
    }

    @Test fun `salir y volver con mismo uid no recupera borrador de sesion anterior`() = runTest {
        val repo = FakeRepository()
        val handle = SavedStateHandle()
        val vm = model(repo, handle)
        runCurrent()
        vm.onAction(AddressAction.Add)
        vm.onAction(AddressAction.ChangeText(AddressField.LABEL, "Privado"))
        repo.states.value = AddressBookState.SignedOut
        runCurrent()
        assertNull(vm.uiState.value.editor)
        assertTrue(handle.keys().isEmpty())
        repo.states.value = AddressBookState.Loading("a")
        runCurrent()
        repo.states.value = ready()
        runCurrent()
        assertNull(vm.uiState.value.editor)
    }

    @Test fun `reconciliacion tras borrado remoto avisa incluso despues de error`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        repo.states.value = AddressBookState.Error("a", "Sin red")
        runCurrent()
        assertNull(vm.uiState.value.book)
        repo.states.value = ready(listOf(address("b")), "b", "b")
        runCurrent()
        assertEquals("b", vm.uiState.value.choiceId)
        assertTrue(vm.uiState.value.message!!.contains("Cambió tu dirección"))
        assertEquals(vm.uiState.value.message, vm.uiState.value.deliveryNotice)
        vm.onAction(AddressAction.DismissMessage)
        assertNull(vm.uiState.value.deliveryNotice)
    }

    @Test fun `reintentar lectura conserva formulario de la misma cuenta`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Edit("a"))
        vm.onAction(AddressAction.ChangeText(AddressField.LABEL, "Pendiente"))
        repo.states.value = AddressBookState.Error("a", "Sin red")
        runCurrent()
        vm.onAction(AddressAction.Retry)
        runCurrent()
        repo.states.value = ready()
        runCurrent()
        assertEquals("Pendiente", vm.uiState.value.editor!!.draft.label)
    }

    @Test fun `direccion borrada remotamente no conserva formulario ni reemplazo invalido`() = runTest {
        val repo = FakeRepository()
        val vm = model(repo)
        runCurrent()
        vm.onAction(AddressAction.Edit("b"))
        repo.states.value = ready(listOf(address("a")))
        runCurrent()
        assertNull(vm.uiState.value.editor)
        repo.states.value = ready()
        runCurrent()
        vm.onAction(AddressAction.AskDelete("a"))
        vm.onAction(AddressAction.ChooseReplacement("b"))
        repo.states.value = ready(listOf(address("a")))
        runCurrent()
        assertNull(vm.uiState.value.deletion!!.replacementId)
    }

    private fun completeDraft(vm: AddressViewModel) {
        vm.onAction(AddressAction.Add)
        vm.onAction(AddressAction.ChangeText(AddressField.ADDRESS_TEXT, "  Texto de prueba  "))
        vm.changeLocation(AddressCoordinates(0.0, 0.0))
        vm.confirmLocation()
    }

    private class FakeRepository(initial: AddressBookState = ready()) : AddressRepository {
        val states = MutableStateFlow(initial)
        var observations = 0
        val creates = mutableListOf<Pair<AddressDraft, String>>()
        val updates = mutableListOf<Pair<String, AddressDraft>>()
        val selected = mutableListOf<String>()
        val defaults = mutableListOf<String>()
        val deleted = mutableListOf<Pair<String, String?>>()
        var beforeWrite: suspend () -> Unit = {}
        var selectResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var createResult: ApiResult<Address> = ApiResult.Success(address("new"))
        var updateResult: ApiResult<Address> = ApiResult.Success(address("a"))
        override fun observeAddressBook(): Flow<AddressBookState> = flow {
            observations++
            val uid = (states.value as? AddressBookState.Ready)?.book?.ownerUid ?: "a"
            emit(AddressBookState.Loading(uid))
            emitAll(states)
        }
        override suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address> {
            creates += draft to operationId
            beforeWrite()
            return createResult
        }
        override suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address> {
            updates += addressId to draft
            beforeWrite()
            return updateResult
        }
        override suspend fun deleteAddress(addressId: String, replacementDefaultAddressId: String?): ApiResult<Unit> {
            deleted += addressId to replacementDefaultAddressId
            beforeWrite()
            return deleteResult
        }
        override suspend fun setDefaultAddress(addressId: String): ApiResult<Unit> {
            defaults += addressId
            beforeWrite()
            return ApiResult.Success(Unit)
        }
        override suspend fun selectAddress(addressId: String): ApiResult<Unit> {
            selected += addressId
            beforeWrite()
            return selectResult
        }
    }

    companion object {
        private fun address(id: String) = Address(id, "Casa $id", "Texto de prueba $id",
            AddressCoordinates(0.0, 0.0), "", "", 1L, 1L)
        private fun ready(addresses: List<Address> = listOf(address("a"), address("b")),
            defaultId: String? = "a", selectedId: String? = "a", owner: String = "a") =
            AddressBookState.Ready(AddressBook(owner, addresses, defaultId, selectedId))
    }
}
