package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressBook
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.address.domain.model.AddressDraft
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutContextSourceTest {
    private val sessions = FakeCheckoutSessions()
    private val carts = FakeCheckoutCarts()
    private val addresses = FakeCheckoutAddresses()
    private val local = FakeCheckoutLocalSnapshotReader(carts, addresses)
    private val source = CheckoutContextSource(sessions, carts, addresses, local)

    @Test fun `solo carrito y direccion seleccionada invalidan la proyeccion`() = runTest {
        val observed = mutableListOf<CheckoutContextState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect(observed::add) }
        val initial = observed.last() as CheckoutContextState.Ready
        assertTrue(source.isCurrent(initial.context))
        assertEquals(1, addresses.subscriptions)

        addresses.states.value = AddressBookState.Ready(book().copy(addresses = listOf(address(), address("other"))))
        runCurrent()
        assertEquals(listOf(initial), observed)

        carts.states.value = CartState.Ready(cart(2))
        runCurrent()
        assertEquals(2, (observed.last() as CheckoutContextState.Ready).context.cart.items.first().quantity)
        assertFalse(source.isCurrent(initial.context))

        addresses.states.value = AddressBookState.Ready(book(selected = address().copy(addressText = "Otra calle")))
        runCurrent()
        assertEquals("Otra calle", (observed.last() as CheckoutContextState.Ready).context.selectedAddress?.addressText)
    }

    @Test fun `logout y regreso al mismo UID cambian la generacion`() = runTest {
        val observed = mutableListOf<CheckoutContextState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect(observed::add) }
        val original = (observed.last() as CheckoutContextState.Ready).context

        sessions.change(null)
        runCurrent()
        assertEquals(CheckoutContextState.SignedOut, observed.last())
        sessions.change("A")
        runCurrent()
        val renewed = (observed.last() as CheckoutContextState.Ready).context
        assertEquals(original.ownerUid, renewed.ownerUid)
        assertTrue(renewed.sessionGeneration > original.sessionGeneration)
        assertFalse(source.isCurrent(original))
    }

    @Test fun `cambio de carrito durante la ultima espera remota se detecta`() = runTest {
        val original = source.observe().let { flow ->
            val states = mutableListOf<CheckoutContextState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect(states::add) }
            (states.last() as CheckoutContextState.Ready).context
        }
        val gate = CompletableDeferred<Unit>()
        val result = async { gate.await(); source.isCurrent(original) }
        runCurrent()
        carts.states.value = CartState.Ready(cart(2))
        gate.complete(Unit)
        assertFalse(result.await())
    }

    @Test fun `fallo de direcciones invalida el contexto listo`() = runTest {
        val observed = mutableListOf<CheckoutContextState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect(observed::add) }
        addresses.states.value = AddressBookState.Error("A", "Error de prueba")
        runCurrent()
        assertEquals(CheckoutContextState.ReadError, observed.last())
    }

    @Test fun `carrito confirmado antes de la emision invalida el contexto observado`() = runTest {
        val observed = mutableListOf<CheckoutContextState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect(observed::add) }
        val original = (observed.last() as CheckoutContextState.Ready).context

        local.committed = CheckoutLocalSnapshot(CartState.Ready(cart(2)), "address")

        assertEquals(CheckoutContextState.Ready(original), observed.last())
        assertFalse(source.isCurrent(original))
    }

    @Test fun `direccion seleccionada antes de la emision invalida el contexto observado`() = runTest {
        val observed = mutableListOf<CheckoutContextState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect(observed::add) }
        val original = (observed.last() as CheckoutContextState.Ready).context

        local.committed = CheckoutLocalSnapshot(CartState.Ready(cart()), "other")

        assertEquals(CheckoutContextState.Ready(original), observed.last())
        assertFalse(source.isCurrent(original))
    }
}

private fun cart(quantity: Int = 1) = Cart("A", listOf(
    CartProductSnapshot("business", "store", "product", "Plato", 1000, quantity),
))

private fun address(id: String = "address") = Address(id, "Casa", "Lima",
    AddressCoordinates(-12.0, -77.0), "", "", 1L, 1L)

private fun book(selected: Address = address()) = AddressBook("A", listOf(selected), selected.id, selected.id)

private class FakeCheckoutSessions : CartSessionSource {
    private val state = MutableStateFlow(CartSession("A", 0))
    override val sessions: StateFlow<CartSession> = state
    override fun current(): CartSession = state.value
    fun change(uid: String?) { state.value = CartSession(uid, state.value.generation + 1) }
}

private class FakeCheckoutCarts : CartRepository {
    val states = MutableStateFlow<CartState>(CartState.Ready(cart()))
    override fun observeCart(): Flow<CartState> = states
    override suspend fun addProduct(storeId: String, productId: String, quantity: Int): CartOperationResult = error("No usado")
    override suspend fun changeQuantity(productId: String, quantity: Int): CartOperationResult = error("No usado")
    override suspend fun removeProduct(productId: String): CartOperationResult = error("No usado")
    override suspend fun confirmStoreChange(token: String): CartOperationResult = error("No usado")
    override suspend fun cancelStoreChange(token: String): Boolean = error("No usado")
}

private class FakeCheckoutAddresses : AddressRepository {
    val states = MutableStateFlow<AddressBookState>(AddressBookState.Ready(book()))
    var subscriptions = 0
    override fun observeAddressBook(): Flow<AddressBookState> = flow {
        subscriptions++
        emitAll(states)
    }
    override suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address> = error("No usado")
    override suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address> = error("No usado")
    override suspend fun deleteAddress(addressId: String, replacementDefaultAddressId: String?): ApiResult<Unit> = error("No usado")
    override suspend fun setDefaultAddress(addressId: String): ApiResult<Unit> = error("No usado")
    override suspend fun selectAddress(addressId: String): ApiResult<Unit> = error("No usado")
}

private class FakeCheckoutLocalSnapshotReader(
    private val carts: FakeCheckoutCarts,
    private val addresses: FakeCheckoutAddresses,
) : CheckoutLocalSnapshotReader {
    var committed: CheckoutLocalSnapshot? = null
    override suspend fun read(session: CartSession): CheckoutLocalSnapshot = committed ?: CheckoutLocalSnapshot(
        carts.states.value,
        (addresses.states.value as? AddressBookState.Ready)?.book?.selectedAddressId,
    )
}
