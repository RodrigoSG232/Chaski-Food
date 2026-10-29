package com.chaskifood.app.core.access.domain

import org.junit.Assert.*
import org.junit.Test

class ResolveAccessTest {
    private val resolve = ResolveAccess()
    private val session = AccessSession("a", 1)
    private val identity = AccessIdentity.Authenticated(session, "user@example.test", EmailVerification.VERIFIED)
    private val account = AccessScope.Account("a")
    private val storeScope = AccessScope.Store("business", "store")
    private fun owner(status: AccessBusinessStatus = AccessBusinessStatus.APPROVED) = AccessOwnership("business", "a", status)
    private fun assignment(store: String = "store") = AccessAssignment("business", store, "a", "USER@example.test", AccessBinding.VERIFIED)
    private fun store(status: AccessStoreStatus = AccessStoreStatus.ACTIVE, op: AccessOperationalStatus = AccessOperationalStatus.OPEN,
        business: AccessBusinessStatus = AccessBusinessStatus.APPROVED) = AccessStore("business", "store", business, status, op)
    private fun facts(owner: AccessOwnership? = null, assignments: List<AccessAssignment> = emptyList(), stores: List<AccessStore> = emptyList()) =
        AccessFacts.Checked(session, owner, assignments, stores, AccessExclusivity.VERIFIED)
    private fun result(facts: AccessFacts = facts(), user: AccessIdentity = identity) = resolve(user, facts) as AccessResolution.Resolved
    private fun allows(r: AccessResolution.Resolved, c: AccessCapability, scope: AccessScope = account) = r.capabilities.allows(c, scope)

    @Test
    fun `cliente sin permisos confirmados`() {
        val r = result(); assertEquals(CommercialExperience.CLIENT, r.experience); assertTrue(allows(r, AccessCapability.START_PURCHASE)); assertTrue(allows(r, AccessCapability.REQUEST_BUSINESS))
    }

    @Test
    fun `solicitud pendiente no hace propietario`() {
        val r = result(facts(owner(AccessBusinessStatus.PENDING_REVIEW))); assertEquals(CommercialExperience.CLIENT, r.experience); assertFalse(allows(r, AccessCapability.REQUEST_BUSINESS))
    }

    @Test
    fun `propietario aprobado administra su negocio`() {
        val r = result(facts(owner())); assertEquals(CommercialExperience.OWNER, r.experience); assertTrue(allows(r, AccessCapability.MANAGE_MANAGERS, AccessScope.Business("business")))
    }

    @Test
    fun `propietario suspendido sin listado ni gestion`() {
        val r = result(facts(owner(AccessBusinessStatus.SUSPENDED), stores = listOf(store()))); assertEquals(CommercialExperience.OWNER, r.experience); assertTrue(allows(r, AccessCapability.READ_OWN_BUSINESS_STATUS, AccessScope.Business("business"))); assertEquals(2, r.capabilities.grants.size)
    }

    @Test
    fun `responsable de un local`() {
        val r = result(facts(assignments = listOf(assignment()), stores = listOf(store()))); assertEquals(CommercialExperience.MANAGER, r.experience); assertTrue(allows(r, AccessCapability.READ_STORE_ORDERS, storeScope)); assertTrue(allows(r, AccessCapability.OPERATE_HOURS_AND_OFFERS, storeScope))
    }

    @Test
    fun `responsable varios locales y negocios`() {
        val r = result(facts(assignments = listOf(assignment(), assignment("two").copy(businessId = "other")))); assertTrue(allows(r, AccessCapability.READ_STORE_ORDERS, AccessScope.Store("other", "two"))); assertFalse(allows(r, AccessCapability.READ_STORE_ORDERS, AccessScope.Store("other", "store")))
    }

    @Test
    fun `correo no verificado no autoriza ni crea cliente`() {
        assertTrue(resolve(identity.copy(verification = EmailVerification.UNVERIFIED), facts(assignments = listOf(assignment()))) is AccessResolution.Error)
    }

    @Test
    fun `retirada total vuelve a cliente`() {
        assertEquals(CommercialExperience.CLIENT, result(facts()).experience)
    }

