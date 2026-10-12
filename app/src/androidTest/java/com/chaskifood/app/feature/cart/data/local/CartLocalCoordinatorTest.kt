package com.chaskifood.app.feature.cart.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import com.chaskifood.app.feature.cart.data.CartSession
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.cart.data.mapper.toEntity
import com.chaskifood.app.feature.cart.data.mapper.toItemEntities
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartChangeRejectionReason
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.cart.domain.CartState
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartLocalCoordinatorTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "cart-coordinator-${UUID.randomUUID()}.db"
    private lateinit var database: ChaskiDatabase
    private lateinit var session: FakeSession
    private lateinit var coordinator: CartLocalCoordinator

    @Before
    fun setUp() {
        database = Room.databaseBuilder(context, ChaskiDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2).build()
        session = FakeSession("a")
        coordinator = CartLocalCoordinator(database, database.cartDao(), session)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun cincuentaIncorporacionesConcurrentesConDosCoordinadoresNoPierdenIncrementos() = runBlocking {
        database.cartDao().saveCart(Cart("a", listOf(item())).toEntity(),
            Cart("a", listOf(item())).toItemEntities())
        val second = CartLocalCoordinator(database, database.cartDao(), session)
        val start = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        val started = java.util.concurrent.atomic.AtomicInteger()

        val jobs = (1..50).map { number ->
            async(Dispatchers.Default) {
                if (started.incrementAndGet() == 50) ready.complete(Unit)
                start.await()
                val actor = if (number % 2 == 0) coordinator else second
                actor.addValidatedItem(session.current(), item())
            }
        }
        withTimeout(10_000) { ready.await() }
        start.complete(Unit)
        assertTrue(withTimeout(30_000) { jobs.awaitAll().all { it is CartOperationResult.Updated } })
        assertEquals(51, stored("a").items.single().quantity)
        assertEquals(5_151L, stored("a").subtotalMinor)
        database.close()
        database = Room.databaseBuilder(context, ChaskiDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2).build()
        assertEquals(51, stored("a").items.single().quantity)
    }

    @Test
    fun reduccionConservaPrecioEliminacionVaciaCarritoYAumentoRequiereValidacion() = runBlocking {
        val original = item(quantity = 4, price = 225)
        assertTrue(coordinator.addValidatedItem(session.current(), original) is CartOperationResult.Updated)
        assertEquals(CartOperationResult.RemoteValidationRequired, coordinator.decreaseQuantity("p", 5))
        val reduced = coordinator.decreaseQuantity("p", 2) as CartOperationResult.Updated
        assertEquals(225L, reduced.cart.items.single().unitPriceMinor)
        assertEquals(450L, reduced.cart.subtotalMinor)
        assertEquals(CartOperationResult.RuleRejected(CartChangeRejectionReason.INVALID_QUANTITY),
            coordinator.decreaseQuantity("p", 0))
        assertTrue(coordinator.removeProduct("p") is CartOperationResult.Updated)
        assertTrue(stored("a").items.isEmpty())
        assertNull(stored("a").storeId)
    }

    @Test
    fun confirmacionCanceladaNoEscribeYElTokenSoloSeUsaUnaVez() = runBlocking {
        coordinator.addValidatedItem(session.current(), item())
        val requested = coordinator.addValidatedItem(session.current(), item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        assertEquals("store", stored("a").storeId)
        assertTrue(coordinator.cancelConfirmation(requested.token))
        assertFalse(coordinator.cancelConfirmation(requested.token))
        assertEquals(CartOperationResult.ConfirmationStale,
            coordinator.confirmValidatedItem(session.current(), requested.token, requested.incomingItem))
        assertEquals("store", stored("a").storeId)

        val fresh = coordinator.addValidatedItem(session.current(), item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        assertTrue(coordinator.confirmValidatedItem(session.current(), fresh.token, fresh.incomingItem)
            is CartOperationResult.Updated)
        assertEquals(CartOperationResult.ConfirmationStale,
            coordinator.confirmValidatedItem(session.current(), fresh.token, fresh.incomingItem))
        assertEquals("other", stored("a").storeId)
    }

    @Test
    fun cambiosYRetornoAlContenidoAnteriorInvalidanConfirmacion() = runBlocking {
        coordinator.addValidatedItem(session.current(), item())
        val request = coordinator.addValidatedItem(session.current(), item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        coordinator.addValidatedItem(session.current(), item(product = "temporary"))
        coordinator.removeProduct("temporary")
        assertEquals(request.currentCart, stored("a"))
        assertEquals(CartOperationResult.ConfirmationStale,
            coordinator.confirmValidatedItem(session.current(), request.token, request.incomingItem))
        assertEquals("store", stored("a").storeId)
    }

    @Test
    fun precioNuevoYReinicioDescartanConfirmacionTemporal() = runBlocking {
        coordinator.addValidatedItem(session.current(), item())
        val request = coordinator.addValidatedItem(session.current(), item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        assertEquals(CartOperationResult.ConfirmationStale,
            coordinator.confirmValidatedItem(session.current(), request.token,
                request.incomingItem.copy(unitPriceMinor = 200)))
        assertEquals("store", stored("a").storeId)
        val another = coordinator.addValidatedItem(session.current(), item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        val afterRestart = CartLocalCoordinator(database, database.cartDao(), session)
        assertNull(afterRestart.pendingConfirmation(another.token))
    }

    @Test
    fun cambioDeUidYLogoutLoginDelMismoUidNoAplicanResultadosTardios() = runBlocking {
        val capturedA = session.current()
        coordinator.addValidatedItem(capturedA, item())
        val request = coordinator.addValidatedItem(capturedA, item(store = "other"))
            as CartOperationResult.ConfirmationRequired
        session.change("b")
        assertEquals(CartOperationResult.SessionChanged, coordinator.addValidatedItem(capturedA, item()))
        assertNull(database.cartDao().getCart("b"))
        assertNull(coordinator.pendingConfirmation(request.token))
        val b = coordinator.addValidatedItem(session.current(), item(store = "b-store"))
        assertTrue(b is CartOperationResult.Updated)
        assertEquals("store", stored("a").storeId)
        assertEquals("b-store", stored("b").storeId)

        session.change(null)
        session.change("a")
        assertEquals(CartOperationResult.SessionChanged, coordinator.addValidatedItem(capturedA, item()))
        assertNotEquals(capturedA, session.current())
        assertEquals("store", stored("a").storeId)
    }

    @Test
    fun observacionMuestraSoloElUidActualYSeOcultaAlSalir() = runBlocking {
        coordinator.addValidatedItem(session.current(), item())
        assertEquals("a", withTimeout(5_000) {
            (coordinator.observeCart().first { it is CartState.Ready } as CartState.Ready).cart.ownerUid
        })
        session.change(null)
        assertEquals(CartState.SignedOut, withTimeout(5_000) { coordinator.observeCart().first() })
        session.change("b")
        val b = withTimeout(5_000) {
            coordinator.observeCart().first { it is CartState.Ready } as CartState.Ready
        }
        assertEquals("b", b.cart.ownerUid)
        assertTrue(b.cart.items.isEmpty())
        assertEquals(1, stored("a").items.single().quantity)
    }

    @Test
    fun falloDeInsercionRevierteLaOperacionCompleta() = runBlocking {
        val original = Cart("a", listOf(item()))
        database.cartDao().saveCart(original.toEntity(), original.toItemEntities())
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_cart_insert BEFORE INSERT ON cart_items " +
                "BEGIN SELECT RAISE(ABORT, 'prueba de rollback'); END",
        )
        assertEquals(CartOperationResult.StorageError,
            coordinator.addValidatedItem(session.current(), item()))
        assertEquals(original, stored("a"))
    }

    @Test
    fun cancelacionMientrasRoomEstaOcupadoNoGuardaCambios() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) {
            database.withTransaction {
                entered.complete(Unit)
                release.await()
            }
        }
        withTimeout(5_000) { entered.await() }
        val writer = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.addValidatedItem(session.current(), item())
        }
        writer.cancel()
        release.complete(Unit)
        withTimeout(5_000) { writer.cancelAndJoin(); holder.await() }
        assertNull(database.cartDao().getCart("a"))
    }

    private suspend fun stored(uid: String): Cart =
        database.cartDao().getCart(uid)?.toDomain() ?: Cart(uid)

    private fun item(
        product: String = "p",
        store: String = "store",
        quantity: Int = 1,
        price: Long = 101,
    ) = CartProductSnapshot(
        businessId = "business", storeId = store, productId = product,
        productName = product, unitPriceMinor = price, quantity = quantity,
    )

    private class FakeSession(initialUid: String?) : CartSessionSource {
        private val state = MutableStateFlow(CartSession(initialUid, 0))
        override val sessions: StateFlow<CartSession> = state
        override fun current(): CartSession = state.value
        fun change(uid: String?) { state.value = CartSession(uid, state.value.generation + 1) }
    }
}
