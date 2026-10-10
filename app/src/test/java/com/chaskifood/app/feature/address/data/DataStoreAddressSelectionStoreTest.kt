package com.chaskifood.app.feature.address.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import okio.Path.Companion.toPath

class DataStoreAddressSelectionStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `seleccion se guarda en disco y se recupera al recrear DataStore`() = runTest {
        val file = File(temporaryFolder.root, "restart.preferences_pb")
        withStore(file) { it.select("A", "direccion-A") }
        withStore(file) { assertEquals("direccion-A", it.observe("A").first()) }
    }

    @Test
    fun `cuentas tienen claves independientes y limpiar una conserva la otra`() = runTest {
        withStore { store ->
            store.select("A", "direccion-A")
            store.select("B", "direccion-B")
            assertEquals("direccion-A", store.observe("A").first())
            assertEquals("direccion-B", store.observe("B").first())
            store.select("A", null)
            assertNull(store.observe("A").first())
            assertEquals("direccion-B", store.observe("B").first())
        }
    }

    @Test
    fun `reconciliar preserva seleccion vigente aunque cambie predeterminada`() = runTest {
        withStore { store ->
            store.select("A", "uno")
            assertEquals("uno", store.reconcile("A", setOf("uno", "dos"), "dos"))
            assertEquals("uno", store.observe("A").first())
        }
    }

    @Test
    fun `reconciliar seleccion borrada utiliza predeterminada y vacio limpia`() = runTest {
        withStore { store ->
            store.select("A", "borrada")
            assertEquals("vigente", store.reconcile("A", setOf("vigente"), "vigente"))
            assertNull(store.reconcile("A", emptySet(), null))
            assertNull(store.observe("A").first())
        }
    }

    @Test
    fun `sin referencia valida no elige primera direccion`() = runTest {
        withStore { store ->
            assertNull(store.reconcile("A", setOf("uno", "dos"), "inexistente"))
            assertNull(store.observe("A").first())
        }
    }

    @Test
    fun `reconciliacion concurrente no pierde una nueva seleccion valida`() = runTest {
        withStore { store ->
            repeat(10) {
                store.select("A", "uno")
                coroutineScope {
                    launch { store.reconcile("A", setOf("uno", "dos"), "uno") }
                    launch { store.select("A", "dos") }
                }
                assertEquals("dos", store.observe("A").first())
            }
        }
    }

    private suspend fun withStore(
        file: File = File(temporaryFolder.root, "selection.preferences_pb"),
        block: suspend (DataStoreAddressSelectionStore) -> Unit,
    ) {
        val job = SupervisorJob()
        // El backend FileStorage de Android usa renameTo, que no reemplaza en Windows.
        // Okio mantiene las mismas preferencias protobuf y hace el reemplazo atómico en JVM.
        val dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
            scope = CoroutineScope(job + Dispatchers.IO),
        )
        try {
            block(DataStoreAddressSelectionStore(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }
}