    @Test
    fun `conflicto sin precedencia conserva solo historico`() {
        val r = resolve(identity, facts(owner(), listOf(assignment()))) as AccessResolution.Conflict; assertEquals(setOf(AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES, account)), r.capabilities.grants)
    }

    @Test
    fun `admin no hereda facultades comerciales`() {
        val r = result(user = identity.copy(isAdmin = true)); assertEquals(CommercialExperience.CLIENT, r.experience); assertFalse(r.capabilities.grants.any { it.capability == AccessCapability.READ_STORE_ORDERS })
    }

    @Test
    fun `propietario y responsable no compran`() {
        listOf(facts(owner()), facts(assignments = listOf(assignment()))).forEach { assertFalse(allows(result(it), AccessCapability.START_PURCHASE)) }
    }

    @Test
    fun `todos conservan historico personal propio`() {
        listOf(facts(), facts(owner()), facts(assignments = listOf(assignment()))).forEach { val r = result(it); assertTrue(allows(r, AccessCapability.READ_PERSONAL_PURCHASES)); assertFalse(allows(r, AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account("b"))) }
    }

    @Test
    fun `cambio correo requiere reconciliacion`() {
        assertTrue(resolve(identity.copy(email = "new@example.test"), facts(assignments = listOf(assignment()))) is AccessResolution.Error); assertTrue(resolve(identity, facts(assignments = listOf(assignment().copy(binding = AccessBinding.RECONCILIATION_REQUIRED)))) is AccessResolution.Error)
    }

    @Test
    fun `error no equivale ausencia`() {
        assertTrue(resolve(identity, AccessFacts.Failed) is AccessResolution.Error); assertTrue(resolve(identity, AccessFacts.Unverifiable) is AccessResolution.Error); assertEquals(CommercialExperience.CLIENT, result().experience)
    }

    @Test
    fun `generaciones distintas no comparten hechos`() {
        val r = resolve(identity.copy(session = session.copy(generation = 2)), facts()) as AccessResolution.Error; assertEquals(AccessProblem.STALE_SESSION, r.problem); assertEquals(2L, r.session.generation)
    }

    @Test
    fun `asignacion no implica habilitacion ni apertura`() {
        for (status in AccessStoreStatus.entries) for (op in AccessOperationalStatus.entries) { val r = result(facts(assignments = listOf(assignment()), stores = listOf(store(status, op)))); assertTrue(allows(r, AccessCapability.READ_STORE_ORDERS, storeScope)); assertEquals(status == AccessStoreStatus.ACTIVE, allows(r, AccessCapability.OPERATE_HOURS_AND_OFFERS, storeScope)) }
    }

    @Test
    fun `responsable no registra negocio`() {
        assertFalse(allows(result(facts(assignments = listOf(assignment()))), AccessCapability.REQUEST_BUSINESS))
    }

    @Test
    fun `dos cuentas independientes`() {
        val otherSession = AccessSession("b", 1); val r = result(AccessFacts.Checked(otherSession, null, emptyList(), emptyList(), AccessExclusivity.VERIFIED), identity.copy(session = otherSession)); assertEquals(CommercialExperience.CLIENT, r.experience); assertTrue(r.capabilities.allows(AccessCapability.REQUEST_BUSINESS, AccessScope.Account("b"))); assertTrue(resolve(identity.copy(session = otherSession), facts(owner())) is AccessResolution.Error)
    }

    @Test
    fun `propietario no tiene bandeja ni acciones futuras`() {
        val r = result(facts(owner(), stores = listOf(store()))); assertFalse(r.capabilities.grants.any { it.capability in setOf(AccessCapability.READ_STORE_ORDERS, AccessCapability.ACCEPT_STORE_ORDER, AccessCapability.REJECT_STORE_ORDER, AccessCapability.PREPARE_STORE_ORDER) })
    }

    @Test
    fun `sin sesion sin permisos`() {
        assertEquals(AccessResolution.SignedOut, resolve(AccessIdentity.SignedOut, facts(owner())))
    }

    @Test
    fun `carga no autoriza`() {
        assertEquals(AccessResolution.Loading(session), resolve(identity, AccessFacts.Loading))
    }

    @Test
    fun `responsable lee historico durante suspension`() {
        val r = result(facts(assignments = listOf(assignment()), stores = listOf(store(business = AccessBusinessStatus.SUSPENDED)))); assertTrue(allows(r, AccessCapability.READ_STORE_ORDERS, storeScope)); assertFalse(allows(r, AccessCapability.OPERATE_HOURS_AND_OFFERS, storeScope))
    }

    @Test
    fun `retirada parcial elimina alcance`() {
        val r = result(facts(assignments = listOf(assignment("remaining")))); assertFalse(allows(r, AccessCapability.READ_STORE_ORDERS, storeScope)); assertEquals(CommercialExperience.MANAGER, r.experience)
    }

    @Test
    fun `hechos de otro propietario son invalidos`() {
        assertEquals(AccessProblem.INVALID_FACTS, (resolve(identity, facts(owner().copy(ownerUid = "other"))) as AccessResolution.Error).problem)
    }

    @Test
    fun `vinculacion uid ajeno no transfiere permisos`() {
        assertEquals(CommercialExperience.CLIENT, result(facts(assignments = listOf(assignment().copy(boundUid = "b")))).experience)
    }

    @Test
    fun `entradas y salidas tienen copia defensiva`() {
        val list = mutableListOf(assignment()); val f = facts(assignments = list); list.clear(); val r = result(f); assertEquals(CommercialExperience.MANAGER, r.experience); try { (r.capabilities.grants as MutableSet).clear(); fail("mutable") } catch (_: UnsupportedOperationException) { }
    }

    @Test
    fun `verificacion desconocida no habilita`() {
        assertTrue(resolve(identity.copy(verification = EmailVerification.UNKNOWN), facts(assignments = listOf(assignment()))) is AccessResolution.Error)
    }

    @Test
    fun `observada y rechazada no son propietario`() {
        listOf(AccessBusinessStatus.OBSERVED, AccessBusinessStatus.REJECTED).forEach { assertEquals(CommercialExperience.CLIENT, result(facts(owner(it))).experience) }
    }

    @Test
    fun `propietario invitacion pendiente exclusividad comprobada`() { val r = result(facts(owner(), listOf(assignment().copy(boundUid = null, binding = AccessBinding.PENDING)))); assertEquals(CommercialExperience.OWNER, r.experience) }

    @Test
    fun `propietario asignacion ajena confirmada`() { val r = result(facts(owner(), listOf(assignment().copy(boundUid = "b")))); assertEquals(CommercialExperience.OWNER, r.experience) }

    @Test
    fun `invitacion sin exclusividad no permite resolver`() { assertTrue(resolve(identity, AccessFacts.Checked(session, owner(), listOf(assignment().copy(binding = AccessBinding.PENDING)), emptyList())) is AccessResolution.Error) }

    @Test
    fun `vinculacion no verificable no autoriza`() { assertTrue(resolve(identity, facts(assignments = listOf(assignment().copy(binding = AccessBinding.UNVERIFIABLE)))) is AccessResolution.Error) }

    @Test
    fun `retirada efectiva compara antes y despues`() { val before = result(facts(assignments = listOf(assignment()))); val after = result(facts()); assertTrue(allows(before, AccessCapability.READ_STORE_ORDERS, storeScope)); assertFalse(allows(after, AccessCapability.READ_STORE_ORDERS, storeScope)); assertTrue(allows(after, AccessCapability.START_PURCHASE)) }

    @Test
    fun `dos cuentas propietario y responsable no heredan`() { val manager = result(facts(assignments = listOf(assignment()))); val b = AccessSession("b", 2); val owner = result(AccessFacts.Checked(b, owner().copy(ownerUid = "b"), emptyList(), emptyList(), AccessExclusivity.VERIFIED), identity.copy(session = b)); assertEquals(CommercialExperience.MANAGER, manager.experience); assertEquals(CommercialExperience.OWNER, owner.experience); assertFalse(owner.capabilities.allows(AccessCapability.READ_STORE_ORDERS, storeScope)); assertFalse(manager.capabilities.allows(AccessCapability.MANAGE_BUSINESS, AccessScope.Business("business"))) }

    @Test
    fun `resultados equivalentes igualdad por valor`() { assertEquals(result(facts(assignments = listOf(assignment()))), result(facts(assignments = listOf(assignment())))); assertEquals(facts(), facts()); assertEquals(facts().hashCode(), facts().hashCode()) }

    @Test
    fun `hechos equivalentes orden distinto`() { assertEquals(facts(assignments = listOf(assignment(), assignment("two"))), facts(assignments = listOf(assignment("two"), assignment()))) }

    @Test
    fun `igualdad no oculta hechos con locales duplicados invalidos`() {
        val valid = facts(assignments = listOf(assignment()), stores = listOf(store()))
        val invalid = facts(assignments = listOf(assignment()), stores = listOf(store(), store()))
        assertNotEquals(valid, invalid)
        assertTrue(resolve(identity, invalid) is AccessResolution.Error)
        assertTrue(resolve(identity, valid) is AccessResolution.Resolved)
    }
}
