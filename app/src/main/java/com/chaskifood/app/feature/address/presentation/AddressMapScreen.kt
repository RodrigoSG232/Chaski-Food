package com.chaskifood.app.feature.address.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.R
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiTextTertiary
import kotlinx.coroutines.delay

@Composable
fun AddressMapRoute(
    request: AddressMapRequest,
    addressText: String,
    onCancel: () -> Unit,
    onConfirm: (AddressCoordinates, String?) -> Unit,
    viewModel: AddressMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(request.id, lifecycle) {
        viewModel.open(request)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelLocation()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.close(request.id)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) viewModel.locate(request.id)
        else {
            val activity = context.findActivity()
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            viewModel.permissionDenied(request.id, permanently = !canAskAgain)
        }
    }
    LaunchedEffect(request.id, state.retry, state.load) {
        if (state.requestId == request.id && state.load == AddressMapLoad.LOADING) {
            delay(25_000L)
            viewModel.mapLoad(request.id, state.retry, AddressMapLoad.ERROR)
        }
    }
    LaunchedEffect(request.id, state.addressLookup, state.suggestedAddress) {
        if (state.requestId == request.id && state.addressLookup == AddressLookupStatus.FOUND) {
            val point = state.point
            val suggestion = state.suggestedAddress
            if (point != null && !suggestion.isNullOrBlank()) onConfirm(point, suggestion)
        }
    }
    if (state.requestId != request.id) return
    AddressMapScreen(state, addressText, onCancel, onConfirm,
        onLocate = {
            when {
                context.hasLocationPermission() -> viewModel.locate(request.id)
                state.locationStatus == AddressLocationStatus.BLOCKED -> context.openLocationSettings(app = true)
                else -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION))
            }
        },
        onCancelLocation = viewModel::cancelLocation,
        onLocationSettings = { context.openLocationSettings(app = false) },
        onRetry = { viewModel.retryMap(request.id) },
        onMapLoad = { viewModel.mapLoad(request.id, state.retry, it) },
        onMoveStarted = { viewModel.moveStarted(request.id) },
        onPoint = { point, recenter -> viewModel.selectPoint(request.id, point, recenter) },
        onSuggestAddress = { viewModel.suggestAddress(request.id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressMapScreen(
    state: AddressMapState,
    addressText: String,
    onCancel: () -> Unit,
    onConfirm: (AddressCoordinates, String?) -> Unit,
    onLocate: () -> Unit,
    onCancelLocation: () -> Unit,
    onLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    onMapLoad: (AddressMapLoad) -> Unit,
    onMoveStarted: () -> Unit,
    onPoint: (AddressCoordinates, Boolean) -> Unit,
    onSuggestAddress: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Scaffold(containerColor = ChaskiSurface, contentColor = ChaskiTextPrimary, topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.address_map_title), style = MaterialTheme.typography.headlineMedium) },
            navigationIcon = { IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.address_back))
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ChaskiSurface),
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.address_map_hint), color = ChaskiTextTertiary)
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp))) {
                key(state.requestId, state.retry) {
                    AddressMapView(state.point, state.centerRevision, onMapLoad, onMoveStarted, onPoint)
                }
                Icon(Icons.Default.LocationOn, stringResource(R.string.address_map_pin),
                    Modifier.align(Alignment.Center).offset(y = (-20).dp).size(40.dp),
                    tint = if (state.point == null) ChaskiTextTertiary else ChaskiPrimary)
                if (state.load != AddressMapLoad.READY) Surface(Modifier.fillMaxSize(), color = ChaskiSurface) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.load == AddressMapLoad.LOADING) CircularProgressIndicator(color = ChaskiPrimary)
                        Text(stringResource(if (state.load == AddressMapLoad.LOADING)
                            R.string.address_map_loading else R.string.address_map_error))
                        if (state.load == AddressMapLoad.ERROR) AddressButton(stringResource(R.string.address_retry), onRetry)
                    }
                }
            }
            MapAttribution()
            AddressButton(stringResource(if (state.locationStatus == AddressLocationStatus.BLOCKED)
                R.string.address_permission_settings else R.string.address_use_location), onLocate,
                enabled = state.locationStatus != AddressLocationStatus.LOCATING, outlined = true)
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when (state.locationStatus) {
                    AddressLocationStatus.IDLE -> R.string.address_location_explanation
                    AddressLocationStatus.LOCATING -> R.string.address_location_loading
                    AddressLocationStatus.FOUND -> R.string.address_location_found
                    AddressLocationStatus.APPROXIMATE -> R.string.address_location_approximate
                    AddressLocationStatus.DENIED -> R.string.address_location_denied
                    AddressLocationStatus.BLOCKED -> R.string.address_location_blocked
                    AddressLocationStatus.DISABLED -> R.string.address_location_disabled
                    AddressLocationStatus.UNAVAILABLE -> R.string.address_location_unavailable
                }), style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary)
                state.accuracyMeters?.let {
                    Text(stringResource(R.string.address_location_accuracy, it.toInt()), style = MaterialTheme.typography.bodySmall)
                }
                if (state.locationStatus == AddressLocationStatus.LOCATING) {
                    TextButton(onClick = onCancelLocation) { Text(stringResource(R.string.address_location_cancel)) }
                }
                if (state.locationStatus == AddressLocationStatus.DISABLED) {
                    AddressButton(stringResource(R.string.address_location_settings), onLocationSettings, outlined = true)
                }
            }
            Text(addressText.ifBlank { stringResource(R.string.address_map_review_text) },
                style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (state.point == null) R.string.address_point_required else R.string.address_map_review),
                color = ChaskiTextTertiary, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.address_geocoding_notice),
                color = ChaskiTextTertiary, style = MaterialTheme.typography.bodySmall)
            when (state.addressLookup) {
                AddressLookupStatus.LOADING -> Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = ChaskiPrimary, strokeWidth = 2.dp)
                    Text(stringResource(R.string.address_geocoding_loading))
                }
                AddressLookupStatus.UNAVAILABLE -> {
                    Text(stringResource(R.string.address_geocoding_unavailable),
                        color = ChaskiTextTertiary, style = MaterialTheme.typography.bodySmall)
                    AddressButton(stringResource(R.string.address_confirm_point_manual),
                        { if (state.canConfirm) state.point?.let { onConfirm(it, null) } }, enabled = state.canConfirm)
                }
                AddressLookupStatus.IDLE, AddressLookupStatus.FOUND -> AddressButton(
                    stringResource(R.string.address_confirm_point), onSuggestAddress, enabled = state.canConfirm)
            }
            Text(stringResource(R.string.address_map_not_saved), style = MaterialTheme.typography.bodySmall,
                color = ChaskiTextTertiary)
        }
    }
}

@Composable
private fun MapAttribution() {
    val uriHandler = LocalUriHandler.current
    Column {
        TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { uriHandler.openUri("https://openfreemap.org/") }) {
            Text("OpenFreeMap · © OpenMapTiles", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Context.hasLocationPermission() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION).any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openLocationSettings(app: Boolean) {
    val intent = if (app) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.address_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}
