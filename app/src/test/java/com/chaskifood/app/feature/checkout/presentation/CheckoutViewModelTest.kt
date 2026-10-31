package com.chaskifood.app.feature.checkout.presentation

import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.domain.CheckoutClock
import com.chaskifood.app.feature.checkout.domain.DeliveryQuote
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.address.domain.model.Address
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
import kotlinx.coroutines.test.advanceTimeBy
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
    private val clock = object : CheckoutClock {
        override fun wallTimeMillis(): Long = 1_000_000L + dispatcher.scheduler.currentTime
        override fun monotonicTimeMillis(): Long = 1_000L + dispatcher.scheduler.currentTime
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `un precio cambiado exige revision expresa y modalidad de pago`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(addressText = "Otra calle"))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertEquals(1, repository.reviews)
    }

    @Test fun `logout y nuevo login con mismo UID no conserva revision ni pago`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        repository.context.value = CheckoutContextState.Ready(context(quantity = 2))
        runCurrent()
        model.review()
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
        gate.complete(Unit)
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
    }

    @Test fun `cambiar de tienda durante cotizacion no publica la respuesta anterior`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val old = CheckoutReviewResult.Ready(quote(oldPrice = 1000))
        val newer = CheckoutReviewResult.Blocked(listOf("Nueva tienda pendiente de revisión"))
        val repository = FakeCheckoutRepository(old)
        repository.reviewBlock = { number ->
            if (number == 1) withContext(NonCancellable) { gate.await() }.let { old } else newer
        }
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()

        repository.context.value = CheckoutContextState.Ready(context(storeId = "other-store"))
        runCurrent()
        assertNull(model.uiState.value.result)
        model.review()
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
        gate.complete(Unit)
        runCurrent()
        assertEquals(newer, model.uiState.value.result)
        assertFalse(model.uiState.value.reviewComplete)
        assertEquals(2, repository.reviews)
    }

    @Test fun `dos ON_RESUME equivalentes comparten la revision en curso`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { gate.await(); repository.next }
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        model.review()
        runCurrent()
        assertEquals(1, repository.reviews)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Ready)
    }

    @Test fun `regreso despues de Ready vuelve a consultar la oferta`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        repository.next = CheckoutReviewResult.Blocked(listOf("Oferta agotada"))
        model.review()
        runCurrent()
        assertEquals(2, repository.reviews)
        assertEquals(repository.next, model.uiState.value.result)
    }

    @Test fun `ON_RESUME durante consulta revisa el contexto nuevo si cambia antes de terminar`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { number ->
            if (number == 1) withContext(NonCancellable) { gate.await() }
            if (number == 1) CheckoutReviewResult.Ready(quote(oldPrice = 1000))
            else CheckoutReviewResult.Blocked(listOf("Cambio de carrito"))
        }
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        model.review() // Equivalente mientras la primera lectura está pendiente.
        runCurrent()
        assertEquals(1, repository.reviews)
        repository.context.value = CheckoutContextState.Ready(context(quantity = 2))
        runCurrent()
        assertEquals(2, repository.reviews)
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Blocked)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Blocked)
    }

    @Test fun `cambio de carrito durante consulta descarta respuesta tardia`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        repository.reviewBlock = { withContext(NonCancellable) { gate.await() }; repository.next }
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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
        val model = CheckoutViewModel(repository, clock)
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

    @Test fun `cotizacion vence con la pantalla abierta y se puede revisar otra vez`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1200)))
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        assertTrue(model.uiState.value.reviewComplete)

        advanceTimeBy(265_000L)
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)
        assertFalse(model.uiState.value.reviewComplete)
        assertEquals(1, repository.reviews)

        repository.next = CheckoutReviewResult.Ready(quote(oldPrice = 1200))
        model.review()
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Ready)
        assertEquals(2, repository.reviews)
    }

    @Test fun `respuesta ya vencida nunca publica Ready`() = runTest {
        val old = quote(oldPrice = 1200).copy(deliveryQuoteDeadlineMillis = clock.monotonicTimeMillis() - 1)
        val model = CheckoutViewModel(FakeCheckoutRepository(CheckoutReviewResult.Ready(old)), clock)
        model.review()
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Error)
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `regreso durante llamada cancela intento previo y revisa otra vez`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1200)))
        repository.reviewBlock = { number ->
            if (number == 1) withContext(NonCancellable) { gate.await() }
            if (number == 1) CheckoutReviewResult.Error("Respuesta antigua") else repository.next
        }
        val model = CheckoutViewModel(repository, clock)
        model.review()
        runCurrent()
        model.onScreenPaused()
        model.review()
        runCurrent()
        assertEquals(2, repository.reviews)
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Ready)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.result is CheckoutReviewResult.Ready)
    }

    @Test fun `reutilizar quoteId tras relogin no restaura Ready ni consentimiento`() = runTest {
        val original = quote(oldPrice = 1000)
        val persisted = original.copy(deliveryQuote = original.deliveryQuote.copy(quoteId = "persisted-quote"))
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(persisted))
        val model = CheckoutViewModel(repository, clock)
        model.review(); runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        model.acceptPriceChanges(); runCurrent()
        assertTrue(model.uiState.value.reviewComplete)
        repository.context.value = CheckoutContextState.SignedOut; runCurrent()
        repository.context.value = CheckoutContextState.Ready(context().copy(sessionGeneration = 3)); runCurrent()
        assertFalse(model.uiState.value.result is CheckoutReviewResult.Ready)
        assertFalse(model.uiState.value.acceptedPriceChanges)
        assertFalse(model.uiState.value.reviewComplete)
        model.review(); runCurrent()
        val ready = model.uiState.value.result as CheckoutReviewResult.Ready
        assertEquals("persisted-quote", ready.quote.deliveryQuote.quoteId)
        assertFalse(model.uiState.value.acceptedPriceChanges)
        assertFalse(model.uiState.value.reviewComplete)
    }

    private fun quote(oldPrice: Long) = CheckoutQuote(
        ownerUid = "customer", storeId = "store", storeName = "Local",
        addressId = "address", addressLabel = "Casa", addressText = "Lima",
        lines = listOf(CheckoutLine("product", "Plato", 2, oldPrice, 1200, 2400)),
        subtotalMinor = 2400, deliveryFeeMinor = 380, totalMinor = 2780,
        deliveryQuote = DeliveryQuote(1_918.8, 380, CartCurrency.PEN, "v1",
            clock.wallTimeMillis(), clock.wallTimeMillis() + 300_000L,
            AddressCoordinates(-12.0, -77.0), AddressCoordinates(-12.0, -77.0)),
        deliveryQuoteDeadlineMillis = clock.monotonicTimeMillis() + 265_000L,
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

private fun context(quantity: Int = 1, addressText: String = "Lima", generation: Long = 0,
                    storeId: String = "store"): CheckoutContext =
    CheckoutContext("customer", generation, Cart("customer", listOf(
        CartProductSnapshot("business", storeId, "product", "Plato", 1000, quantity),
    )), Address("address", "Casa", addressText, AddressCoordinates(-12.0, -77.0),
        "", "", 1L, 1L))
