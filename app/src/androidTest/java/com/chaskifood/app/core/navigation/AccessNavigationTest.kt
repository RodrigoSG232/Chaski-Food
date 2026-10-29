package com.chaskifood.app.core.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.ui.theme.ChaskiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class AccessNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ViewModelStore()
    private val events = MutableStateFlow<IdentityObservation>(IdentityObservation.Authenticated("login", "a", "a@example.test", EmailVerification.VERIFIED))
    private var owner = false
    private var localIds = listOf("one", "two")
    private var manager = false
    private var failed = false
    private var clock = 0L
    private var mutations = 0
    private var queuedAction: (() -> Unit)? = null
    private var reads = 0
    private val driver = object : AccessIdentityDriver {
        override fun observe(): Flow<IdentityObservation> = events
        override suspend fun refresh() {
            val old = events.value as? IdentityObservation.Authenticated ?: return
            events.value = old.copy(credentialRevision = old.credentialRevision + 1)
        }
    }
    private val identity = SharedAccessIdentitySource(driver, scope)
    private val context = SharedAccessContext(identity, object : AccessFactsReader {
        override suspend fun read(identity: AccessIdentityState): AccessFactsResult {
            reads++
            val actor = identity.identity as AccessIdentity.Authenticated
            val session = actor.session
            if (failed) return AccessFactsResult(AccessFacts.Failed)
            return AccessFactsResult(AccessFacts.Checked(session,
                if (owner) AccessOwnership("business", session.uid, AccessBusinessStatus.APPROVED) else null,
                if (manager) localIds.map { AccessAssignment("business", it, session.uid, actor.email.orEmpty(), AccessBinding.VERIFIED) } else emptyList(),
                localIds.map { AccessStore("business", it, AccessBusinessStatus.APPROVED, AccessStoreStatus.ACTIVE, AccessOperationalStatus.CLOSED) },
                AccessExclusivity.VERIFIED), 1)
        }
    }, scope, true, monotonicMillis = { clock })
    private lateinit var access: AccessNavigationViewModel
    private lateinit var nav: NavHostController
    private var mainProbe: Probe? = null
    private var cartProbe: Probe? = null
    private var privateCreations = 0
    private lateinit var lifecycle: LifecycleRegistry
    class Probe : ViewModel()
    @Before fun prepare() {
        access = AccessNavigationViewModel(context, identity)
        store.put("access", access)
    }
    @After fun finish() { store.clear(); scope.cancel() }
    private fun screen(initial: String = ChaskiDestinations.MAIN, restoration: StateRestorationTester? = null, controlledLifecycle: Boolean = false) {
        access.refresh()
        val content: @Composable () -> Unit = {
            nav = rememberNavController()
            ChaskiTheme {
                if (controlledLifecycle) {
                    val owner = remember { object : LifecycleOwner {
                        val registry = LifecycleRegistry(this)
                        override val lifecycle: Lifecycle get() = registry
                    }.also { it.registry.currentState = Lifecycle.State.RESUMED } }
                    lifecycle = owner.registry
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) { AccessNavigationEvents(access, nav) }
                } else AccessNavigationEvents(access, nav)
                NavHost(nav, initial) {
                    protectedComposable(access, nav, ChaskiDestinations.MAIN) { entry ->
                        mainProbe = viewModel(viewModelStoreOwner = entry)
                        Text("MAIN privado")
                        TextButton(onClick = { nav.navigate(ChaskiDestinations.CART) }) { Text("Abrir carrito") }
                    }
                    protectedComposable(access, nav, ChaskiDestinations.CART) {
                        cartProbe = viewModel()
                        DisposableEffect(Unit) { privateCreations++; onDispose {} }
                        Text("Carrito privado")
                    }
                    protectedComposable(access, nav, ChaskiDestinations.STORE_OPERATIONS) {
                        Text("Operaciones privadas")
                        val authorize = LocalProtectedAction.current
                        queuedAction = { authorize { mutations++ } }
                        TextButton(onClick = { authorize { mutations++ } }) { Text("Acción protegida") }
                    }
                    protectedComposable(access, nav, ChaskiDestinations.ADMIN_AUDIT_LOG) { Text("Auditoría privada") }
                    protectedComposable(access, nav, ChaskiDestinations.SIGN_IN) { Text("Acceso público") }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitForIdle()
    }
    @Test fun clienteAbreCarritoYMainMantieneSuViewModelTrasRefresco() {
        screen()
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
        val original = mainProbe
        compose.onNodeWithText("Abrir carrito").performClick()
        compose.onNodeWithText("Carrito privado").assertIsDisplayed()
        val originalCart = cartProbe
        compose.runOnIdle { access.refresh() }
        compose.waitForIdle()
        assertSame(originalCart, cartProbe)
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
        assertSame(original, mainProbe)
    }
    @Test fun propietarioNoPuedeAbrirCarritoDirectoNiPorAtras() {
        owner = true
        screen(ChaskiDestinations.CART)
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, privateCreations); assertEquals(ChaskiDestinations.MAIN, nav.currentDestination?.route) }
        compose.onNodeWithText("Abrir carrito").performClick()
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
    }
    @Test fun revocacionParcialRetiraDestinoPrivadoYConservaMain() {
        manager = true; screen()
        val original = mainProbe
        compose.runOnIdle { nav.navigate(ChaskiDestinations.STORE_OPERATIONS) }
        compose.onNodeWithText("Operaciones privadas").assertIsDisplayed()
        compose.runOnIdle { localIds = listOf("two"); access.refresh() }
        compose.waitForIdle()
        compose.onNodeWithText("Operaciones privadas").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ChaskiDestinations.MAIN, nav.currentDestination?.route); assertSame(original, mainProbe) }
    }
    @Test fun logoutYReingresoMismoUidNoConservaDestinoAnterior() {
        screen()
        compose.onNodeWithText("Abrir carrito").performClick()
        compose.onNodeWithText("Carrito privado").assertIsDisplayed()
        compose.runOnIdle { events.value = IdentityObservation.SignedOut }
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
        compose.onNodeWithText("Acceso público").assertIsDisplayed()
        compose.runOnIdle { events.value = IdentityObservation.Authenticated("new-login", "a", "a@example.test", EmailVerification.VERIFIED) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(ChaskiDestinations.SIGN_IN, nav.currentDestination?.route) }
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
    }
    @Test fun errorNoMuestraInformacionPrivadaYReintentoRecupera() {
        failed = true; screen()
        compose.onNodeWithText("MAIN privado").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
        compose.runOnIdle { failed = false }
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
    }
    @Test fun rutaAdministrativaSinClaimNoComponeContenido() {
        screen(ChaskiDestinations.ADMIN_AUDIT_LOG)
        compose.onNodeWithText("Auditoría privada").assertDoesNotExist()
    }
    @Test fun recomposicionNoDisparaLecturasRemotas() {
        screen(); val initial = reads
        compose.onNodeWithText("Abrir carrito").performClick()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        assertEquals(initial, reads)
    }
    @Test fun segundoPlanoInvalidaYRetornoRefrescaSinReconstruirMain() {
        screen(controlledLifecycle = true)
        val original = mainProbe
        val before = reads
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("MAIN privado").assertDoesNotExist()
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
        assertEquals(before + 1, reads)
        assertSame(original, mainProbe)
    }
    @Test fun restauracionDeOtroHostNoRecuperaDestinoPrivadoAnterior() {
        val restoration = StateRestorationTester(compose)
        screen(restoration = restoration)
        compose.onNodeWithText("Abrir carrito").performClick()
        compose.onNodeWithText("Carrito privado").assertIsDisplayed()
        compose.runOnIdle {
            access = AccessNavigationViewModel(context, identity)
            store.put("access", access)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
    }
    @Test fun cambioDeUidRetiraCarritoYNoLoRecuperaConAtras() {
        screen(); compose.onNodeWithText("Abrir carrito").performClick()
        compose.runOnIdle { events.value = IdentityObservation.Authenticated("b-login", "b", "b@example.test", EmailVerification.VERIFIED) }
        compose.waitForIdle()
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Carrito privado").assertDoesNotExist()
    }    @Test fun carritoDirectoDeClienteCreaMainSoloCuandoFalta() {
        screen(ChaskiDestinations.CART)
        compose.onNodeWithText("Carrito privado").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(nav.getBackStackEntry(ChaskiDestinations.MAIN)) }
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
    }    @Test fun cambioDeCuentaEnRutaPublicaNoRecuperaMainAnterior() {
        screen()
        val previousMain = mainProbe
        compose.runOnIdle { nav.navigate(ChaskiDestinations.SIGN_IN) }
        compose.onNodeWithText("Acceso público").assertIsDisplayed()
        compose.runOnIdle { events.value = IdentityObservation.Authenticated("login-b", "b", "b@example.test", EmailVerification.VERIFIED) }
        compose.waitForIdle()
        compose.onNodeWithText("Acceso público").assertIsDisplayed()
        compose.runOnIdle { nav.navigate(ChaskiDestinations.MAIN) }
        compose.onNodeWithText("MAIN privado").assertIsDisplayed()
        assertNotSame(previousMain, mainProbe)
        compose.runOnIdle { assertFalse(nav.popBackStack()) }
    }    @Test fun accionCaducadaNoSeDespachaAunqueAunNoHayaEmitidoElTemporizador() {
        manager = true; screen()
        compose.runOnIdle { nav.navigate(ChaskiDestinations.STORE_OPERATIONS) }
        compose.onNodeWithText("Acción protegida").performClick()
        assertEquals(1, mutations)
        clock = 60_001
        compose.onNodeWithText("Acción protegida").performClick()
        assertEquals(1, mutations)
        compose.onNodeWithText("Operaciones privadas").assertDoesNotExist()
    }    @Test fun eventoEnColaDeOtraCuentaNoEsAutorizadoPorPermisosDeLaNueva() {
        manager = true; screen()
        compose.runOnIdle { nav.navigate(ChaskiDestinations.STORE_OPERATIONS) }
        compose.onNodeWithText("Operaciones privadas").assertIsDisplayed()
        val old = checkNotNull(queuedAction)
        compose.runOnIdle { events.value = IdentityObservation.Authenticated("login-b", "b", "b@example.test", EmailVerification.VERIFIED) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(access.allows(ChaskiDestinations.STORE_OPERATIONS))
            old()
        }
        assertEquals(0, mutations)
    }}
