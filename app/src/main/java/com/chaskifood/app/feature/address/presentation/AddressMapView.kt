package com.chaskifood.app.feature.address.presentation

import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val DELIVERY_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Adapta el View nativo a Compose. La cámara inicial no asigna un domicilio. */
@Composable
internal fun AddressMapView(
    point: AddressCoordinates?,
    centerRevision: Int,
    onLoad: (AddressMapLoad) -> Unit,
    onMoveStarted: () -> Unit,
    onPoint: (AddressCoordinates, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentLoad by rememberUpdatedState(onLoad)
    val currentMove by rememberUpdatedState(onMoveStarted)
    val currentPoint by rememberUpdatedState(onPoint)
    val initialPoint = remember { point }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(mapView, lifecycle) {
        var disposed = false
        var started = false
        var resumed = false
        var styleLoaded = false
        var gesture = false
        val failure = MapView.OnDidFailLoadingMapListener {
            if (!disposed) currentLoad(AddressMapLoad.ERROR)
        }
        val rendered = MapView.OnDidFinishRenderingMapListener { fully ->
            if (!disposed && styleLoaded && fully) currentLoad(AddressMapLoad.READY)
        }
        mapView.addOnDidFailLoadingMapListener(failure)
        mapView.addOnDidFinishRenderingMapListener(rendered)
        mapView.getMapAsync { nativeMap ->
            if (!disposed) {
                map = nativeMap
                nativeMap.uiSettings.isAttributionEnabled = true
                nativeMap.uiSettings.isLogoEnabled = false
                nativeMap.uiSettings.isRotateGesturesEnabled = false
                nativeMap.uiSettings.isTiltGesturesEnabled = false
                nativeMap.cameraPosition = CameraPosition.Builder()
                    .target(initialPoint?.toLatLng() ?: LatLng(-9.19, -75.0152))
                    .zoom(if (initialPoint != null) 16.0 else 4.0).build()
                nativeMap.addOnMapClickListener { position ->
                    if (!disposed) currentPoint(AddressCoordinates(position.latitude, position.longitude), true)
                    true
                }
                nativeMap.addOnCameraMoveStartedListener { reason ->
                    if (!disposed && reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        gesture = true
                        currentMove()
                    }
                }
                nativeMap.addOnCameraIdleListener {
                    if (!disposed && gesture) {
                        gesture = false
                        nativeMap.cameraPosition.target?.let {
                            currentPoint(AddressCoordinates(it.latitude, it.longitude), false)
                        }
                    }
                }
                nativeMap.setStyle(DELIVERY_MAP_STYLE) { if (!disposed) styleLoaded = true }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failure)
            mapView.removeOnDidFinishRenderingMapListener(rendered)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, centerRevision) {
        point?.let { map?.moveCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 16.0)) }
    }
    AndroidView(factory = {
        mapView.apply {
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(
                    event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                false
            }
        }
    }, modifier = Modifier.fillMaxSize())
}

private fun AddressCoordinates.toLatLng() = LatLng(latitude, longitude)
