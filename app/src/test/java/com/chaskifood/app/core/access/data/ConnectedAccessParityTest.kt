package com.chaskifood.app.core.access.data

import androidx.lifecycle.ViewModelStore
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.core.firebase.FoodApiEnvironment
import com.chaskifood.app.core.navigation.*
import com.chaskifood.app.feature.cart.data.*
import com.chaskifood.app.feature.checkout.data.SharedCheckoutPurchaseGuard
import com.chaskifood.app.feature.orders.data.SharedStoreOrdersAccessSource
import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ConnectedAccessParityTest(private val mode: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun modes() = listOf(arrayOf("LOCAL"), arrayOf("CONECTADO"))
    }
    private fun configured() = if (mode == "LOCAL") FoodApiEnvironment.isConfigured(mode, "demo-chaski-hu15", "",
        "http://127.0.0.1:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/", "127.0.0.1")
        else FoodApiEnvironment.isConfigured(mode, "chaski-food-9adba", "chaski-food-9adba",
            "https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/", "")
    private class Fixture(test: TestScope, enabled: Boolean) {
        val events = MutableStateFlow<IdentityObservation>(IdentityObservation.SignedOut)
        val driver = object : AccessIdentityDriver { override fun observe() = events }
        val identity = SharedAccessIdentitySource(driver, test.backgroundScope)
        var ownership: AccessOwnership? = null
        var assignments = emptyList<AccessAssignment>()
        var stores = emptyList<AccessStore>()
        var unavailable = false
        val context = SharedAccessContext(identity, object : AccessFactsReader {
            override suspend fun read(identity: AccessIdentityState): AccessFactsResult =
                if (unavailable) AccessFactsResult(AccessFacts.Unverifiable, problem = AccessCheckProblem.UNAVAILABLE)
                else AccessFactsResult(AccessFacts.Checked((identity.identity as AccessIdentity.Authenticated).session,
                    ownership, assignments, stores, AccessExclusivity.VERIFIED), 1)
        }, test.backgroundScope, enabled)
        val viewModels = ViewModelStore()
        val navigation = AccessNavigationViewModel(context, identity).also { viewModels.put("navigation", it) }
        val cartState = MutableStateFlow(CartSession("a", 1))
        val cart = object : CartSessionSource {
            override val sessions = cartState
            override fun current() = sessions.value
        }
        val inbox = SharedStoreOrdersAccessSource(context, cart)
        val purchase = SharedCheckoutPurchaseGuard(context)
        fun login(key: String = "login") { events.value = IdentityObservation.Authenticated(key, "a", "a@example.test", EmailVerification.VERIFIED) }
    }
    @Test fun `purchase navigation and recovery retain identity checks in both environments`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this, configured())
        try {
            assertTrue(configured()); runCurrent(); f.login(); runCurrent()
            assertTrue(f.navigation.enabled); assertTrue(f.navigation.allows(ChaskiDestinations.CART))
            assertTrue(f.purchase.allows("a", true)); assertFalse(f.purchase.allows("foreign", true))
            assertFalse(f.inbox.current(StoreOrdersTarget("b", "s")) is StoreOrdersAuthorization.Allowed)
            val old = f.context.currentState().origin
            f.events.value = IdentityObservation.SignedOut; runCurrent()
            assertFalse(f.purchase.allows("a", false)); assertFalse(f.navigation.allows(ChaskiDestinations.CART))
            f.login("new-login"); runCurrent()
            var actions = 0; f.navigation.runIfAllowedFrom(old, ChaskiDestinations.CART) { actions++ }
            assertEquals(0, actions); assertTrue(f.purchase.allows("a", false))
        } finally { f.viewModels.clear(); Dispatchers.resetMain() }
    }
    @Test fun `owner cannot purchase or read manager inbox even with a valid environment`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(this, configured())
        try {
            f.ownership = AccessOwnership("a", "a", AccessBusinessStatus.SUSPENDED)
            runCurrent(); f.login(); runCurrent()
            assertFalse(f.purchase.allows("a", true)); assertTrue(f.purchase.allows("a", false))
            assertFalse(f.navigation.allows(ChaskiDestinations.CART))
            assertFalse(f.inbox.current(StoreOrdersTarget("a", "s")) is StoreOrdersAuthorization.Allowed)
            assertTrue(CurrentStoreAuthorization(f.context).enforced)
            assertFalse(CurrentStoreAuthorization(f.context).allows("a", "s"))
        } finally { f.viewModels.clear(); Dispatchers.resetMain() }
    }
    @Test fun `assigned manager retains scoped inbox during suspension but cannot operate or purchase`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(this, configured())
        try {
            f.assignments = listOf(AccessAssignment("b", "s", "a", "a@example.test", AccessBinding.VERIFIED))
            f.stores = listOf(AccessStore("b", "s", AccessBusinessStatus.SUSPENDED, AccessStoreStatus.SUSPENDED, AccessOperationalStatus.PAUSED))
            runCurrent(); f.login(); runCurrent()
            assertTrue(f.inbox.current(StoreOrdersTarget("b", "s")) is StoreOrdersAuthorization.Allowed)
            assertFalse(f.inbox.current(StoreOrdersTarget("b", "foreign")) is StoreOrdersAuthorization.Allowed)
            assertFalse(f.purchase.allows("a", true)); assertTrue(f.purchase.allows("a", false))
            assertFalse(CurrentStoreAuthorization(f.context).allows("b", "s"))
            f.cartState.value = CartSession("other", 2)
            assertFalse(f.inbox.current(StoreOrdersTarget("b", "s")) is StoreOrdersAuthorization.Allowed)
        } finally { f.viewModels.clear(); Dispatchers.resetMain() }
    }
    @Test fun `unconfigured or failed context never bypasses navigation authorization`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val f = Fixture(this, false)
        try {
            runCurrent(); f.login(); runCurrent()
            assertTrue(f.navigation.enabled); assertFalse(f.navigation.allows(ChaskiDestinations.CART))
            var actions = 0; f.navigation.runIfAllowedFrom(f.context.currentState().origin, ChaskiDestinations.CART) { actions++ }
            assertEquals(0, actions); assertFalse(f.purchase.allows("a", true))
        } finally { f.viewModels.clear(); Dispatchers.resetMain() }
    }
}
