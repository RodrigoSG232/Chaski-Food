package com.chaskifood.app.core.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.ui.components.ErrorState
import com.chaskifood.app.ui.components.LoadingState

/** Ámbito privado asociado al destino y a su autorización confirmada. */
internal class AccessDestinationOwner(private val entry: NavBackStackEntry) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory get() = entry.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras get() = entry.defaultViewModelCreationExtras
}


/** Conserva el ámbito mientras el destino esté en back stack; no lo pierde por un refresco temporal. */
internal class AccessDestinationCache : ViewModel() {
    private var stamp: Any? = null
    private var owner: AccessDestinationOwner? = null
    private var lifecycle: Lifecycle? = null
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) owner?.viewModelStore?.clear()
    }
    var version = 0
        private set
    fun prepare(entry: NavBackStackEntry, next: Any): AccessDestinationOwner {
        if (lifecycle == null) {
            lifecycle = entry.lifecycle
            entry.lifecycle.addObserver(observer)
        }
        if (stamp != next) {
            owner?.viewModelStore?.clear()
            owner = AccessDestinationOwner(entry)
            stamp = next
            version++
        }
        return checkNotNull(owner)
    }
    override fun onCleared() {
        lifecycle?.removeObserver(observer)
        owner?.viewModelStore?.clear()
    }
}
internal typealias ProtectedAction = (() -> Unit) -> Unit
internal val LocalProtectedAction = staticCompositionLocalOf<ProtectedAction> { { it() } }

internal val LocalNavigationAccess = staticCompositionLocalOf<AccessNavigationViewModel?> { null }

fun NavGraphBuilder.protectedComposable(
    access: AccessNavigationViewModel,
    navController: NavHostController,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { entry ->
        AccessDestinationGuard(access, navController, entry) { content(entry) }
    }
}

@Composable
internal fun AccessDestinationGuard(access: AccessNavigationViewModel, navController: NavHostController, entry: NavBackStackEntry,
    content: @Composable () -> Unit) {
    if (!access.enabled || entry.destination.route in AccessNavigationPolicy.publicRoutes) {
        content(); return
    }
    val observed by access.states.collectAsStateWithLifecycle()
    val state = access.current()
    val route = entry.destination.route
    val businessId = entry.arguments?.getString("businessId")
    val storeId = if (route?.substringBefore('?') == ChaskiDestinations.STORE_OPERATIONS ||
        route?.substringBefore('?') == ChaskiDestinations.STORE_PRODUCT_CONFIGURATION) entry.arguments?.getString("storeId") else null
    val allowed = observed.origin == state.origin && AccessNavigationPolicy.allows(state.resolution,
        route, businessId, storeId, entry.arguments?.getBoolean("resubmit") == true)
    val holder = rememberSaveableStateHolder()
    val resolution = state.resolution as? AccessResolution.Resolved
    val identityKey = resolution?.identity ?: (state.resolution as? AccessResolution.Conflict)?.session
    if (!allowed) {
        if (route != ChaskiDestinations.CHECKOUT_RECOVERY &&
            AccessNavigationPolicy.allows(state.resolution, ChaskiDestinations.CHECKOUT_RECOVERY)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { RestrictedAccess(state, { access.refresh() }) }
                com.chaskifood.app.ui.components.ChaskiButton("Recuperar compra pendiente", onClick = {
                    navController.navigate(ChaskiDestinations.CHECKOUT_RECOVERY) { launchSingleTop = true }
                })
            }
        } else RestrictedAccess(state, { access.refresh() })
        return
    }
    val sessionStamp = access.sessionStamp(state.origin)
    val boundSession = entry.savedStateHandle.get<String>("hu34_navigation_session")
    if (boundSession != null && boundSession != sessionStamp) {
        LaunchedEffect(entry.id, sessionStamp) {
            navController.navigate(ChaskiDestinations.MAIN) { popUpTo(0) { inclusive = true } }
        }
        LoadingState()
        return
    }
    entry.savedStateHandle["hu34_navigation_session"] = sessionStamp
    val needsMain = route in setOf(ChaskiDestinations.PRODUCT_DETAIL, ChaskiDestinations.CART, ChaskiDestinations.ADDRESS_BOOK)
    val mainEntry = if (needsMain) runCatching { navController.getBackStackEntry(ChaskiDestinations.MAIN) }.getOrNull() else null
    val mainStamp = mainEntry?.savedStateHandle?.get<String>("hu34_navigation_session")
    if (needsMain && (mainEntry == null || (mainStamp != null && mainStamp != sessionStamp))) {
        LaunchedEffect(entry.id) {
            val target = route!!.replace("{storeId}", android.net.Uri.encode(entry.arguments?.getString("storeId").orEmpty()))
                .replace("{productId}", android.net.Uri.encode(entry.arguments?.getString("productId").orEmpty()))
            navController.navigate(ChaskiDestinations.MAIN) { popUpTo(entry.destination.id) { inclusive = true } }
            navController.navigate(target) { launchSingleTop = true }
        }
        LoadingState()
        return
    }
    if (route == ChaskiDestinations.MAIN) {
        holder.SaveableStateProvider(access.instanceId + identityKey.toString()) {
            CompositionLocalProvider(LocalNavigationAccess provides access,
                    LocalProtectedAction provides { action -> access.runIfAllowedFrom(state.origin, route, businessId, storeId,
                        entry.arguments?.getBoolean("resubmit") == true, action = action) }) { content() }
        }
    } else {
        val cache: AccessDestinationCache = viewModel(viewModelStoreOwner = entry)
        val stamp = listOf(identityKey, resolution?.capabilities)
        val owner = cache.prepare(entry, stamp)
        val saveKey = access.instanceId + cache.version
        var lastKey by remember { mutableStateOf(saveKey) }
        if (lastKey != saveKey) { holder.removeState(lastKey); lastKey = saveKey }
        key(saveKey) {
            holder.SaveableStateProvider(saveKey) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner,
                    LocalNavigationAccess provides access,
                    LocalProtectedAction provides { action -> access.runIfAllowedFrom(state.origin, route, businessId, storeId,
                        entry.arguments?.getBoolean("resubmit") == true, action = action) }) { content() }
            }
        }
    }
}

@Composable
internal fun RestrictedAccess(state: AccessContextState, onRetry: () -> Unit) {
    val connectivity = androidx.compose.ui.platform.LocalContext.current.getSystemService(android.net.ConnectivityManager::class.java)
    // Un fallo HTTP no demuestra desconexión: solo usar OFFLINE cuando Android confirma que no hay red activa.
    val panel = if (state.phase == AccessContextPhase.ERROR && connectivity.activeNetwork == null)
        AuthorizationPanel.OFFLINE else authorizationPanel(state)
    AuthorizationPanelContent(panel, onRetry)
}