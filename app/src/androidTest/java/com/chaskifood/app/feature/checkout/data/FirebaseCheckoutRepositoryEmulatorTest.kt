package com.chaskifood.app.feature.checkout.data

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.cart.data.mapper.toEntity
import com.chaskifood.app.feature.cart.data.mapper.toItemEntities
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.presentation.CheckoutViewModel
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import com.chaskifood.app.feature.search.domain.ProductOfferReviewSession
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Requiere Auth 9099, Firestore 8089 y Functions 5001 con demo-chaski-hu15. */
@RunWith(AndroidJUnit4::class)
class FirebaseCheckoutRepositoryEmulatorTest {
    companion object {
        private lateinit var fixture: CheckoutEmulatorFixture

        @BeforeClass @JvmStatic fun createFixture() { fixture = CheckoutEmulatorFixture() }
        @AfterClass @JvmStatic fun closeFixture() { fixture.close() }
    }

    @Before fun reset() = runBlocking { fixture.beginTest() }

    @Test fun compraValidaUsaPrecioVigenteYNoEscribePedidosNiCarrito() = runBlocking {
        val context = fixture.seed()
        val before = fixture.database.cartDao().getCart(fixture.uid)?.toDomain()
        val result = fixture.repository.review(context) as CheckoutReviewResult.Ready
        assertEquals(1_050L, result.quote.lines.single().unitPriceMinor)
        assertEquals(1_050L, result.quote.subtotalMinor)
        assertEquals(1_550L, result.quote.totalMinor)
        assertTrue(result.quote.hasPriceChanges)
        assertEquals(before, fixture.database.cartDao().getCart(fixture.uid)?.toDomain())
        assertEquals(0, fixture.collection("orders").optJSONArray("documents")?.length() ?: 0)
    }

    @Test fun productoAgotadoBloqueaCheckout() = runBlocking {
        val context = fixture.seed()
        fixture.updateConfiguration(availability = "OUT_OF_STOCK")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
    }

    @Test fun productoEliminadoBloqueaCheckout() = runBlocking {
        val context = fixture.seed()
        fixture.delete("products/${CheckoutEmulatorFixture.productId(1)}")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
    }

    @Test fun precioAumentadoYDisminuidoSeMuestranSinRedondeo() = runBlocking {
        val context = fixture.seed()
        fixture.updateConfiguration(price = 12.25)
        val increased = fixture.repository.review(context) as CheckoutReviewResult.Ready
        assertEquals(1_225L, increased.quote.subtotalMinor)
        assertEquals(1_725L, increased.quote.totalMinor)
        assertTrue(increased.quote.hasPriceChanges)
        fixture.updateConfiguration(price = 9.75)
        val decreased = fixture.repository.review(context) as CheckoutReviewResult.Ready
        assertEquals(975L, decreased.quote.subtotalMinor)
        assertEquals(1_475L, decreased.quote.totalMinor)
        assertTrue(decreased.quote.hasPriceChanges)
    }

    @Test fun configuracionAusenteBloqueaCheckout() = runBlocking {
        val context = fixture.seed()
        fixture.delete("store_product_configs/${CheckoutEmulatorFixture.STORE_ID}_${CheckoutEmulatorFixture.productId(1)}")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
    }

