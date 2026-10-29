package com.chaskifood.app.core.navigation

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.chaskifood.app.core.access.data.AccessContextPhase
import com.chaskifood.app.core.access.domain.AccessResolution
import kotlinx.coroutines.launch

/** Un observador de ciclo de vida/red para el host, no uno por destino. */
@Composable
internal fun AccessNavigationEvents(access: AccessNavigationViewModel, nav: NavHostController) {
    if (!access.enabled) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by access.states.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    var backgrounded by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, access) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { backgrounded = true; access.invalidate() }
                Lifecycle.Event.ON_START -> if (backgrounded) { backgrounded = false; access.refresh(force = false) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context, access) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val recovery = AccessRecoveryTrigger { scope.launch { access.refresh() } }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                recovery.validated(validated)
            }
            override fun onLost(network: Network) { recovery.validated(false) }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    var previousGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var process by rememberSaveable { mutableStateOf(access.instanceId) }
    var pendingSessionCleanup by rememberSaveable { mutableStateOf(false) }
    var previousAccess by remember { mutableStateOf<AccessResolution.Resolved?>(null) }
    LaunchedEffect(state, entry?.id) {
        val route = entry?.destination?.route ?: return@LaunchedEffect
        val changedProcess = process != access.instanceId
        process = access.instanceId
        val changedSession = previousGeneration != null && previousGeneration != state.origin.generation
        previousGeneration = state.origin.generation
        if ((changedProcess || changedSession) &&
            runCatching { nav.getBackStackEntry(ChaskiDestinations.MAIN) }.getOrNull() != null) pendingSessionCleanup = true
        if ((pendingSessionCleanup || changedProcess || changedSession) && route !in AccessNavigationPolicy.publicRoutes) {
            pendingSessionCleanup = false
            nav.navigate(if (state.phase == AccessContextPhase.SIGNED_OUT) ChaskiDestinations.SIGN_IN else ChaskiDestinations.MAIN) {
                popUpTo(0) { inclusive = true }; launchSingleTop = true
            }
            previousAccess = null
            return@LaunchedEffect
        }
        if (state.phase == AccessContextPhase.SIGNED_OUT && route !in AccessNavigationPolicy.publicRoutes) {
            nav.navigate(ChaskiDestinations.SIGN_IN) { popUpTo(0) { inclusive = true } }
        } else if (state.phase == AccessContextPhase.RESOLVED) {
            val resolved = state.resolution as AccessResolution.Resolved
            val revoked = AccessNavigationPolicy.requiresReset(previousAccess, resolved, route)
            previousAccess = resolved
            if (route !in AccessNavigationPolicy.publicRoutes && route != ChaskiDestinations.MAIN &&
                (revoked || !access.allows(route, businessId = if (route.startsWith(ChaskiDestinations.STORE_ORDERS + "/")) entry?.arguments?.getString("businessId") else null, storeId = if (route.substringBefore('?') in setOf(ChaskiDestinations.STORE_OPERATIONS, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION) || route.startsWith(ChaskiDestinations.STORE_ORDERS + "/")) entry?.arguments?.getString("storeId") else null, resubmit = entry?.arguments?.getBoolean("resubmit") == true))) {
                if (!nav.popBackStack(ChaskiDestinations.MAIN, false)) {
                    nav.navigate(ChaskiDestinations.MAIN) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }
    var previousRoute by remember { mutableStateOf<String?>(null) }
    val commercial = remember { setOf(ChaskiDestinations.REGISTER_BUSINESS, ChaskiDestinations.BUSINESS_STATUS,
        ChaskiDestinations.MANAGE_STORES, ChaskiDestinations.STORE_OPERATIONS,
        ChaskiDestinations.MANAGE_CATALOG, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION,
        ChaskiDestinations.ADMIN_BUSINESS_REVIEW, ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION) }
    LaunchedEffect(entry?.id) {
        val route = entry?.destination?.route
        if (previousRoute?.substringBefore('?') in commercial && previousRoute != route) access.refresh()
        previousRoute = route
    }
}

/** La primera señal no es una reconexión; señales repetidas no duplican el refresco. */
internal class AccessRecoveryTrigger(private val refresh: () -> Unit) {
    private var previous: Boolean? = null
    fun validated(value: Boolean) {
        val recovered = previous == false && value
        previous = value
        if (recovered) refresh()
    }
}