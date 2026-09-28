package com.chaskifood.app.feature.address.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAddressRepositoryTest {
    private val sessions = FakeSessions()
    private val remote = FakeRemote()
    private val selection = FakeSelection()
    private val repository = FirebaseAddressRepository(remote, selection, sessions)

    @Test
    fun `sin sesion no consulta ni escribe direcciones`() = runTest {
        sessions.change(null)
        assertEquals(AddressBookState.SignedOut, repository.observeAddressBook().first())
        assertTrue(repository.createAddress(draft(), "op") is ApiResult.Failure)
        assertEquals(0, remote.reads)
        assertEquals(0, remote.writes)
    }

    @Test
    fun `borrador invalido se rechaza antes de contactar fuente remota`() = runTest {
        assertTrue(repository.createAddress(AddressDraft(), "op") is ApiResult.Failure)
        assertEquals(0, remote.writes)
    }

    @Test
    fun `alta envia borrador normalizado UID autenticado y operationId original`() = runTest {
        assertTrue(repository.createAddress(draft().copy(addressText = " Calle ficticia "), "op") is ApiResult.Success)
        assertEquals("A", remote.lastOwner)
        assertEquals("op", remote.lastOperation)
        assertEquals("Calle ficticia", remote.lastDraft?.addressText)
    }

    @Test
    fun `cambio de cuenta mientras espera impide escribir y devolver exito`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.beforeWrite = { gate.await() }
        val result = async { repository.createAddress(draft(), "op") }
        advanceUntilIdle()
        sessions.change("B")
        gate.complete(Unit)
        assertTrue(result.await() is ApiResult.Failure)
        assertEquals(0, remote.writes)
    }

    @Test
    fun `respuesta tardia de escritura confirmada no entrega exito a otra sesion`() = runTest {
        remote.afterWrite = { sessions.change("B") }
        assertTrue(repository.createAddress(draft(), "op") is ApiResult.Failure)
        assertEquals(1, remote.writes) // No promete revertir una escritura ya confirmada.
    }

    @Test
    fun `volver al mismo UID no acepta respuesta de su sesion anterior`() = runTest {
        remote.afterWrite = { sessions.change(null); sessions.change("A") }
        assertTrue(repository.createAddress(draft(), "op") is ApiResult.Failure)
    }

    @Test
    fun `cancelacion se propaga y no se convierte en fallo de negocio`() = runTest {
        val cancellation = CancellationException("cancelación sintética")
        remote.beforeWrite = { throw cancellation }
        try {
            repository.createAddress(draft(), "op")
            fail("Debe propagar cancelación")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun `error de fuente no filtra su mensaje privado`() = runTest {
        remote.beforeWrite = { error("contenido sintético que no debe exponerse") }
        val result = repository.createAddress(draft(), "op") as ApiResult.Failure
        assertFalse(result.message.orEmpty().contains("contenido sintético"))
        assertNull(result.cause)
    }

    @Test
    fun `seleccion exige lectura vigente y no modifica predeterminada`() = runTest {
        remote.books["A"] = RemoteAddressBook(listOf(address("uno"), address("dos")), "uno")
        assertTrue(repository.selectAddress("dos") is ApiResult.Success)
        assertEquals("dos", selection.observe("A").first())
        assertEquals("uno", remote.books.getValue("A").defaultAddressId)
        assertTrue(repository.selectAddress("ajena") is ApiResult.Failure)
        assertEquals("dos", selection.observe("A").first())
    }

    @Test
    fun `fallo de lectura no borra seleccion ni se presenta como lista vacia`() = runTest {
        selection.select("A", "uno")
        remote.readError = IllegalStateException("sin red")
        val states = mutableListOf<AddressBookState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeAddressBook().toList(states) }
        advanceUntilIdle()
        assertTrue(states.first() is AddressBookState.Loading)
        assertTrue(states.last() is AddressBookState.Error)
        assertFalse(states.any { it is AddressBookState.Ready })
        assertEquals("uno", selection.observe("A").first())
        remote.readError = null
        remote.books["A"] = RemoteAddressBook(listOf(address("uno")), "uno")
        remote.invalidate()
        advanceUntilIdle()
        assertTrue(states.last() is AddressBookState.Ready)
    }

    @Test
    fun `cambio de cuenta emite carga nueva y nunca reutiliza seleccion de otra`() = runTest {
        remote.books["A"] = RemoteAddressBook(listOf(address("uno")), "uno")
        remote.books["B"] = RemoteAddressBook(listOf(address("dos")), "dos")
        val states = mutableListOf<AddressBookState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeAddressBook().toList(states) }
        advanceUntilIdle()
        assertEquals("A", (states.last() as AddressBookState.Ready).book.ownerUid)
        val boundary = states.size
        sessions.change("B")
        advanceUntilIdle()
        assertEquals(AddressBookState.Loading("B"), states[boundary])
        assertTrue(states.drop(boundary).filterIsInstance<AddressBookState.Ready>().all { it.book.ownerUid == "B" })
        assertEquals("dos", (states.last() as AddressBookState.Ready).book.selectedAddressId)
        sessions.change(null)
        advanceUntilIdle()
        assertEquals(AddressBookState.SignedOut, states.last())
    }

    @Test
    fun `lectura suspendida de cuenta anterior se cancela y no aparece despues del cambio`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.beforeRead = { uid -> if (uid == "A") gate.await() }
        remote.books["A"] = RemoteAddressBook(listOf(address("uno")), "uno")
        remote.books["B"] = RemoteAddressBook(listOf(address("dos")), "dos")
        val states = mutableListOf<AddressBookState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeAddressBook().toList(states) }
        advanceUntilIdle()
        assertEquals(AddressBookState.Loading("A"), states.last())
        sessions.change("B")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(states.filterIsInstance<AddressBookState.Ready>().all { it.book.ownerUid == "B" })
        assertEquals("B", (states.last() as AddressBookState.Ready).book.ownerUid)
    }

    @Test
    fun `seleccionar direccion recien creada relee catalogo aunque no llegue aun listener`() = runTest {
        remote.books["A"] = RemoteAddressBook(listOf(address("uno")), "uno")
        val states = mutableListOf<AddressBookState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeAddressBook().toList(states) }
        advanceUntilIdle()
        remote.books["A"] = RemoteAddressBook(listOf(address("uno"), address("dos")), "uno")
        repository.selectAddress("dos")
        advanceUntilIdle()
        assertEquals("dos", (states.last() as AddressBookState.Ready).book.selectedAddressId)
    }

    @Test
    fun `cambio de predeterminada conserva seleccion y borrado confirmado aplica fallback`() = runTest {
        remote.books["A"] = RemoteAddressBook(listOf(address("uno"), address("dos")), "uno")
        selection.select("A", "uno")
        val states = mutableListOf<AddressBookState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeAddressBook().toList(states) }
        advanceUntilIdle()
        remote.books["A"] = RemoteAddressBook(listOf(address("uno"), address("dos")), "dos")
        remote.invalidate()
        advanceUntilIdle()
        assertEquals("uno", (states.last() as AddressBookState.Ready).book.selectedAddressId)
        remote.books["A"] = RemoteAddressBook(listOf(address("dos")), "dos")
        remote.invalidate()
        advanceUntilIdle()
        assertEquals("dos", (states.last() as AddressBookState.Ready).book.selectedAddressId)
    }

    @Test
    fun `rutas manipuladas se rechazan antes de fuentes remotas`() = runTest {
        assertTrue(repository.createAddress(draft(), "otra/ruta") is ApiResult.Failure)
        assertTrue(repository.updateAddress("../otra", draft()) is ApiResult.Failure)
        assertTrue(repository.deleteAddress("A", "otra/ruta") is ApiResult.Failure)
        assertTrue(repository.setDefaultAddress("otra/ruta") is ApiResult.Failure)
        assertEquals(0, remote.writes)
    }

    private class FakeSessions : AddressSessionSource {
        override val sessions = MutableStateFlow(AddressSession("A", 0))
        override fun current() = sessions.value
        fun change(uid: String?) { sessions.value = AddressSession(uid, sessions.value.generation + 1) }
    }

    private class FakeSelection : AddressSelectionStore {
        private val values = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun observe(ownerUid: String) = values.map { it[ownerUid] }.distinctUntilChanged()
        override suspend fun select(ownerUid: String, addressId: String?) {
            values.value = if (addressId == null) values.value - ownerUid else values.value + (ownerUid to addressId)
        }
        override suspend fun reconcile(ownerUid: String, availableAddressIds: Set<String>, defaultAddressId: String?): String? {
            val next = AddressSelectionPolicy.resolve(availableAddressIds, values.value[ownerUid], defaultAddressId)
            select(ownerUid, next)
            return next
        }
    }

    private class FakeRemote : AddressRemoteDataSource {
        val books = mutableMapOf<String, RemoteAddressBook>()
        private val version = MutableStateFlow(0)
        var beforeWrite: suspend () -> Unit = {}
        var beforeRead: suspend (String) -> Unit = {}
        var afterWrite: suspend () -> Unit = {}
        var readError: Exception? = null
        var reads = 0
        var writes = 0
        var lastOwner: String? = null
        var lastOperation: String? = null
        var lastDraft: AddressDraft? = null
        fun invalidate() { version.value++ }
        override fun changes(ownerUid: String) = version.map { Unit }
        override suspend fun read(ownerUid: String): RemoteAddressBook {
            reads++
            beforeRead(ownerUid)
            readError?.let { throw it }
            return books[ownerUid] ?: RemoteAddressBook(emptyList(), null)
        }
        override suspend fun create(ownerUid: String, draft: AddressDraft, operationId: String, checkSession: () -> Unit): Address {
            beforeWrite()
            checkSession()
            writes++
            lastOwner = ownerUid
            lastOperation = operationId
            lastDraft = draft
            afterWrite()
            return address(operationId)
        }
        override suspend fun update(ownerUid: String, addressId: String, draft: AddressDraft, checkSession: () -> Unit) =
            create(ownerUid, draft, addressId, checkSession)
        override suspend fun delete(ownerUid: String, addressId: String, replacementId: String?, checkSession: () -> Unit) { checkSession(); writes++ }
        override suspend fun setDefault(ownerUid: String, addressId: String, checkSession: () -> Unit) { checkSession(); writes++ }
    }

    companion object {
        private fun draft() = AddressDraft(addressText = "Calle ficticia", location = AddressCoordinates(0.0, 0.0), isLocationConfirmed = true)
        private fun address(id: String) = Address(id, "", "Calle ficticia", AddressCoordinates(0.0, 0.0), "", "", 1, 1)
    }
}
