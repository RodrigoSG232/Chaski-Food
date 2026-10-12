package com.chaskifood.app.feature.cart.presentation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.feature.cart.domain.AddToCartRejectionReason
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartChangeRejectionReason
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val original = Cart("a", listOf(item()))
    private val repository = FakeCartRepository(CartState.Ready(original))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun `inicia cargando y distingue carrito vacio de sesion cerrada`() = runTest {
        val model = model()
        assertEquals(CartState.Loading, model.uiState.value.source)
        assertFalse(model.uiState.value.isEmpty)
        runCurrent()
        repository.states.value = CartState.Ready(Cart("a"))
        runCurrent()
        assertTrue(model.uiState.value.isEmpty)
        assertEquals(0L, model.uiState.value.cart?.subtotalMinor)
        repository.states.value = CartState.SignedOut
        runCurrent()
        assertNull(model.uiState.value.cart)
        assertFalse(model.uiState.value.isEmpty)
        assertNull(model.uiState.value.totalUnits)
    }

    @Test fun `contador suma unidades del carrito de la sesion observada`() = runTest {
        val model = model()
        runCurrent()
        repository.states.value = CartState.Ready(Cart("a", listOf(item(quantity = 2),
            item(quantity = 4).copy(productId = "otro"))))
        runCurrent()
        assertEquals(6L, model.uiState.value.totalUnits)
        repository.states.value = CartState.SignedOut
        runCurrent()
        assertNull(model.uiState.value.totalUnits)
        repository.states.value = CartState.Ready(Cart("b"))
        runCurrent()
        assertEquals(0L, model.uiState.value.totalUnits)
    }

    @Test fun `observa productos y subtotal exacto sin crear otra fuente de datos`() = runTest {
        val model = model()
        runCurrent()
        assertEquals(original, model.uiState.value.cart)
        assertEquals(1050L, model.uiState.value.cart?.subtotalMinor)
        val updated = Cart("a", listOf(item(quantity = 3)))
        repository.states.value = CartState.Ready(updated)
        runCurrent()
        assertEquals(3150L, model.uiState.value.cart?.subtotalMinor)
        assertEquals(3, model.uiState.value.cart?.items?.single()?.quantity)
        assertEquals(3L, model.uiState.value.totalUnits)
        assertEquals(1, repository.activeCollectors)
    }

    @Test fun `delega incorporacion aumento reduccion y eliminacion sin reglas duplicadas`() = runTest {
        val model = model()
        runCurrent()
        model.addProduct("store", "product", 2)
        runCurrent()
        model.changeQuantity("product", 4)
        runCurrent()
        model.changeQuantity("product", 1)
        runCurrent()
        model.removeProduct("product")
        runCurrent()
        assertEquals(listOf("add:store:product:2", "quantity:product:4", "quantity:product:1", "remove:product"), repository.calls)
        // El resultado de escritura no sustituye la observación del carrito.
        assertEquals(original, model.uiState.value.cart)
        repository.states.value = CartState.Ready(Cart("a"))
        runCurrent()
        assertTrue(model.uiState.value.isEmpty)
    }

    @Test fun `solo una incorporacion confirmada muestra exito`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = CartOperationResult.OfferNetworkError
        model.addProduct("store", "product", 2)
        runCurrent()
        assertEquals(CartUiMessageKind.Error, model.uiState.value.message?.kind)
        repository.answer = CartOperationResult.Updated(original)
        model.addProduct("store", "product", 2)
        runCurrent()
        assertEquals(CartUiMessageKind.Success, model.uiState.value.message?.kind)
        assertEquals(original, model.uiState.value.cart)
        model.changeQuantity("product", 3)
        runCurrent()
        assertNull(model.uiState.value.message)
    }

    @Test fun `reserva la operacion antes del launch y evita pulsaciones simultaneas`() = runTest {
        val gate = CompletableDeferred<CartOperationResult>()
        repository.operation = { gate.await() }
        val model = model()
        runCurrent()
        model.changeQuantity("product", 2)
        model.changeQuantity("product", 2)
        model.removeProduct("product")
        assertEquals(CartUiOperation.ChangeQuantity("product"), model.uiState.value.operation)
        runCurrent()
        assertEquals(listOf("quantity:product:2"), repository.calls)
        assertTrue(model.uiState.value.isBusy)
        gate.complete(CartOperationResult.Updated(original))
        runCurrent()
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `no permite operaciones mientras carga o no hay sesion`() = runTest {
        val model = model()
        model.addProduct("store", "product")
        runCurrent()
        repository.states.value = CartState.SignedOut
        runCurrent()
        model.removeProduct("product")
        model.changeQuantity("product", 2)
        runCurrent()
        assertTrue(repository.calls.isEmpty())
    }

    @Test fun `confirmacion conserva carrito y solo acepta el token mostrado`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        assertEquals(confirmation(), model.uiState.value.confirmation)
        assertEquals(original, model.uiState.value.cart)
        model.confirmStoreChange("wrong")
        model.addProduct("store", "another")
        runCurrent()
        assertEquals(1, repository.calls.size)
        repository.answer = CartOperationResult.Updated(Cart("a", listOf(item(storeId = "other-store"))))
        model.confirmStoreChange("token-1")
        model.confirmStoreChange("token-1")
        runCurrent()
        assertEquals(listOf("add:other-store:product:1", "confirm:token-1"), repository.calls)
        assertNull(model.uiState.value.confirmation)
        assertEquals(original, model.uiState.value.cart)
    }

    @Test fun `cancelar consume la solicitud sin cambiar productos`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        model.cancelStoreChange("token-1")
        model.cancelStoreChange("token-1")
        runCurrent()
        assertEquals(listOf("token-1"), repository.cancelledTokens)
        assertNull(model.uiState.value.confirmation)
        assertEquals(original, model.uiState.value.cart)
    }

    @Test fun `una oferta cambiada exige una segunda aceptacion con el nuevo token`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        val renewed = confirmation().copy(token = "token-2", incomingItem = item(storeId = "other-store", price = 1200), offerChanged = true)
        repository.answer = renewed
        model.confirmStoreChange("token-1")
        runCurrent()
        assertEquals(renewed, model.uiState.value.confirmation)
        assertEquals(1200L, model.uiState.value.confirmation?.incomingItem?.unitPriceMinor)
        model.confirmStoreChange("token-1")
        runCurrent()
        assertEquals(2, repository.calls.size)
        repository.answer = CartOperationResult.Updated(original)
        model.confirmStoreChange("token-2")
        runCurrent()
        assertEquals("confirm:token-2", repository.calls.last())
        assertNull(model.uiState.value.confirmation)
    }

    @Test fun `error de red durante confirmacion mantiene el dialogo sin reintentar solo`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        repository.answer = CartOperationResult.OfferNetworkError
        model.confirmStoreChange("token-1")
        runCurrent()
        assertEquals(confirmation(), model.uiState.value.confirmation)
        assertTrue(model.uiState.value.message!!.text.contains("conexión"))
        assertEquals(2, repository.calls.size)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `red disponibilidad permisos y direccion producen avisos sin alterar carrito`() = runTest {
        val model = model()
        runCurrent()
        val results = listOf(
            CartOperationResult.OfferNetworkError, CartOperationResult.OfferUnavailable,
            CartOperationResult.OfferNotFound, CartOperationResult.OfferPermissionDenied,
            CartOperationResult.OfferUnverified, CartOperationResult.OfferInvalidData,
            CartOperationResult.AddressRequired, CartOperationResult.AddressUnavailable,
            CartOperationResult.AddressChanged, CartOperationResult.RemoteValidationRequired,
            CartOperationResult.StorageError, CartOperationResult.ConfirmationStale,
        ) + AddToCartRejectionReason.entries.map { CartOperationResult.OfferRejected(it) } +
            CartChangeRejectionReason.entries.map { CartOperationResult.RuleRejected(it) }
        for (result in results) {
            repository.answer = result
            model.addProduct("store", "product")
            runCurrent()
            assertTrue("Sin aviso para $result", model.uiState.value.message?.text?.isNotBlank() == true)
            assertEquals(original, model.uiState.value.cart)
            assertFalse(model.uiState.value.isBusy)
        }
        assertEquals(results.size, repository.calls.size)
    }

    @Test fun `descartar un aviso antiguo no borra otro mas reciente`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = CartOperationResult.OfferNetworkError
        model.addProduct("store", "product")
        runCurrent()
        val first = model.uiState.value.message!!
        model.addProduct("store", "product")
        runCurrent()
        val second = model.uiState.value.message!!
        assertTrue(second.id > first.id)
        model.dismissMessage(first.id)
        assertEquals(second, model.uiState.value.message)
        model.dismissMessage(second.id)
        assertNull(model.uiState.value.message)
    }

    @Test fun `confirmacion obsoleta o direccion cambiada cierran el dialogo`() = runTest {
        val model = model()
        runCurrent()
        for (failure in listOf(CartOperationResult.ConfirmationStale, CartOperationResult.AddressChanged)) {
            repository.answer = confirmation()
            model.addProduct("other-store", "product")
            runCurrent()
            repository.answer = failure
            model.confirmStoreChange("token-1")
            runCurrent()
            assertNull(model.uiState.value.confirmation)
            assertTrue(model.uiState.value.message != null)
            assertEquals(original, model.uiState.value.cart)
        }
    }

    @Test fun `una actualizacion externa invalida la confirmacion visible`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        repository.states.value = CartState.Ready(Cart("a", listOf(item(quantity = 2))))
        runCurrent()
        assertNull(model.uiState.value.confirmation)
        model.confirmStoreChange("token-1")
        runCurrent()
        assertEquals(1, repository.calls.size)
    }

    @Test fun `una respuesta tardia no sustituye una observacion mas reciente`() = runTest {
        val gate = CompletableDeferred<CartOperationResult>()
        repository.operation = { gate.await() }
        val model = model()
        runCurrent()
        model.changeQuantity("product", 2)
        runCurrent()
        val newer = Cart("a", listOf(item(quantity = 4)))
        repository.states.value = CartState.Ready(newer)
        runCurrent()
        gate.complete(CartOperationResult.Updated(Cart("a", listOf(item(quantity = 2)))))
        runCurrent()
        assertEquals(newer, model.uiState.value.cart)
    }

    @Test fun `cambiar usuario descarta dialogos y mensajes de la cuenta anterior`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = confirmation()
        model.addProduct("other-store", "product")
        runCurrent()
        repository.states.value = CartState.Ready(Cart("b"))
        runCurrent()
        assertEquals("b", model.uiState.value.cart?.ownerUid)
        assertNull(model.uiState.value.confirmation)
        assertNull(model.uiState.value.message)
    }

    @Test fun `logout y nueva sesion del mismo UID descartan una respuesta antigua`() = runTest {
        val gate = CompletableDeferred<CartOperationResult>()
        repository.operation = { withContext(NonCancellable) { gate.await() } }
        val model = model()
        runCurrent()
        model.addProduct("other-store", "product")
        runCurrent()
        repository.states.value = CartState.SignedOut
        runCurrent()
        assertNull(model.uiState.value.cart)
        repository.states.value = CartState.Loading
        runCurrent()
        repository.states.value = CartState.Ready(original)
        runCurrent()
        gate.complete(confirmation())
        runCurrent()
        assertNull(model.uiState.value.confirmation)
        assertNull(model.uiState.value.message)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `carga de una nueva sesion cancela la operacion anterior`() = runTest {
        val gate = CompletableDeferred<CartOperationResult>()
        var cancelled = false
        repository.operation = { try { gate.await() } finally { cancelled = true } }
        val model = model()
        runCurrent()
        model.addProduct("store", "product")
        runCurrent()
        repository.states.value = CartState.Loading
        runCurrent()
        assertTrue(cancelled)
        assertEquals(CartState.Loading, model.uiState.value.source)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `SessionChanged vuelve a observar y SignedOut borra el contenido`() = runTest {
        val model = model()
        runCurrent()
        repository.answer = CartOperationResult.SessionChanged
        model.addProduct("store", "product")
        runCurrent()
        assertEquals(2, repository.observations)
        assertEquals(1, repository.activeCollectors)
        assertFalse(model.uiState.value.isBusy)
        repository.answer = CartOperationResult.SignedOut
        model.addProduct("store", "product")
        runCurrent()
        assertEquals(CartState.SignedOut, model.uiState.value.source)
        assertNull(model.uiState.value.cart)
    }

    @Test fun `error de lectura permite reintento explicito sin colecciones duplicadas`() = runTest {
        repository.failReading = true
        val model = model()
        runCurrent()
        assertEquals(CartState.ReadError, model.uiState.value.source)
        repository.failReading = false
        model.retryLoading()
        model.retryLoading()
        runCurrent()
        assertEquals(original, model.uiState.value.cart)
        assertEquals(2, repository.observations)
        assertEquals(1, repository.activeCollectors)
    }

    @Test fun `excepcion inesperada muestra aviso pero cancelacion no se convierte en error`() = runTest {
        val model = model()
        runCurrent()
        repository.operation = { error("fallo simulado") }
        model.removeProduct("product")
        runCurrent()
        assertTrue(model.uiState.value.message != null)
        assertFalse(model.uiState.value.isBusy)
        repository.operation = { throw CancellationException("cancelado") }
        model.removeProduct("product")
        runCurrent()
        assertNull(model.uiState.value.message)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `liberar ViewModel cancela observacion y operacion activa`() = runTest {
        val gate = CompletableDeferred<CartOperationResult>()
        var cancelled = false
        repository.operation = { try { gate.await() } finally { cancelled = true } }
        val model = model()
        runCurrent()
        model.addProduct("store", "product")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(0, repository.activeCollectors)
        assertTrue(cancelled)
    }

    private fun model() = CartViewModel(repository).also { store.put("cart", it) }

    private fun confirmation() = CartOperationResult.ConfirmationRequired(
        "token-1", original, item(storeId = "other-store"),
    )

    private fun item(quantity: Int = 1, storeId: String = "store", price: Long = 1050) =
        CartProductSnapshot("business", storeId, "product", "Producto", price, quantity)
}

private class FakeCartRepository(initial: CartState) : CartRepository {
    val states = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    val cancelledTokens = mutableListOf<String>()
    var answer: CartOperationResult = CartOperationResult.Updated(Cart("a"))
    var operation: suspend () -> CartOperationResult = { answer }
    var failReading = false
    var observations = 0
    var activeCollectors = 0

    override fun observeCart(): Flow<CartState> = flow {
        observations++
        activeCollectors++
        try {
            if (failReading) error("lectura simulada")
            emitAll(states)
        } finally {
            activeCollectors--
        }
    }

    override suspend fun addProduct(storeId: String, productId: String, quantity: Int): CartOperationResult {
        calls += "add:$storeId:$productId:$quantity"
        return operation()
    }

    override suspend fun changeQuantity(productId: String, quantity: Int): CartOperationResult {
        calls += "quantity:$productId:$quantity"
        return operation()
    }

    override suspend fun removeProduct(productId: String): CartOperationResult {
        calls += "remove:$productId"
        return operation()
    }

    override suspend fun confirmStoreChange(token: String): CartOperationResult {
        calls += "confirm:$token"
        return operation()
    }

    override suspend fun cancelStoreChange(token: String): Boolean {
        cancelledTokens += token
        return true
    }
}
