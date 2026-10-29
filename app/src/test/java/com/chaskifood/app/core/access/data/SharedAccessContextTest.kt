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
        val context = SharedAccessContext(source, reader, test.backgroundScope, enabled, 1_000)
    }
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
    @Test fun `lease caduca sin polling ni capacidades persistentes`() = runTest {
        val f = Fixture(this); runCurrent(); f.driver.login(); runCurrent(); advanceTimeBy(1_001); runCurrent()
        assertEquals(AccessCheckProblem.EXPIRED, f.context.states.value.problem); assertTrue(f.context.states.value.resolution is AccessResolution.Error)
        assertEquals(1, f.reader.calls)
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

}