    @Test fun configuracionIncompletaNoPublicaReady() = runBlocking {
        val context = fixture.seed()
        fixture.updateConfiguration(availability = "INVALID")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Error)
    }

    @Test fun localCerradoOInactivoBloqueaCheckout() = runBlocking {
        val context = fixture.seed()
        fixture.updateStore(operationalStatus = "CLOSED")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
        fixture.updateStore(operationalStatus = "OPEN", status = "INACTIVE")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
    }

    @Test fun negocioSuspendidoConLocalActivoBloqueaCheckout() = runBlocking {
        val context = fixture.seed()
        fixture.updateBusiness("SUSPENDED")
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertTrue(result.reasons.single().contains("suspendido"))
        assertFalse(result.canChangeAddress)
    }

    @Test fun compradorNoPuedeLeerFichaPrivadaDelNegocio() = runBlocking {
        fixture.seed()
        val failure = runCatching {
            fixture.firestore.collection("business_requests")
                .document(CheckoutEmulatorFixture.BUSINESS_ID).get(Source.SERVER).await()
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (failure as FirebaseFirestoreException).code)
    }

    @Test fun direccionFueraDeCoberturaPermiteCambiarla() = runBlocking {
        val far = CheckoutEmulatorFixture.defaultAddress(location = AddressCoordinates(-12.5, -77.5))
        val context = fixture.seed(address = far)
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertTrue(result.canChangeAddress)
    }

    @Test fun direccionConCoordenadasInvalidasPermiteCambiarla() = runBlocking {
        val invalid = CheckoutEmulatorFixture.defaultAddress(location = AddressCoordinates(91.0, 0.0))
        val context = fixture.seed(address = invalid)
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertTrue(result.canChangeAddress)
    }

    @Test fun direccionNoSeleccionadaPermiteElegirOtra() = runBlocking {
        val context = fixture.seed(address = null)
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertTrue(result.canChangeAddress)
    }

    @Test fun perdidaDeCoberturaEnLecturaFinalPermiteCambiarDireccion() = runBlocking {
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            fixture.updateStore(radiusKm = 0.01)
        }
        fixture.createLocalCheckout(reader)
        val nearby = CheckoutEmulatorFixture.defaultAddress(
            location = AddressCoordinates(-12.002, -77.0))
        val context = fixture.seed(address = nearby)
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertTrue(result.canChangeAddress)
    }

    @Test fun cierreEnLecturaFinalNoHabilitaCambioDeDireccion() = runBlocking {
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            fixture.updateStore(operationalStatus = "CLOSED")
        }
        fixture.createLocalCheckout(reader)
        val context = fixture.seed()
        val result = fixture.repository.review(context) as CheckoutReviewResult.Blocked
        assertFalse(result.canChangeAddress)
    }

    @Test fun cambioDeCarritoDuranteLecturasDescartaCotizacion() = runBlocking {
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            val current = requireNotNull(fixture.database.cartDao().getCart(fixture.uid)).toDomain()
            val updated = current.withItems(current.items.map { it.copy(quantity = 2) })
            fixture.database.cartDao().saveCart(updated.toEntity(), updated.toItemEntities())
        }
        fixture.createLocalCheckout(reader)
        val context = fixture.seed()
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Error)
        assertEquals(2, fixture.database.cartDao().getCart(fixture.uid)?.items?.single()?.quantity)
    }

    @Test fun cambioDeDireccionDuranteLecturasDescartaCotizacion() = runBlocking {
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            fixture.selection.select(fixture.uid, "otra-direccion")
        }
        fixture.createLocalCheckout(reader)
        val context = fixture.seed()
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Error)
    }

    @Test fun logoutDuranteLecturasDescartaCotizacion() = runBlocking {
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            fixture.auth.signOut()
            fixture.sessions.current()
        }
        fixture.createLocalCheckout(reader)
        val context = fixture.seed()
        // La lectura que sigue al logout puede fallar por permisos antes de publicar SignedOut.
        assertFalse(fixture.repository.review(context) is CheckoutReviewResult.Ready)
        assertEquals(null, fixture.sessions.current().uid)
    }

    @Test fun logoutYLoginMismoUidNoRecuperaRevisionAnterior() = runBlocking {
        fixture.useEmailAccount()
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            fixture.auth.signOut()
            fixture.sessions.current()
            fixture.signInSameAccount()
            fixture.sessions.current()
        }
        fixture.createLocalCheckout(reader)
        val context = fixture.seed()
        val oldGeneration = context.sessionGeneration
        assertFalse(fixture.repository.review(context) is CheckoutReviewResult.Ready)
        assertTrue(fixture.sessions.current().generation > oldGeneration)
        assertEquals(context.ownerUid, fixture.sessions.current().uid)
    }

    @Test fun desconexionProduceErrorYRecuperacionPermiteNuevaRevision() = runBlocking {
        val context = fixture.seed()
        fixture.firestore.disableNetwork().await()
        try {
            assertTrue(withTimeout(15_000) { fixture.repository.review(context) }
                is CheckoutReviewResult.Error)
        } finally {
            fixture.firestore.enableNetwork().await()
        }
        assertTrue(withTimeout(20_000) { fixture.repository.review(context) }
            is CheckoutReviewResult.Ready)
    }

    @Test fun categoriasCompartidasSeLeenUnaVezPorRevisionPeroNoEntreRevisiones() = runBlocking {
        val context = fixture.seed(products = 5, categories = 3)
        val first = measuredReview(context)
        assertTrue(first.result is CheckoutReviewResult.Ready)
        assertEquals(15, first.sdkGets)
        assertEquals(3, first.categoryGets)
        assertEquals(2, first.storeGets)

        fixture.updateCategory(index = 2, isActive = false)
        val second = measuredReview(context)
        assertTrue(second.result is CheckoutReviewResult.Blocked)
        assertTrue(second.categoryGets >= 2)
    }

    @Test fun regresoCoalesceRevisionActivaYRevalidaDespuesDeReady() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reader = AfterFirstOfferReader(fixture.realOffers) {
            entered.complete(Unit)
            release.await()
        }
        fixture.createLocalCheckout(reader)
        fixture.seed()
        val model = CheckoutViewModel(fixture.repository)
        val store = ViewModelStore().apply { put("checkout", model) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync { model.review() }
            withTimeout(20_000) { entered.await() }
            instrumentation.runOnMainSync { model.review() }
            assertEquals(1, reader.calls.get())

            release.complete(Unit)
            withTimeout(20_000) { model.uiState.first { it.result is CheckoutReviewResult.Ready } }
            instrumentation.runOnMainSync { model.review() }
            withTimeout(20_000) { model.uiState.first {
                !it.loading && it.result is CheckoutReviewResult.Ready
            } }
            assertEquals(2, reader.calls.get())

            fixture.updateConfiguration(price = 12.25)
            instrumentation.runOnMainSync { model.review() }
            val updated = withTimeout(20_000) { model.uiState.first {
                (it.result as? CheckoutReviewResult.Ready)?.quote?.subtotalMinor == 1_225L
            } }
            assertTrue(updated.result is CheckoutReviewResult.Ready)
            assertEquals(3, reader.calls.get())
        } finally {
            release.complete(Unit)
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun estadosAdministrativosNoAprobadosYNegocioAusenteBloquean() = runBlocking {
        val context = fixture.seed()
        for (status in listOf("PENDING_REVIEW", "OBSERVED", "REJECTED")) {
            fixture.updateBusiness(status)
            assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
        }
        fixture.delete("business_requests/${CheckoutEmulatorFixture.BUSINESS_ID}")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Blocked)
        fixture.updateBusiness("UNKNOWN")
        assertTrue(fixture.repository.review(context) is CheckoutReviewResult.Error)
    }

    @Test fun cambiosDuranteHttpNoPublicanReady() = runBlocking {
        for (change in listOf("cart", "address", "session", "sameUid")) {
            fixture.beginTest()
            if (change == "sameUid") fixture.useEmailAccount()
            val context = fixture.seed()
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            fixture.afterHttpResponse = {
                entered.complete(Unit)
                check(release.await(8, TimeUnit.SECONDS)) { "La prueba no liberó HTTP" }
            }
            try {
                val review = async { fixture.repository.review(context) }
                withTimeout(20_000) { entered.await() }
                when (change) {
                    "cart" -> fixture.database.cartDao().deleteCart(fixture.uid)
                    "address" -> fixture.selection.select(fixture.uid, "another-address")
                    "sameUid" -> { fixture.auth.signOut(); fixture.signInSameAccount() }
                    else -> fixture.auth.signOut()
                }
                release.countDown()
                assertFalse(review.await() is CheckoutReviewResult.Ready)
            } finally {
                release.countDown()
                fixture.afterHttpResponse = null
            }
        }
    }

    @Test fun medirRevisionesConUnoCincoYDiezProductos() = runBlocking {
        for (count in listOf(1, 5, 10)) {
            val timedOffers = TimedOfferReader(fixture.realOffers)
            fixture.createLocalCheckout(timedOffers)
            val samples = mutableListOf<Pair<ReviewMetrics, ReviewMetrics>>()
            repeat(4) { run ->
                val context = fixture.seed(products = count)
                val beforeCart = fixture.database.cartDao().getCart(fixture.uid)?.toDomain()
                val initial = measuredReview(context, timedOffers)
                val repeated = measuredReview(context, timedOffers)
                assertTrue(initial.result is CheckoutReviewResult.Ready)
                assertTrue(repeated.result is CheckoutReviewResult.Ready)
                assertEquals(count, (initial.result as CheckoutReviewResult.Ready).quote.lines.size)
                assertEquals(count, initial.offerCalls)
                assertEquals(count, repeated.offerCalls)
                assertEquals(2 + 2 * count + 1, initial.sdkGets)
                assertEquals(initial.sdkGets, repeated.sdkGets)
                assertEquals(1, initial.categoryGets)
                assertEquals(2, initial.storeGets)
                assertEquals(2, initial.backendReads)
                assertEquals(beforeCart, fixture.database.cartDao().getCart(fixture.uid)?.toDomain())
                if (run > 0) samples += initial to repeated // Primera pareja: calentamiento.
            }
            println("HU15_CHECKOUT_OPT count=$count categories=1 " +
                "initial_ms=${samples.map { it.first.totalMs }} " +
                "return_ms=${samples.map { it.second.totalMs }} " +
                "initial_offer_ms=${samples.map { it.first.offerMs }} " +
                "return_offer_ms=${samples.map { it.second.offerMs }} " +
                "sdk_gets=${samples.first().first.sdkGets} " +
                "http_ms=${samples.map { it.first.httpMs }} backend_ms=${samples.map { it.first.backendMs }} " +
                "backend_reads=${samples.first().first.backendReads}")
        }
    }

    private suspend fun measuredReview(context: com.chaskifood.app.feature.checkout.domain.CheckoutContext,
                                       timedOffers: TimedOfferReader? = null): ReviewMetrics {
        val paths = mutableListOf<String>()
        var directStoreGets = 0
        fixture.realOffers.onServerGet = { paths += it }
        fixture.repository.onStoreGet = { directStoreGets++ }
        timedOffers?.reset()
        try {
            val start = SystemClock.elapsedRealtimeNanos()
            val result = withTimeout(60_000) { fixture.repository.review(context) }
            return ReviewMetrics(
                result = result,
                totalMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000,
                offerMs = timedOffers?.elapsedNanos?.div(1_000_000) ?: 0,
                offerCalls = timedOffers?.calls ?: 0,
                sdkGets = directStoreGets + paths.size,
                categoryGets = paths.count { it.startsWith("categories/") },
                storeGets = directStoreGets,
                httpMs = fixture.lastHttpMs,
                backendMs = fixture.lastBackendMs,
                backendReads = fixture.lastBackendReads,
            )
        } finally {
            fixture.realOffers.onServerGet = null
            fixture.repository.onStoreGet = null
        }
    }
}

