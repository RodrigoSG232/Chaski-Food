package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAccessContextTest {
    private class Driver : AccessIdentityDriver {
        val events = MutableSharedFlow<IdentityObservation>(extraBufferCapacity = 20)
        var last = IdentityObservation.Authenticated("login", "a", "a@example.test", EmailVerification.VERIFIED)
        var opened = 0; var closed = 0; var credentialCurrent = true
        override fun runIfCurrent(state: AccessIdentityState, publish: () -> Unit): Boolean { if (!credentialCurrent) return false; publish(); return true }
        override fun observe(): Flow<IdentityObservation> = flow { opened++; try { emitAll(events) } finally { closed++ } }
        override suspend fun refresh() { events.emit(IdentityObservation.Loading); last = last.copy(credentialRevision = last.credentialRevision + 1); events.emit(last) }
        suspend fun login(uid: String = "a", key: String = "login", email: String = "$uid@example.test", verified: EmailVerification = EmailVerification.VERIFIED) {
            last = IdentityObservation.Authenticated(key, uid, email, verified); events.emit(last)
        }
    }
    private class Reader : AccessFactsReader {
        var calls = 0
        var read: suspend (AccessIdentityState) -> AccessFactsResult = { state ->
            AccessFactsResult(AccessFacts.Checked((state.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 5)
        }
        override suspend fun read(identity: AccessIdentityState): AccessFactsResult { calls++; return read.invoke(identity) }
    }
    private class Fixture(test: TestScope, enabled: Boolean = true) {
        val driver = Driver(); val reader = Reader()
        val source = SharedAccessIdentitySource(driver, test.backgroundScope)
        // Reloj monotono = tiempo virtual: lease, renovacion (a 833 ms) y caducidad (a 1000 ms) son coherentes.
        val context = SharedAccessContext(source, reader, test.backgroundScope, enabled, 1_000) { test.testScheduler.currentTime }
    }
    private fun checked(state: AccessIdentityState, revision: Long = 5) =
        AccessFactsResult(AccessFacts.Checked((state.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), revision)
    @Test fun `varios consumidores comparten driver y una consulta`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        repeat(3) { backgroundScope.launch { f.context.states.collect {} } }; runCurrent()
        assertEquals(1, f.driver.opened); assertEquals(1, f.reader.calls)
        assertTrue(f.context.states.value.resolution is AccessResolution.Resolved)
        backgroundScope.cancel(); runCurrent(); assertEquals(1, f.driver.closed)
    }
    @Test fun `logout quita capacidades y relogin mismo UID cambia generacion`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent(); val before = f.context.states.value.origin
        f.driver.events.emit(IdentityObservation.SignedOut); runCurrent(); assertEquals(AccessResolution.SignedOut, f.context.states.value.resolution)
        f.driver.login(key = "new-login"); runCurrent(); assertTrue(f.context.states.value.origin.generation > before.generation)
    }
    @Test fun `otra cuenta nunca recibe resultado antiguo ni error antiguo`() = runTest {
        for (failed in listOf(false, true)) {
            val f = Fixture(this); val pending = CompletableDeferred<AccessFactsResult>(); runCurrent()
            f.reader.read = { state -> if ((state.identity as AccessIdentity.Authenticated).session.uid == "a") withContext(NonCancellable) { pending.await() }
                else AccessFactsResult(AccessFacts.Checked(state.identity.session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 9) }
            f.driver.login(); runCurrent(); f.driver.login("b", "login-b"); runCurrent(); val current = f.context.states.value
            val session = AccessSession("a", 1)
            pending.complete(if (failed) AccessFactsResult(AccessFacts.Failed) else AccessFactsResult(AccessFacts.Checked(session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 8))
            runCurrent(); assertEquals(current, f.context.states.value); assertEquals("b", current.origin.session?.uid)
        }
    }
    @Test fun `mismo UID otra generacion descarta respuesta pendiente`() = runTest {
        val f = Fixture(this); val pending = CompletableDeferred<AccessFactsResult>(); runCurrent()
        f.reader.read = { state -> if (state.origin.generation == 1L) withContext(NonCancellable) { pending.await() }
            else AccessFactsResult(AccessFacts.Checked((state.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 7) }
        f.driver.login(); runCurrent(); f.driver.events.emit(IdentityObservation.SignedOut); runCurrent(); f.driver.login(key = "again"); runCurrent()
        val current = f.context.states.value; pending.complete(AccessFactsResult(AccessFacts.Failed)); runCurrent(); assertEquals(current, f.context.states.value)
    }
    @Test fun `correo y verificacion cambian revision y requieren nuevos hechos`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent(); val origin = f.context.states.value.origin
        f.driver.login(email = "new@example.test", verified = EmailVerification.UNVERIFIED); runCurrent()
        assertEquals(origin.generation, f.context.states.value.origin.generation); assertTrue(f.context.states.value.origin.revision > origin.revision)
        assertEquals(2, f.reader.calls)
    }
    @Test fun `refrescar invalida antes de esperar red y no restaura cache`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent(); val pending = CompletableDeferred<AccessFactsResult>()
        f.reader.read = { pending.await() }; f.context.refresh()
        assertFalse(f.context.states.value.resolution is AccessResolution.Resolved); runCurrent()
        pending.complete(AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE)); runCurrent()
        assertTrue(f.context.states.value.resolution is AccessResolution.Error); assertNull(f.context.states.value.serverRevision)
    }
    @Test fun `reconexion con refresco explicito recupera error`() = runTest {
        val f = Fixture(this); runCurrent(); f.reader.read = { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
        f.driver.login(); runCurrent(); assertTrue(f.context.states.value.resolution is AccessResolution.Error)
        f.reader.read = { AccessFactsResult(AccessFacts.Checked((it.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 6) }
        f.context.refresh(); runCurrent(); assertTrue(f.context.states.value.resolution is AccessResolution.Resolved)
    }
    @Test fun `renovacion correcta mantiene permisos sin pasar por error ni carga`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val phases = mutableListOf<AccessContextPhase>()
        backgroundScope.launch { f.context.states.collect { phases += it.phase } }; runCurrent()
        advanceTimeBy(834); runCurrent(); assertEquals(2, f.reader.calls)
        advanceTimeBy(5_000); runCurrent()
        assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
        assertEquals(listOf(AccessContextPhase.RESOLVED), phases.distinct())
        assertEquals(8, f.reader.calls) // inicial + una renovacion cada 833 ms hasta 5834 ms, nunca simultaneas
    }
    @Test fun `fallo de red en renovacion conserva lease hasta caducar y no reintenta`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
        advanceTimeBy(834); runCurrent(); assertEquals(2, f.reader.calls)
        assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
        advanceTimeBy(166); runCurrent()
        assertEquals(AccessCheckProblem.EXPIRED, f.context.states.value.problem); assertTrue(f.context.states.value.resolution is AccessResolution.Error)
        advanceTimeBy(10_000); runCurrent(); assertEquals(2, f.reader.calls)
    }
    @Test fun `renovacion sin respuesta caduca a su hora sin extender la lease`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { awaitCancellation() }
        advanceTimeBy(999); runCurrent(); assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
        advanceTimeBy(2); runCurrent()
        assertEquals(AccessCheckProblem.EXPIRED, f.context.states.value.problem); assertEquals(2, f.reader.calls)
    }
    @Test fun `renovacion que revoca se publica de inmediato y no se sobrescribe`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.RECONCILIATION_REQUIRED) }
        advanceTimeBy(834); runCurrent()
        assertEquals(AccessCheckProblem.RECONCILIATION_REQUIRED, f.context.states.value.problem)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(AccessCheckProblem.RECONCILIATION_REQUIRED, f.context.states.value.problem); assertEquals(2, f.reader.calls)
    }
    @Test fun `cambio de usuario durante renovacion descarta la respuesta de la cuenta anterior`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val pending = CompletableDeferred<AccessFactsResult>()
        f.reader.read = { state -> if ((state.identity as AccessIdentity.Authenticated).session.uid == "a") withContext(NonCancellable) { pending.await() } else checked(state, 9) }
        advanceTimeBy(834); runCurrent()
        f.driver.login("b", "login-b"); runCurrent(); val current = f.context.states.value
        assertEquals("b", current.origin.session?.uid); assertTrue(current.resolution is AccessResolution.Resolved)
        pending.complete(AccessFactsResult(AccessFacts.Checked(AccessSession("a", 1), null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 10)); runCurrent()
        assertEquals(current, f.context.states.value)
    }
    @Test fun `revalidar con lease vigente no muestra carga y aplica la respuesta nueva`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val phases = mutableListOf<AccessContextPhase>()
        backgroundScope.launch { f.context.states.collect { phases += it.phase } }; runCurrent()
        f.reader.read = { checked(it, 6) }; advanceTimeBy(300); runCurrent()
        assertTrue(f.context.revalidate()); assertTrue(f.context.currentState().resolution is AccessResolution.Resolved); runCurrent()
        assertEquals(2, f.reader.calls); assertEquals(6L, f.context.states.value.serverRevision)
        assertEquals(listOf(AccessContextPhase.RESOLVED), phases.distinct())
        advanceTimeBy(1_100); runCurrent() // la lease nueva nace en 300 ms: sigue vigente tras 1000 ms
        assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
    }
    @Test fun `revalidar aplica de inmediato una revocacion`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.RECONCILIATION_REQUIRED) }
        assertTrue(f.context.revalidate()); runCurrent()
        assertEquals(AccessCheckProblem.RECONCILIATION_REQUIRED, f.context.states.value.problem)
        assertFalse(f.context.currentState().resolution is AccessResolution.Resolved)
    }
    @Test fun `revalidar con fallo de red no extiende la lease`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
        advanceTimeBy(500); runCurrent(); assertTrue(f.context.revalidate()); runCurrent()
        assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
        advanceTimeBy(500); runCurrent()
        assertEquals(AccessCheckProblem.EXPIRED, f.context.states.value.problem)
        advanceTimeBy(10_000); runCurrent(); assertEquals(2, f.reader.calls)
    }
    @Test fun `sin lease vigente revalidar no consulta y pide refresco bloqueante`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Failed, problem = AccessCheckProblem.UNAVAILABLE) }
        advanceTimeBy(1_001); runCurrent(); val calls = f.reader.calls
        assertFalse(f.context.revalidate()); runCurrent(); assertEquals(calls, f.reader.calls)
        f.context.invalidate(); assertFalse(f.context.revalidate())
    }
    @Test fun `revalidaciones seguidas reemplazan la consulta y descartan la antigua`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val old = CompletableDeferred<AccessFactsResult>()
        f.reader.read = { withContext(NonCancellable) { old.await() } }
        assertTrue(f.context.revalidate()); runCurrent()
        f.reader.read = { checked(it, 9) }
        assertTrue(f.context.revalidate()); runCurrent(); assertEquals(9L, f.context.states.value.serverRevision)
        old.complete(AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.RECONCILIATION_REQUIRED)); runCurrent()
        assertEquals(9L, f.context.states.value.serverRevision); assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
    }
    @Test fun `cambio de usuario durante revalidacion descarta la respuesta anterior`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val pending = CompletableDeferred<AccessFactsResult>()
        f.reader.read = { state -> if ((state.identity as AccessIdentity.Authenticated).session.uid == "a") withContext(NonCancellable) { pending.await() } else checked(state, 9) }
        assertTrue(f.context.revalidate()); runCurrent()
        f.driver.login("b", "login-b"); runCurrent(); val current = f.context.states.value
        pending.complete(AccessFactsResult(AccessFacts.Checked(AccessSession("a", 1), null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 10)); runCurrent()
        assertEquals(current, f.context.states.value); assertEquals("b", current.origin.session?.uid)
    }
    @Test fun `logout y segundo plano cancelan la renovacion`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.driver.events.emit(IdentityObservation.SignedOut); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(1, f.reader.calls); assertEquals(AccessContextPhase.SIGNED_OUT, f.context.states.value.phase)
        f.driver.login(key = "again"); runCurrent(); f.context.invalidate(); advanceTimeBy(5_000); runCurrent()
        assertEquals(2, f.reader.calls); assertFalse(f.context.currentState().resolution is AccessResolution.Resolved)
    }
    @Test fun `revision de servidor menor no restaura permisos`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.reader.read = { AccessFactsResult(AccessFacts.Checked((it.identity as AccessIdentity.Authenticated).session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 4) }
        f.context.refresh(); runCurrent(); assertEquals(AccessCheckProblem.INVALID_RESPONSE, f.context.states.value.problem)
    }
    @Test fun `entorno no habilitado no hace consultas`() = runTest {
        val f = Fixture(this, false); runCurrent(); f.driver.login(); runCurrent(); f.context.refresh(); runCurrent()
        assertEquals(0, f.reader.calls); assertEquals(AccessCheckProblem.ENVIRONMENT_UNAVAILABLE, f.context.states.value.problem)
        assertEquals(AccessContextPhase.UNAVAILABLE, f.context.states.value.phase)
        assertFalse(f.context.states.value.resolution is AccessResolution.Resolved)
    }
    @Test fun `identidad fallida y carga nunca reutilizan capacidades`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.driver.events.emit(IdentityObservation.Loading); runCurrent(); assertTrue(f.context.states.value.resolution is AccessResolution.Loading)
        f.driver.events.emit(IdentityObservation.Failed); runCurrent(); assertTrue(f.context.states.value.resolution is AccessResolution.Error)
    }
    @Test fun `credencial invalidada antes de publicacion no concede capacidades`() = runTest {
        val f = Fixture(this); val pending = CompletableDeferred<AccessFactsResult>(); runCurrent()
        f.reader.read = { pending.await() }; f.driver.login(); runCurrent()
        val identity = f.source.states.value.identity as AccessIdentity.Authenticated
        f.driver.credentialCurrent = false
        pending.complete(AccessFactsResult(AccessFacts.Checked(identity.session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 5)); runCurrent()
        assertFalse(f.context.states.value.resolution is AccessResolution.Resolved)
    }
    @Test fun `dos revisiones con respuestas desordenadas conservan la nueva`() = runTest {
        val f = Fixture(this); val old = CompletableDeferred<AccessFactsResult>(); runCurrent()
        f.reader.read = { withContext(NonCancellable) { old.await() } }; f.driver.login(); runCurrent()
        val session = (f.source.states.value.identity as AccessIdentity.Authenticated).session
        f.reader.read = { AccessFactsResult(AccessFacts.Checked(session, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), 8) }
        f.context.refresh(); runCurrent(); assertEquals(8L, f.context.states.value.serverRevision)
        old.complete(AccessFactsResult(AccessFacts.Failed)); runCurrent(); assertEquals(8L, f.context.states.value.serverRevision)
    }
    @Test fun `timeout no transforma consulta incompleta en cliente`() = runTest {
        val f = Fixture(this); runCurrent(); f.reader.read = { awaitCancellation() }
        f.driver.login(); runCurrent(); advanceTimeBy(12_001); runCurrent()
        assertEquals(AccessCheckProblem.UNAVAILABLE, f.context.states.value.problem)
        assertTrue(f.context.states.value.resolution is AccessResolution.Error)
    }

    @Test fun `carga inicial y error de identidad no se presentan como logout confirmado`() = runTest {
        val f = Fixture(this); assertEquals(AccessContextPhase.LOADING, f.context.states.value.phase)
        runCurrent(); f.driver.events.emit(IdentityObservation.Failed); runCurrent()
        assertEquals(AccessContextPhase.ERROR, f.context.states.value.phase)
        f.driver.events.emit(IdentityObservation.SignedOut); runCurrent()
        assertEquals(AccessContextPhase.SIGNED_OUT, f.context.states.value.phase)
    }

    @Test fun `accion comprueba lease monotona aunque temporizador no haya corrido`() = runTest {
        val driver = Driver(); val reader = Reader(); var now = 0L
        val identity = SharedAccessIdentitySource(driver, backgroundScope)
        val context = SharedAccessContext(identity, reader, backgroundScope, true, 1_000) { now }
        runCurrent(); driver.login(); runCurrent()
        var allowed = false
        context.runIfCurrent { allowed = it.resolution is AccessResolution.Resolved }
        assertTrue(allowed)
        now = 1_001
        context.runIfCurrent { allowed = it.resolution is AccessResolution.Resolved }
        assertFalse(allowed); assertEquals(AccessCheckProblem.EXPIRED, context.currentState().problem)
        assertEquals(1, reader.calls)
    }
    @Test fun `credencial cambiada antes de publicacion UI no concede acciones`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        f.driver.credentialCurrent = false
        var executed = false
        assertFalse(f.context.runIfCurrent { executed = true })
        assertFalse(executed)
        assertFalse(f.context.currentState().resolution is AccessResolution.Resolved)
    }
    @Test fun `segundo plano invalida sin consultar ni borrar y refresh recupera`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent()
        val generation = f.context.states.value.origin.generation
        f.context.invalidate(); assertFalse(f.context.currentState().resolution is AccessResolution.Resolved)
        assertEquals(1, f.reader.calls)
        f.context.refresh(); runCurrent()
        assertEquals(generation, f.context.currentState().origin.generation)
        assertTrue(f.context.currentState().resolution is AccessResolution.Resolved)
    }

    @Test fun `vigencia predeterminada expira exactamente a sesenta segundos sin extender cache`() = runTest {
        var monotonic=0L
        val driver=Driver()
        val reader=Reader()
        val identity=SharedAccessIdentitySource(driver,backgroundScope)
        val context=SharedAccessContext(identity,reader,backgroundScope,true,monotonicMillis={monotonic})
        runCurrent();driver.login();runCurrent()
        assertTrue(context.currentState().resolution is AccessResolution.Resolved)
        monotonic=59_999
        assertTrue(context.currentState().resolution is AccessResolution.Resolved)
        monotonic=60_000
        assertEquals(AccessCheckProblem.EXPIRED,context.currentState().problem)
        assertFalse(context.currentState().resolution is AccessResolution.Resolved)
        assertEquals(1,reader.calls)
    }
}
