package com.chaskifood.app.core.access.data

import com.chaskifood.app.core.access.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAccessIdentitySourceTest {
    private class Driver : AccessIdentityDriver {
        val events = MutableSharedFlow<IdentityObservation>(replay = 1)
        var subscriptions = 0
        var closed = 0
        override fun observe(): Flow<IdentityObservation> = flow {
            subscriptions++
            try { emitAll(events) } finally { closed++ }
        }
    }
    private fun user(uid: String = "a", key: String = "login-a", email: String = "a@example.test",
        verification: EmailVerification = EmailVerification.VERIFIED) = IdentityObservation.Authenticated(key, uid, email, verification)
    private val evaluate = EvaluateAccessEvent()
    private fun facts(origin: AccessProvenance): AccessFacts = AccessFacts.Checked(checkNotNull(origin.session), null, emptyList(), emptyList(), AccessExclusivity.VERIFIED)

    @Test fun `Loading antiguo no cambia cuenta B`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent(); val old = source.states.value
        val event = AccessEvent.Facts(old.origin, AccessFacts.Loading)
        d.events.emit(IdentityObservation.SignedOut); runCurrent()
        d.events.emit(user("b", "login-b")); runCurrent(); val current = source.states.value
        assertNull(evaluate(current.origin, current.identity, event))
        assertEquals("b", (source.states.value.identity as AccessIdentity.Authenticated).session.uid)
    }

    @Test fun `Failed antiguo no cambia cuenta B`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent(); val old = source.states.value
        val event = AccessEvent.Facts(old.origin, AccessFacts.Failed)
        d.events.emit(IdentityObservation.SignedOut); runCurrent()
        d.events.emit(user("b", "login-b")); runCurrent(); val current = source.states.value
        assertNull(evaluate(current.origin, current.identity, event))
        assertEquals("b", (source.states.value.identity as AccessIdentity.Authenticated).session.uid)
    }

    @Test fun `Unverifiable antiguo no cambia cuenta B`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent(); val old = source.states.value
        val event = AccessEvent.Facts(old.origin, AccessFacts.Unverifiable)
        d.events.emit(IdentityObservation.SignedOut); runCurrent()
        d.events.emit(user("b", "login-b")); runCurrent(); val current = source.states.value
        assertNull(evaluate(current.origin, current.identity, event))
        assertEquals("b", (source.states.value.identity as AccessIdentity.Authenticated).session.uid)
    }

    @Test fun `Checked antiguo no cambia cuenta B`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent(); val old = source.states.value
        val event = AccessEvent.Facts(old.origin, facts(old.origin))
        d.events.emit(IdentityObservation.SignedOut); runCurrent()
        d.events.emit(user("b", "login-b")); runCurrent(); val current = source.states.value
        assertNull(evaluate(current.origin, current.identity, event))
        assertEquals("b", (source.states.value.identity as AccessIdentity.Authenticated).session.uid)
    }

    @Test fun `mismo uid reingreso cambia generacion`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; val event = AccessEvent.Facts(old.origin, facts(old.origin)); d.events.emit(IdentityObservation.SignedOut); runCurrent(); d.events.emit(user(key = "new-login")); runCurrent(); val current = source.states.value; assertTrue(current.origin.generation > old.origin.generation); assertNull(evaluate(current.origin, current.identity, event))
    }

    @Test fun `correo cambia revision sin nueva sesion`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; d.events.emit(user(email = "new@example.test")); runCurrent(); val current = source.states.value; assertEquals(old.origin.generation, current.origin.generation); assertTrue(current.origin.revision > old.origin.revision); assertNull(evaluate(current.origin, current.identity, AccessEvent.Facts(old.origin, facts(old.origin))))
    }

    @Test fun `verificacion cambia revision`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; d.events.emit(user(verification = EmailVerification.UNVERIFIED)); runCurrent(); assertTrue(source.states.value.origin.revision > old.origin.revision); assertEquals(old.origin.generation, source.states.value.origin.generation); assertNull(evaluate(source.states.value.origin, source.states.value.identity, AccessEvent.Facts(old.origin, AccessFacts.Failed)))
    }

    @Test fun `misma sesion nueva consulta invalidada por revision`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; d.events.emit(IdentityObservation.Loading); runCurrent(); val current = source.states.value; assertNull(evaluate(current.origin, current.identity, AccessEvent.Facts(old.origin, AccessFacts.Checked(checkNotNull(old.origin.session), null, emptyList(), emptyList(), AccessExclusivity.VERIFIED))))
    }

    @Test fun `error explicito sin cliente`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        d.events.emit(IdentityObservation.Failed); runCurrent(); val current = source.states.value; assertEquals(IdentityAvailability.FAILED, current.availability); assertTrue(evaluate(current.origin, current.identity, AccessEvent.Facts(current.origin, AccessFacts.Failed)) is AccessResolution.Error)
    }

    @Test fun `cierre antiguo no cierra nueva sesion`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; d.events.emit(user("b", "login-b")); runCurrent(); val current = source.states.value; assertNull(evaluate(current.origin, current.identity, AccessEvent.Identity(old.origin, AccessIdentity.SignedOut))); assertTrue(evaluate(current.origin, current.identity, AccessEvent.Identity(current.origin, current.identity)) is AccessResolution.Loading)
    }

    @Test fun `cierre vigente identificado`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        d.events.emit(IdentityObservation.SignedOut); runCurrent(); val current = source.states.value; assertEquals(AccessResolution.SignedOut, evaluate(current.origin, current.identity, AccessEvent.Identity(current.origin, current.identity)))
    }

    @Test fun `multiples consumidores una suscripcion y cierre`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val j1 = backgroundScope.launch { source.states.collect() }; val j2 = backgroundScope.launch { source.states.collect() }; runCurrent(); assertEquals(1, d.subscriptions); j1.cancel(); j2.cancel(); backgroundScope.cancel(); runCurrent(); assertEquals(1, d.closed)
    }

    @Test fun `identidad equivalente deduplicada`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; val observed = mutableListOf<AccessIdentityState>(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.states.toList(observed) }; d.events.emit(user()); runCurrent(); assertEquals(old, source.states.value); assertEquals(1, observed.size); job.cancel()
    }

    @Test fun `identidad aislada no concede autorizacion comercial`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val current = source.states.value; assertTrue(evaluate(current.origin, current.identity, AccessEvent.Identity(current.origin, current.identity)) is AccessResolution.Loading)
    }

    @Test fun `evento checked vigente evaluado`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val current = source.states.value; val r = evaluate(current.origin, current.identity, AccessEvent.Facts(current.origin, facts(current.origin))) as AccessResolution.Resolved; assertEquals(CommercialExperience.CLIENT, r.experience)
    }

    @Test fun `identidad tardia revision vieja descartada`() = runTest {
        val d = Driver(); val source = SharedAccessIdentitySource(d, backgroundScope)
        d.events.emit(user()); runCurrent()
        val old = source.states.value; d.events.emit(user(email = "changed@example.test")); runCurrent(); assertNull(evaluate(source.states.value.origin, source.states.value.identity, AccessEvent.Identity(old.origin, old.identity)))
    }
}
