package com.chaskifood.app.feature.orders.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaskifood.app.feature.orders.domain.BuyerTrackingPosition
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Read-only map: no device location permission, route interpolation or invented movement. */
@Suppress("DEPRECATION")
@Composable
internal fun DeliveryTrackingMap(position: BuyerTrackingPosition, modifier: Modifier, onFailure: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failure by rememberUpdatedState(onFailure)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    val mapView = remember(context) { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }
    DisposableEffect(mapView, lifecycle) {
        var disposed = false
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
        val listener = MapView.OnDidFailLoadingMapListener { if (!disposed) failure(true) }
        mapView.addOnDidFailLoadingMapListener(listener)
        mapView.getMapAsync { native ->
            if (!disposed) {
                native.uiSettings.isAttributionEnabled = true
                native.uiSettings.isLogoEnabled = false
                native.uiSettings.isRotateGesturesEnabled = false
                native.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    if (!disposed) { map = native; failure(false) }
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(listener)
            stop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, position) {
        map?.let { native ->
            val point = LatLng(position.latitude, position.longitude)
            val existing = marker
            if (existing == null) marker = native.addMarker(MarkerOptions().position(point).title("Última ubicación del repartidor"))
            else existing.position = point
            native.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15.0))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
