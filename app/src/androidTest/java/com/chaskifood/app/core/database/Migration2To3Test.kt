package com.chaskifood.app.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import com.chaskifood.app.core.database.migration.MIGRATION_2_3
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class Migration2To3Test {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "hu16-migration-${UUID.randomUUID()}.db"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ChaskiDatabase::class.java)
    @After fun clean() { context.deleteDatabase(name) }
    @Test fun directOneToThreePreservesAppConfigAndValidatesExportedSchema() = runBlocking<Unit> {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO app_config (`key`,`value`) VALUES ('preserved','from-v1')"); close()
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3).close()
        Room.databaseBuilder(context, ChaskiDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().withClose { db ->
            assertEquals("from-v1", db.appConfigDao().getValue("preserved"))
            assertEquals(3, db.openHelper.readableDatabase.version)
            assertNull(db.checkoutIntentDao().get("a", "missing"))
        }
    }
    @Test fun twoToThreePreservesBothUsersLinesMoneyPositionsAndEmptyHeader() = runBlocking<Unit> {
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO app_config (`key`,`value`) VALUES ('preserved','from-v2')")
            execSQL("INSERT INTO carts VALUES ('a','business','store','PEN')")
            execSQL("INSERT INTO carts VALUES ('b',NULL,NULL,'PEN')")
            execSQL("INSERT INTO cart_items VALUES ('a','p2','Segundo',123,2,0)")
            execSQL("INSERT INTO cart_items VALUES ('a','p1','Primero',999,3,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()
        Room.databaseBuilder(context, ChaskiDatabase::class.java, name).addMigrations(MIGRATION_2_3).build().withClose { db ->
            assertEquals("from-v2", db.appConfigDao().getValue("preserved"))
            val cart = db.cartDao().getCart("a")!!
            assertEquals(0L, cart.cart.revision)
            assertEquals(listOf("p2", "p1"), cart.toDomain().items.map { it.productId })
            assertEquals(3243L, cart.toDomain().subtotalMinor)
            assertEquals(0L, db.cartDao().getCart("b")!!.cart.revision)
            assertTrue(db.cartDao().getCart("b")!!.items.isEmpty())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    @Test fun chainOneToTwoWithExistingCartThenThreeRetainsData() = runBlocking<Unit> {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO app_config (`key`,`value`) VALUES ('chain','v1')"); close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).apply {
            execSQL("INSERT INTO carts VALUES ('a','business','store','PEN')")
            execSQL("INSERT INTO cart_items VALUES ('a','p','Plato',1000,2,0)"); close()
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).apply {
            query("SELECT revision FROM carts WHERE ownerUid='a'").use { assertTrue(it.moveToFirst()); assertEquals(0L,it.getLong(0)) }
            query("SELECT quantity FROM cart_items WHERE ownerUid='a'").use { assertTrue(it.moveToFirst()); assertEquals(2,it.getInt(0)) }
            close()
        }
    }
}

private inline fun <T> ChaskiDatabase.withClose(block: (ChaskiDatabase) -> T): T = try { block(this) } finally { close() }
