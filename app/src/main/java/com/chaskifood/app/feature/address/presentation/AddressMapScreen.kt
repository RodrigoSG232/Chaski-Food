package com.chaskifood.app.feature.address.presentation

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
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
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiSurfaceVariant
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
                activity, Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            viewModel.permissionDenied(request.id, permanently = !canAskAgain)
        }
    }
    LaunchedEffect(request.id, state.retry, state.load) {
        if (state.requestId == request.id && state.load == AddressMapLoad.LOADING) {
            delay(25_000L)
            viewModel.mapLoad(request.id, state.retry, AddressMapLoad.ERROR)
        }
    }
    if (state.requestId != request.id) return
    AddressMapScreen(
        state, addressText, onCancel, onConfirm,
        onLocate = {
            when {
                context.hasLocationPermission() -> viewModel.locate(request.id)
                state.locationStatus == AddressLocationStatus.BLOCKED -> context.openLocationSettings(app = true)
                else -> permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
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
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.address_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ChaskiSurface),
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(ChaskiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
        ) {
            Text(stringResource(R.string.address_map_hint), color = ChaskiTextTertiary, style = MaterialTheme.typography.bodyMedium)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                key(state.requestId, state.retry) {
                    AddressMapView(state.point, state.centerRevision, onMapLoad, onMoveStarted, onPoint)
                }
                Icon(
                    Icons.Default.LocationOn, stringResource(R.string.address_map_pin),
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = (-20).dp)
                        .size(40.dp),
                    tint = if (state.point == null) ChaskiTextTertiary else ChaskiPrimary,
                )

                if (state.load != AddressMapLoad.READY) Surface(Modifier.fillMaxSize(), color = ChaskiSurface) {
                    Column(
                        Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.load == AddressMapLoad.LOADING) CircularProgressIndicator(color = ChaskiPrimary)
                        Text(
                            stringResource(
                                if (state.load == AddressMapLoad.LOADING)
                                    R.string.address_map_loading else R.string.address_map_error,
                            ),
                        )
                        if (state.load == AddressMapLoad.ERROR) AddressButton(stringResource(R.string.address_retry), onRetry)
                    }
                }
            }

            // Tarjeta destacada de previsualización de la ubicación seleccionada
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ChaskiSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ChaskiPrimary),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = ChaskiPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ubicación seleccionada en el mapa:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChaskiTextTertiary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                !state.suggestedAddress.isNullOrBlank() -> state.suggestedAddress
                                state.addressLookup == AddressLookupStatus.LOADING -> "Buscando dirección de la ubicación..."
                                state.point != null -> "Coordenadas: ${state.point.latitude}, ${state.point.longitude}"
                                else -> addressText.ifBlank { "Mueve el mapa para seleccionar el punto de entrega" }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChaskiTextPrimary,
                        )
                    }
                }
            }

            AddressButton(
                stringResource(
                    if (state.locationStatus == AddressLocationStatus.BLOCKED)
                        R.string.address_permission_settings else R.string.address_use_location,
                ), onLocate,
                enabled = state.locationStatus != AddressLocationStatus.LOCATING, outlined = true,
            )

            AddressButton(
                stringResource(R.string.address_confirm_point),
                { if (state.canConfirm && state.point != null) onConfirm(state.point, state.suggestedAddress) },
                enabled = state.canConfirm,
            )

            MapAttribution()
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

private fun Context.hasLocationPermission() = listOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
).any {
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