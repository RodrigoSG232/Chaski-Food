package com.chaskifood.app.feature.cart.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.cart.data.mapper.toEntity
import com.chaskifood.app.feature.cart.data.mapper.toItemEntities
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartDaoTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cart-dao-test-${UUID.randomUUID()}.db"
    private lateinit var database: ChaskiDatabase

    @Before
    fun setUp() {
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun baseNuevaV3AdmiteCarritoVacioYConservaAppConfig() = runBlocking {
        val uid = "uid-a"
        assertEquals(3, database.openHelper.readableDatabase.version)
        assertNull(database.cartDao().getCart(uid))
        val empty = Cart(uid)

        database.cartDao().saveCart(empty.toEntity(), empty.toItemEntities())
        database.appConfigDao().upsert(com.chaskifood.app.core.database.AppConfigEntity("test", "sample"))

        assertEquals(empty, database.cartDao().getCart(uid)?.toDomain())
        assertEquals("sample", database.appConfigDao().getValue("test"))
    }

    @Test
    fun dosUidConMismoProductoPermanecenAisladosYBorradoEsLimitado() = runBlocking {
        val a = cart("uid-a", item("shared", 199L, 2))
        val b = cart("uid-b", item("shared", 250L, 3))
        val dao = database.cartDao()
        dao.saveCart(a.toEntity(), a.toItemEntities())
        dao.saveCart(b.toEntity(), b.toItemEntities())

        val changedA = cart("uid-a", item("other", 300L, 1))
        dao.saveCart(changedA.toEntity(), changedA.toItemEntities())
        assertEquals(changedA, dao.getCart("uid-a")?.toDomain())
        assertEquals(b, dao.getCart("uid-b")?.toDomain())

        dao.deleteCart("uid-a")
        assertEquals(Cart("uid-a"), dao.getCart("uid-a")?.toDomain())
        assertEquals(3L, dao.getCart("uid-a")?.cart?.revision)
        assertEquals(b, dao.getCart("uid-b")?.toDomain())
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM cart_items WHERE ownerUid = 'uid-a'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun conservaOrdenCantidadYCentimosAlCerrarYReabrir() = runBlocking {
        val original = cart(
            "uid-a",
            item("product-b", 123L, 2),
            item("product-a", 999L, 3),
        )
        database.cartDao().saveCart(original.toEntity(), original.toItemEntities())

        database.close()
        database = openDatabase()

        val restored = database.cartDao().getCart("uid-a")?.toDomain()
        assertEquals(original, restored)
        assertEquals(listOf("product-b", "product-a"), restored?.items?.map { it.productId })
        assertEquals(3243L, restored?.subtotalMinor)
    }

    @Test
    fun flowObservaCambiosDeSuUid() = runBlocking {
        val dao = database.cartDao()
        assertNull(withTimeout(5_000) { dao.observeCart("uid-a").first() })
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { dao.observeCart("uid-a").first { it?.items?.size == 1 } }
        }
        val a = cart("uid-a", item("product-a", 101L, 1))
        dao.saveCart(a.toEntity(), a.toItemEntities())

        assertEquals(a, observed.await()?.toDomain())
        assertNull(withTimeout(5_000) { dao.observeCart("uid-b").first() })
    }

    @Test
    fun errorEnInsercionRevierteCabeceraYLineas() = runBlocking {
        val dao = database.cartDao()
        val original = cart("uid-a", item("product-a", 200L, 2))
        dao.saveCart(original.toEntity(), original.toItemEntities())
        val row = original.toItemEntities().single()

        val failure = runCatching {
            dao.saveCart(
                original.toEntity().copy(storeId = "otro-local"),
                listOf(row, row.copy(position = 1)),
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(original, dao.getCart("uid-a")?.toDomain())
    }

    private fun openDatabase(): ChaskiDatabase = Room.databaseBuilder(
        context,
        ChaskiDatabase::class.java,
        name,
    ).addMigrations(MIGRATION_1_2).build()

    private fun cart(uid: String, vararg items: CartProductSnapshot) = Cart(uid, items.toList())

    private fun item(productId: String, price: Long, quantity: Int) = CartProductSnapshot(
        businessId = "business-a",
        storeId = "store-a",
        productId = productId,
        productName = "Producto $productId",
        unitPriceMinor = price,
        quantity = quantity,
    )
}
