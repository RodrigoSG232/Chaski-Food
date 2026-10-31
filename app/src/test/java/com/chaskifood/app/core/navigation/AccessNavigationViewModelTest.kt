package com.chaskifood.app.core.navigation

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessNavigationViewModelTest {
    private class Fixture(test: TestScope) {
        val store = ViewModelStore()
        val events = MutableSharedFlow<IdentityObservation>(extraBufferCapacity = 10)
        var revision = 0L
        var silentRefresh = false
        val driver = object : AccessIdentityDriver {
            override fun observe(): Flow<IdentityObservation> = events
            override suspend fun refresh() {
                if (!silentRefresh) events.emit(IdentityObservation.Authenticated("login", "a", "a@example.test",
                    EmailVerification.VERIFIED, credentialRevision = ++revision))
            }
        }
        var reads = 0
        var read: suspend (AccessIdentityState) -> AccessFactsResult = { client(it) }
        fun client(state: AccessIdentityState) = AccessFactsResult(AccessFacts.Checked(
            (state.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 1)
        val identity = SharedAccessIdentitySource(driver, test.backgroundScope)
        val context = SharedAccessContext(identity, object : AccessFactsReader {
            override suspend fun read(identity: AccessIdentityState): AccessFactsResult { reads++; return this@Fixture.read.invoke(identity) }
        }, test.backgroundScope, true)
        val bound = mutableListOf<String>()
        var bind: suspend (String) -> Unit = {}
        var signOuts = 0
        val model = AccessNavigationViewModel(context, identity, { uid -> bound += uid; bind(uid) },
            { signOuts++; true }).also { store.put("access", it) }
        fun reconciliation() = AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.RECONCILIATION_REQUIRED)
        suspend fun login(uid: String, key: String = "login-$uid", verification: EmailVerification = EmailVerification.VERIFIED) =
            events.emit(IdentityObservation.Authenticated(key, uid, "$uid@example.test", verification, credentialRevision = ++revision))
    }
    private fun bindingTest(body: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try { runCurrent(); body(f) } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `invitacion pendiente se vincula una vez y concede acceso tras hechos nuevos`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        f.bind = { f.read = { f.client(it) } }
        f.driver.refresh(); runCurrent()
        assertEquals(listOf("a"), f.bound)
        assertEquals(AccessContextPhase.RESOLVED, f.model.states.value.phase)
    }
    @Test fun `conflicto real no reintenta automaticamente ni entra en bucle`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        f.bind = { error("BINDING_OWNED_BY_ANOTHER_UID") }
        f.driver.refresh(); runCurrent()
        f.model.refresh(); runCurrent(); f.model.refresh(); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(listOf("a"), f.bound)
        assertEquals(AccessCheckProblem.RECONCILIATION_REQUIRED, f.model.states.value.problem)
    }
    @Test fun `fallo temporal permite reintento explicito controlado`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        f.bind = { error("SERVICE_UNAVAILABLE") }
        f.driver.refresh(); runCurrent(); assertEquals(1, f.bound.size)
        f.bind = { f.read = { f.client(it) } }
        f.model.retry(); runCurrent()
        assertEquals(2, f.bound.size); assertEquals(AccessContextPhase.RESOLVED, f.model.states.value.phase)
    }
    @Test fun `correo sin verificar no intenta y verificarlo habilita un intento nuevo`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        f.bind = { f.read = { f.client(it) } }
        f.login("a", "login", EmailVerification.UNVERIFIED); runCurrent()
        assertTrue(f.bound.isEmpty())
        f.login("a", "login", EmailVerification.VERIFIED); runCurrent()
        assertEquals(listOf("a"), f.bound); assertEquals(AccessContextPhase.RESOLVED, f.model.states.value.phase)
    }
    @Test fun `cambio de cuenta cancela la vinculacion anterior y no la aplica a la nueva`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        var cancelled = false
        f.bind = { uid -> if (uid == "a") try { awaitCancellation() } finally { cancelled = true } }
        f.login("a"); runCurrent(); assertEquals(listOf("a"), f.bound)
        f.read = { f.client(it) }
        f.login("b"); runCurrent()
        assertTrue(cancelled)
        assertEquals("b", f.model.states.value.origin.session?.uid); assertEquals(AccessContextPhase.RESOLVED, f.model.states.value.phase)
        assertEquals(listOf("a"), f.bound)
    }
    @Test fun `cerrar sesion desde el panel usa el cierre central y descarta la vinculacion`() = bindingTest { f ->
        f.read = { f.reconciliation() }
        var cancelled = false
        f.bind = { try { awaitCancellation() } finally { cancelled = true } }
        f.driver.refresh(); runCurrent()
        var protected: Boolean? = null
        f.model.signOut { protected = it }; runCurrent()
        assertTrue(cancelled); assertEquals(1, f.signOuts); assertEquals(true, protected)
        assertTrue(AuthorizationPanel.RECONCILIATION.offersSignOut()); assertTrue(AuthorizationPanel.CONFLICT.offersSignOut())
        assertFalse(AuthorizationPanel.VERIFYING.offersSignOut()); assertFalse(AuthorizationPanel.ACCOUNT_CHANGED.offersSignOut())
    }
    @Test fun `refrescos equivalentes simultaneos comparten consulta`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try {
            runCurrent(); f.driver.refresh(); runCurrent()
            val pending = CompletableDeferred<AccessFactsResult>()
            var captured: AccessIdentityState? = null
            f.read = { captured = it; pending.await() }
            f.model.refresh(false); runCurrent()
            f.model.refresh(false); f.model.refresh(false); runCurrent()
            assertEquals(2, f.reads)
            pending.complete(f.client(checkNotNull(captured))); runCurrent()
            assertTrue(f.model.allows(ChaskiDestinations.CART))
            assertEquals(2, f.reads)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `mutacion fuerza hechos nuevos y respuesta anterior no recupera permisos`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try {
            runCurrent(); f.driver.refresh(); runCurrent()
            val old = CompletableDeferred<AccessFactsResult>()
            var oldIdentity: AccessIdentityState? = null
            f.read = { state -> oldIdentity = state; withContext(NonCancellable) { old.await() } }
            f.model.refresh(); runCurrent()
            f.read = { state -> AccessFactsResult(AccessFacts.Checked(
                (state.identity as AccessIdentity.Authenticated).session,
                AccessOwnership("business", "a", AccessBusinessStatus.SUSPENDED), emptyList(), emptyList(), AccessExclusivity.VERIFIED), 2) }
            f.model.refresh(); runCurrent()
            assertFalse(f.model.allows(ChaskiDestinations.CART))
            assertTrue(f.model.allows(ChaskiDestinations.BUSINESS_STATUS))
            old.complete(f.client(checkNotNull(oldIdentity))); runCurrent()
            assertFalse(f.model.allows(ChaskiDestinations.CART))
            assertEquals(3, f.reads)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `identidad fallida o refresh sin respuesta termina restringido sin carga infinita`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try {
            runCurrent(); f.driver.refresh(); runCurrent()
            f.events.emit(IdentityObservation.Failed); runCurrent()
            assertEquals(AccessContextPhase.ERROR, f.model.states.value.phase)
            assertFalse(f.model.allows(ChaskiDestinations.CART))
            f.driver.refresh(); runCurrent()
            f.silentRefresh = true
            f.model.refresh(); runCurrent(); advanceTimeBy(25_001); runCurrent()
            assertEquals(AccessContextPhase.ERROR, f.model.states.value.phase)
            assertFalse(f.model.allows(ChaskiDestinations.CART))
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `evento diferido de revision anterior no despacha aunque el permiso siga concedido`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try {
            runCurrent(); f.driver.refresh(); runCurrent()
            val previous = f.context.currentState().origin
            f.driver.refresh(); runCurrent()
            assertTrue(f.model.allows(ChaskiDestinations.CART))
            var actions = 0
            f.model.runIfAllowedFrom(previous, ChaskiDestinations.CART) { actions++ }
            assertEquals(0, actions)
            f.model.runIfAllowedFrom(f.context.currentState().origin, ChaskiDestinations.CART) { actions++ }
            assertEquals(1, actions)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }}
