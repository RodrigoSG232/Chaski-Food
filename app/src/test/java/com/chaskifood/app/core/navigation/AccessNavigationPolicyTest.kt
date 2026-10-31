package com.chaskifood.app.core.navigation

import com.chaskifood.app.core.access.domain.*
import org.junit.Assert.*
import org.junit.Test

class AccessNavigationPolicyTest {
    private val session = AccessSession("buyer", 1)
    private val identity = AccessIdentity.Authenticated(session, "buyer@example.test", EmailVerification.VERIFIED)
    private fun resolved(owner: AccessOwnership? = null, stores: List<String> = emptyList(), manager: Boolean = false,
        admin: Boolean = false): AccessResolution = ResolveAccess()(identity.copy(isAdmin = admin),
        AccessFacts.Checked(session, owner,
            if (manager) stores.map { AccessAssignment("business", it, "buyer", "buyer@example.test", AccessBinding.VERIFIED) } else emptyList(),
            stores.map { AccessStore("business", it, AccessBusinessStatus.APPROVED, AccessStoreStatus.ACTIVE, AccessOperationalStatus.CLOSED) },
            AccessExclusivity.VERIFIED))
    private fun owner(status: AccessBusinessStatus = AccessBusinessStatus.APPROVED) = AccessOwnership("business", "buyer", status)
    @Test fun `recuperacion personal no concede compras nuevas ni ambitos ajenos`() {
        for (access in listOf(resolved(owner()), resolved(owner(AccessBusinessStatus.SUSPENDED)),
            resolved(stores=listOf("one"), manager=true))) {
            assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.CHECKOUT_RECOVERY))
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.CHECKOUT))
        }
        val conflict = AccessResolution.Conflict(session,AccessCapabilities(setOf(
            AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES,AccessScope.Account(session.uid)))))
        assertTrue(AccessNavigationPolicy.allows(conflict,ChaskiDestinations.CHECKOUT_RECOVERY))
        assertFalse(AccessNavigationPolicy.allows(conflict,ChaskiDestinations.CHECKOUT))
        assertFalse(AccessNavigationPolicy.allows(conflict,ChaskiDestinations.MAIN))
        assertFalse(AccessNavigationPolicy.allows(conflict,ChaskiDestinations.CHECKOUT_RECOVERY,storeId="foreign"))
        assertFalse(AccessNavigationPolicy.allows(AccessResolution.Conflict(session,AccessCapabilities(setOf(
            AccessGrant(AccessCapability.READ_PERSONAL_PURCHASES,AccessScope.Account("other"))))),ChaskiDestinations.CHECKOUT_RECOVERY))
    }

    @Test fun `bandeja exige READ_STORE_ORDERS en el local exacto incluso suspendido`() {
        val route=ChaskiDestinations.STORE_ORDERS+"/{businessId}/{storeId}"
        val manager=resolved(stores=listOf("one","two"),manager=true)
        assertTrue(AccessNavigationPolicy.allows(manager,route,"business","one"))
        assertFalse(AccessNavigationPolicy.allows(manager,route,"business","foreign"))
        assertFalse(AccessNavigationPolicy.allows(manager,route,"foreign","one"))
        assertFalse(AccessNavigationPolicy.allows(manager,route))
        for(denied in listOf(resolved(),resolved(owner()),resolved(admin=true),AccessResolution.SignedOut))
            assertFalse(AccessNavigationPolicy.allows(denied,route,"business","one"))
        val paused=ResolveAccess()(identity,AccessFacts.Checked(session,null,
            listOf(AccessAssignment("business","one","buyer","buyer@example.test",AccessBinding.VERIFIED)),
            listOf(AccessStore("business","one",AccessBusinessStatus.SUSPENDED,AccessStoreStatus.SUSPENDED,AccessOperationalStatus.PAUSED)),
            AccessExclusivity.VERIFIED))
        assertTrue(AccessNavigationPolicy.allows(paused,route,"business","one"))
        assertFalse(AccessNavigationPolicy.allows(paused,ChaskiDestinations.STORE_OPERATIONS,"business","one"))
    }

    @Test fun `cliente conserva MAIN producto carrito checkout y direcciones`() {
        val client = resolved()
        listOf(ChaskiDestinations.MAIN, ChaskiDestinations.PRODUCT_DETAIL, ChaskiDestinations.CART,
            ChaskiDestinations.CHECKOUT, ChaskiDestinations.ADDRESS_BOOK).forEach {
            assertTrue(it, AccessNavigationPolicy.allows(client, it))
        }
    }
    @Test fun `propietario aprobado gestiona solo negocio propio y no compra`() {
        val access = resolved(owner(), listOf("one"))
        assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.MANAGE_STORES, "business"))
        assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.MANAGE_STORES, "foreign"))
        assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.MANAGE_CATALOG))
        listOf(ChaskiDestinations.CART, ChaskiDestinations.CHECKOUT, ChaskiDestinations.PRODUCT_DETAIL).forEach {
            assertFalse(AccessNavigationPolicy.allows(access, it))
        }
    }
    @Test fun `suspendido conserva estado y Cuenta pero no listado catalogo ni operaciones`() {
        val access = resolved(owner(AccessBusinessStatus.SUSPENDED), listOf("one"))
        assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.BUSINESS_STATUS))
        assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.MAIN))
        listOf(ChaskiDestinations.MANAGE_STORES, ChaskiDestinations.MANAGE_CATALOG,
            ChaskiDestinations.STORE_OPERATIONS, ChaskiDestinations.CART).forEach {
            assertFalse(AccessNavigationPolicy.allows(access, it))
        }
    }
    @Test fun `responsable uno y varios solo opera locales vinculados`() {
        for (stores in listOf(listOf("one"), listOf("one", "two"))) {
            val access = resolved(stores = stores, manager = true)
            stores.forEach { assertTrue(AccessNavigationPolicy.allows(access, ChaskiDestinations.STORE_OPERATIONS, "business", it)) }
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.STORE_OPERATIONS, "business", "foreign"))
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.STORE_OPERATIONS, "foreign", "one"))
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.MANAGE_STORES))
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.REGISTER_BUSINESS))
            assertFalse(AccessNavigationPolicy.allows(access, ChaskiDestinations.CHECKOUT))
        }
    }
    @Test fun `administracion exige claim confirmado independiente de experiencia`() {
        val routes = listOf(ChaskiDestinations.ADMIN_BUSINESS_REVIEW, ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION, ChaskiDestinations.ADMIN_AUDIT_LOG)
        routes.forEach { assertFalse(AccessNavigationPolicy.allows(resolved(), it)); assertTrue(AccessNavigationPolicy.allows(resolved(admin = true), it)) }
        assertFalse(AccessNavigationPolicy.allows(resolved(admin = true), ChaskiDestinations.MANAGE_STORES))
        assertFalse(AccessNavigationPolicy.allows(resolved(admin = true), "store_orders"))
    }
    @Test fun `carga error conflicto logout no conceden acceso privado`() {
        val states = listOf(null, AccessResolution.SignedOut, AccessResolution.Loading(session),
            AccessResolution.Error(session, AccessProblem.FACTS_UNAVAILABLE), AccessResolution.Conflict(session, AccessCapabilities(emptySet())))
        states.forEach { state ->
            listOf(ChaskiDestinations.MAIN, ChaskiDestinations.CART, ChaskiDestinations.STORE_OPERATIONS,
                ChaskiDestinations.ADMIN_AUDIT_LOG).forEach { assertFalse(AccessNavigationPolicy.allows(state, it)) }
            assertTrue(AccessNavigationPolicy.allows(state, ChaskiDestinations.SIGN_IN))
        }
    }
    @Test fun `revocacion parcial retira solo el ambito eliminado`() {
        val before = resolved(stores = listOf("one", "two"), manager = true)
        val after = resolved(stores = listOf("two"), manager = true)
        assertEquals(2, AccessNavigationPolicy.storeScopes(before).size)
        assertFalse(AccessNavigationPolicy.allows(after, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION, "business", "one"))
        assertTrue(AccessNavigationPolicy.allows(after, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION, "business", "two"))
    }
    @Test fun `revocacion total y destinos desconocidos no reutilizan facultades`() {
        assertFalse(AccessNavigationPolicy.allows(resolved(), ChaskiDestinations.STORE_OPERATIONS))
        assertFalse(AccessNavigationPolicy.allows(resolved(), "unregistered_private_route"))
    }
    @Test fun `solicitud existente permite solo reenvio observado o rechazado`() {
        for (status in AccessBusinessStatus.entries) {
            val access = resolved(owner(status))
            assertFalse(AccessNavigationPolicy.allows(access, "register_business?resubmit={resubmit}"))
            assertEquals(status in setOf(AccessBusinessStatus.OBSERVED, AccessBusinessStatus.REJECTED),
                AccessNavigationPolicy.allows(access, "register_business?resubmit={resubmit}", resubmit = true))
        }
    }
    @Test fun `solo recuperacion confirmada dispara refresco sin duplicarlo`() {
        var refreshes = 0
        val trigger = AccessRecoveryTrigger { refreshes++ }
        trigger.validated(true); trigger.validated(true); assertEquals(0, refreshes)
        trigger.validated(false); trigger.validated(false); assertEquals(0, refreshes)
        trigger.validated(true); trigger.validated(true); assertEquals(1, refreshes)
    }    @Test fun `registrar negocio conserva destino estado mientras siga autorizado`() {
        val before = resolved() as AccessResolution.Resolved
        val after = resolved(owner(AccessBusinessStatus.PENDING_REVIEW)) as AccessResolution.Resolved
        assertTrue(AccessNavigationPolicy.allows(after, ChaskiDestinations.BUSINESS_STATUS))
        assertFalse(AccessNavigationPolicy.requiresReset(before, after, ChaskiDestinations.BUSINESS_STATUS))
    }
    @Test fun `retirada de ambito limpia editor pero no formularios personales autorizados`() {
        val before = resolved(stores = listOf("one", "two"), manager = true) as AccessResolution.Resolved
        val after = resolved(stores = listOf("two"), manager = true) as AccessResolution.Resolved
        assertTrue(AccessNavigationPolicy.requiresReset(before, after, ChaskiDestinations.STORE_OPERATIONS))
        assertFalse(AccessNavigationPolicy.requiresReset(before, after, ChaskiDestinations.PROFILE_INFO))
    }    @Test fun `dialogo compartido exige UID y generacion de destino y MAIN`() {
        assertTrue(AccessNavigationPolicy.matchesSharedFrame("run:3", "run:3", "run:3", "a", "a"))
        assertFalse(AccessNavigationPolicy.matchesSharedFrame("run:3", "run:1", "run:3", "a", "a"))
        assertFalse(AccessNavigationPolicy.matchesSharedFrame("run:3", "run:3", "run:1", "a", "a"))
        assertFalse(AccessNavigationPolicy.matchesSharedFrame("run:3", "run:3", "run:3", "b", "a"))
        assertFalse(AccessNavigationPolicy.matchesSharedFrame("run:3", "run:3", "run:3", "a", null))
    }    @Test fun `rutas con selector mantienen autorizacion exacta del local`() {
        val access = resolved(stores = listOf("one", "two"), manager = true)
        for (base in listOf(ChaskiDestinations.STORE_OPERATIONS, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION)) {
            assertTrue(AccessNavigationPolicy.allows(access, "$base?storeId={storeId}", "business", "two"))
            assertFalse(AccessNavigationPolicy.allows(access, "$base?storeId={storeId}", "business", "foreign"))
            assertFalse(AccessNavigationPolicy.allows(access, "$base?storeId={storeId}", "foreign", "two"))
        }
    }}