private data class ReviewMetrics(
    val result: CheckoutReviewResult,
    val totalMs: Long,
    val offerMs: Long,
    val offerCalls: Int,
    val sdkGets: Int,
    val categoryGets: Int,
    val storeGets: Int,
    val httpMs: Long,
    val backendMs: Double,
    val backendReads: Int,
)

/** El lector real hace las lecturas; la pausa controlada permite cambiar una fuente entre ellas. */
private class AfterFirstOfferReader(
    private val delegate: ProductOfferReader,
    private val afterFirstRead: suspend () -> Unit,
) : ProductOfferReader {
    private val first = AtomicBoolean(true)
    val calls = AtomicInteger()
    override suspend fun read(storeId: String, productId: String,
                              deliveryLocation: AddressCoordinates): ProductOfferReadResult {
        calls.incrementAndGet()
        val result = delegate.read(storeId, productId, deliveryLocation)
        if (first.compareAndSet(true, false)) afterFirstRead()
        return result
    }

    override fun openCheckoutReview(verifiedStore: StoreDiscoveryCandidate,
                                    deliveryLocation: AddressCoordinates): ProductOfferReviewSession {
        val review = delegate.openCheckoutReview(verifiedStore, deliveryLocation)
        return ProductOfferReviewSession { productId ->
            calls.incrementAndGet()
            val result = review.read(productId)
            if (first.compareAndSet(true, false)) afterFirstRead()
            result
        }
    }
}

/** Mide solo la lectura de ofertas, no las dos lecturas directas del local hechas por Checkout. */
private class TimedOfferReader(private val delegate: ProductOfferReader) : ProductOfferReader {
    var calls = 0
        private set
    var elapsedNanos = 0L
        private set

    override suspend fun read(storeId: String, productId: String,
                              deliveryLocation: AddressCoordinates): ProductOfferReadResult {
        return timed { delegate.read(storeId, productId, deliveryLocation) }
    }

    override fun openCheckoutReview(verifiedStore: StoreDiscoveryCandidate,
                                    deliveryLocation: AddressCoordinates): ProductOfferReviewSession {
        val review = delegate.openCheckoutReview(verifiedStore, deliveryLocation)
        return ProductOfferReviewSession { productId -> timed { review.read(productId) } }
    }

    private suspend fun timed(block: suspend () -> ProductOfferReadResult): ProductOfferReadResult {
        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
        } finally {
            calls++
            elapsedNanos += SystemClock.elapsedRealtimeNanos() - start
        }
    }

    fun reset() {
        calls = 0
        elapsedNanos = 0L
    }
}
