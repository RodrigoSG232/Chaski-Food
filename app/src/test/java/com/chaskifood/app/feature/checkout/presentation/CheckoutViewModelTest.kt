package com.chaskifood.app.feature.checkout.presentation

import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `un precio cambiado exige revision expresa y modalidad de pago`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        assertFalse(model.uiState.value.reviewComplete)
        model.acceptPriceChanges()
        runCurrent()
        assertTrue(model.uiState.value.reviewComplete)

        repository.next = CheckoutReviewResult.Blocked(listOf("Producto agotado"))
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `revalidar invalida la aceptacion anterior de precios`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        model.acceptPriceChanges()
        runCurrent()
        assertTrue(model.uiState.value.reviewComplete)

        repository.next = CheckoutReviewResult.Ready(quote(oldPrice = 900))
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
        model.acceptPriceChanges()
        runCurrent()
        assertTrue(model.uiState.value.reviewComplete)
    }

    @Test fun `editar el carrito despues de Ready descarta cotizacion y aceptacion`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        model.acceptPriceChanges()
        runCurrent()
        assertTrue(model.uiState.value.reviewComplete)

        repository.context.value = CheckoutContextState.Ready(context(quantity = 2))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.acceptedPriceChanges)
        assertFalse(model.uiState.value.reviewComplete)
        assertEquals(1, repository.reviews)
    }

    @Test fun `editar direccion seleccionada invalida Ready sin consultar otra vez`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(addressText = "Otra calle"))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertEquals(1, repository.reviews)
    }

    @Test fun `logout y nuevo login con mismo UID no conserva revision ni pago`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        model.acceptPriceChanges()
        runCurrent()

        repository.context.value = CheckoutContextState.SignedOut
        runCurrent()
        assertEquals(CheckoutReviewResult.SignedOut, model.uiState.value.result)
        assertNull(model.uiState.value.paymentMethod)
        repository.context.value = CheckoutContextState.Ready(context(generation = 2))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `dos revisiones desordenadas no publican la primera respuesta`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val old = CheckoutReviewResult.Ready(quote(oldPrice = 1000))
        val newer = CheckoutReviewResult.Blocked(listOf("Oferta agotada"))
        val repository = FakeCheckoutRepository(old)
        repository.reviewBlock = { number ->
            if (number == 1) withContext(NonCancellable) { gate.await() }.let { old } else newer
        }
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.review()
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
        gate.complete(Unit)
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
    }

    @Test fun `cambio de carrito durante consulta descarta respuesta tardia`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { withContext(NonCancellable) { gate.await() }; repository.next }
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(quantity = 3))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `cambio de direccion durante consulta descarta respuesta tardia`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { withContext(NonCancellable) { gate.await() }; repository.next }
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(addressText = "Otra calle"))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.result)
    }

    @Test fun `logout durante consulta y regreso al mismo UID descarta respuesta`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { withContext(NonCancellable) { gate.await() }; repository.next }
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.SignedOut
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(generation = 2))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.result)
        assertNull(model.uiState.value.paymentMethod)
    }

    @Test fun `error y cancelacion de repositorio no dejan revision anterior`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        repository.reviewBlock = { throw IllegalStateException("error de prueba") }
        model.review()
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `respuesta remota no publica Ready si el carrito cambio antes del Flow`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { gate.await(); repository.next }
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()

        repository.actualContext = context(quantity = 2)
        gate.complete(Unit)
        runCurrent()

        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.loading)
        assertFalse(model.uiState.value.reviewComplete)
        assertEquals(CheckoutContextState.Ready(context()), repository.context.value)
    }

    @Test fun `aceptacion se invalida si el carrito cambio antes del Flow`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)

        repository.actualContext = context(quantity = 2)
        model.acceptPriceChanges()
        runCurrent()

        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `ReadError permite reabrir observacion y revisar de nuevo`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.contextFlow = {
            if (repository.subscriptions == 1) flowOf(CheckoutContextState.ReadError)
            else flowOf(CheckoutContextState.Ready(context()))
        }
        val model = CheckoutViewModel(repository)
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)

        model.review()
        runCurrent()

        assertTrue(model.uiState.value.result is CheckoutReviewResult.Ready)
        assertEquals(2, repository.subscriptions)
        assertEquals(1, repository.reviews)
    }

    @Test fun `ReadError persistente no provoca bucle ni observadores duplicados`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.contextFlow = { flowOf(CheckoutContextState.ReadError) }
        val model = CheckoutViewModel(repository)
        runCurrent()
        assertEquals(1, repository.subscriptions)
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)

        model.review()
        runCurrent()

        assertEquals(2, repository.subscriptions)
        assertEquals(0, repository.reviews)
        assertEquals(1, repository.peakActiveSubscriptions)
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)
    }

    private fun quote(oldPrice: Long) = CheckoutQuote(
        ownerUid = "customer", storeId = "store", storeName = "Local",
        addressId = "address", addressLabel = "Casa", addressText = "Lima",
        lines = listOf(CheckoutLine("product", "Plato", 2, oldPrice, 1200, 2400)),
        subtotalMinor = 2400, deliveryFeeMinor = 500, totalMinor = 2900,
    )
}

private class FakeCheckoutRepository(var next: CheckoutReviewResult) : CheckoutRepository {
    val context = MutableStateFlow<CheckoutContextState>(CheckoutContextState.Ready(context()))
    var actualContext: CheckoutContext? = null
    var contextFlow: () -> Flow<CheckoutContextState> = { context }
    var subscriptions = 0
    private var activeSubscriptions = 0
    var peakActiveSubscriptions = 0
    var reviews = 0
    var reviewBlock: suspend (Int) -> CheckoutReviewResult = { next }
    override fun observeContext(): Flow<CheckoutContextState> = flow {
        subscriptions++
        activeSubscriptions++
        peakActiveSubscriptions = maxOf(peakActiveSubscriptions, activeSubscriptions)
        try {
            emitAll(contextFlow())
        } finally {
            activeSubscriptions--
        }
    }
    override suspend fun isContextCurrent(context: CheckoutContext): Boolean =
        (this.context.value as? CheckoutContextState.Ready)?.context == context &&
            (actualContext == null || actualContext == context)
    override suspend fun review(context: CheckoutContext): CheckoutReviewResult = reviewBlock(++reviews)
}

private fun context(quantity: Int = 1, addressText: String = "Lima", generation: Long = 0): CheckoutContext =
    CheckoutContext("customer", generation, Cart("customer", listOf(
        CartProductSnapshot("business", "store", "product", "Plato", 1000, quantity),
    )), Address("address", "Casa", addressText, AddressCoordinates(-12.0, -77.0),
        "", "", 1L, 1L))
