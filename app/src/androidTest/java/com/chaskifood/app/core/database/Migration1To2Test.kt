package com.chaskifood.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cart-migration-test-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChaskiDatabase::class.java,
    )

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun migracionConservaFilasDeAppConfigYAnadeTablasCart() = runBlocking {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO app_config (`key`, `value`) VALUES ('test-key', 'sample-value')")
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val database = Room.databaseBuilder(context, ChaskiDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals("sample-value", database.appConfigDao().getValue("test-key"))
            assertNull(database.cartDao().getCart("uid-a"))
            assertEquals(2, database.openHelper.readableDatabase.version)
            database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue(!cursor.moveToFirst())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migracionAdmiteAppConfigVacia() = runBlocking {
        helper.createDatabase(name, 1).close()

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val database = Room.databaseBuilder(context, ChaskiDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
        try {
            assertNull(database.appConfigDao().getValue("missing"))
            assertNull(database.cartDao().getCart("uid-a"))
        } finally {
            database.close()
        }
    }
}
