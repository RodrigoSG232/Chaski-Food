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
        val model = AccessNavigationViewModel(context, identity).also { store.put("access", it) }
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
