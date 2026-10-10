package com.chaskifood.app.feature.address.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.DeviceLocationProvider
import com.chaskifood.app.feature.address.domain.DeviceLocationResult
import com.chaskifood.app.feature.address.domain.ReverseGeocodingProvider
import com.chaskifood.app.feature.address.domain.ReverseGeocodingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AddressMapLoad { LOADING, READY, ERROR }
enum class AddressLocationStatus { IDLE, LOCATING, FOUND, APPROXIMATE, DENIED, BLOCKED, DISABLED, UNAVAILABLE }
enum class AddressLookupStatus { IDLE, LOADING, FOUND, UNAVAILABLE }

data class AddressMapState(
    val requestId: String? = null,
    val point: AddressCoordinates? = null,
    val centerRevision: Int = 0,
    val moving: Boolean = false,
    val load: AddressMapLoad = AddressMapLoad.LOADING,
    val retry: Int = 0,
    val locationStatus: AddressLocationStatus = AddressLocationStatus.IDLE,
    val accuracyMeters: Float? = null,
    val addressLookup: AddressLookupStatus = AddressLookupStatus.IDLE,
    val suggestedAddress: String? = null,
) {
    val canConfirm: Boolean get() = load == AddressMapLoad.READY && !moving &&
        locationStatus != AddressLocationStatus.LOCATING && addressLookup != AddressLookupStatus.LOADING &&
        point?.isValid == true
}

@HiltViewModel
class AddressMapViewModel @Inject constructor(
    private val locationProvider: DeviceLocationProvider,
    private val reverseGeocodingProvider: ReverseGeocodingProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddressMapState())
    val uiState = _uiState.asStateFlow()
    private var locationJob: Job? = null
    private var addressJob: Job? = null
    private var generation = 0L
    private var addressGeneration = 0L
    private var active = false

    fun open(request: AddressMapRequest) {
        active = true
        if (_uiState.value.requestId != request.id) {
            cancelLocation()
            cancelAddressLookup()
            _uiState.value = AddressMapState(requestId = request.id, point = request.initialPoint)
            request.initialPoint?.let { if (it.isValid) suggestAddressForPoint(request.id, it) }
        } else _uiState.value = _uiState.value.copy(load = AddressMapLoad.LOADING, moving = false)
    }

    fun close(requestId: String) {
        if (_uiState.value.requestId != requestId) return
        active = false
        cancelLocation()
        cancelAddressLookup()
    }

    fun cancelLocation() {
        generation++
        locationJob?.cancel()
        locationJob = null
        if (_uiState.value.locationStatus == AddressLocationStatus.LOCATING) {
            _uiState.value = _uiState.value.copy(locationStatus = AddressLocationStatus.IDLE)
        }
    }

    fun locate(requestId: String) {
        if (!accepts(requestId)) return
        cancelLocation()
        cancelAddressLookup()
        val token = generation
        _uiState.value = _uiState.value.copy(locationStatus = AddressLocationStatus.LOCATING, accuracyMeters = null)
        locationJob = viewModelScope.launch {
            val result = try { locationProvider.currentLocation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DeviceLocationResult.Unavailable }
            if (!accepts(requestId) || token != generation) return@launch
            val old = _uiState.value
            when (result) {
                is DeviceLocationResult.Available -> {
                    if (!result.coordinates.isValid) {
                        _uiState.value = old.copy(locationStatus = AddressLocationStatus.UNAVAILABLE)
                        return@launch
                    }
                    _uiState.value = old.copy(
                        point = result.coordinates, centerRevision = old.centerRevision + 1, moving = false,
                        accuracyMeters = result.accuracyMeters,
                        locationStatus = if (result.approximate) AddressLocationStatus.APPROXIMATE else AddressLocationStatus.FOUND,
                        suggestedAddress = null,
                    )
                    suggestAddressForPoint(requestId, result.coordinates)
                }
                DeviceLocationResult.Disabled -> _uiState.value = old.copy(locationStatus = AddressLocationStatus.DISABLED)
                DeviceLocationResult.PermissionRequired -> _uiState.value = old.copy(locationStatus = AddressLocationStatus.DENIED)
                DeviceLocationResult.Unavailable -> _uiState.value = old.copy(locationStatus = AddressLocationStatus.UNAVAILABLE)
            }
        }
    }

    fun permissionDenied(requestId: String, permanently: Boolean) {
        if (!accepts(requestId)) return
        _uiState.value = _uiState.value.copy(locationStatus =
            if (permanently) AddressLocationStatus.BLOCKED else AddressLocationStatus.DENIED)
    }

    fun moveStarted(requestId: String) {
        if (!accepts(requestId)) return
        cancelLocation()
        cancelAddressLookup()
        _uiState.value = _uiState.value.copy(moving = true)
    }

    fun selectPoint(requestId: String, point: AddressCoordinates, recenter: Boolean) {
        if (!accepts(requestId) || !point.isValid) return
        cancelLocation()
        cancelAddressLookup()
        val old = _uiState.value
        _uiState.value = old.copy(
            point = point, moving = false, accuracyMeters = null,
            locationStatus = AddressLocationStatus.IDLE,
            addressLookup = AddressLookupStatus.LOADING, suggestedAddress = null,
            centerRevision = old.centerRevision + if (recenter) 1 else 0,
        )
        suggestAddressForPoint(requestId, point)
    }

    fun suggestAddress(requestId: String) {
        if (!accepts(requestId)) return
        val point = _uiState.value.point?.takeIf { it.isValid } ?: return
        suggestAddressForPoint(requestId, point)
    }

    private fun suggestAddressForPoint(requestId: String, point: AddressCoordinates) {
        cancelAddressLookup()
        val token = ++addressGeneration
        _uiState.value = _uiState.value.copy(addressLookup = AddressLookupStatus.LOADING, suggestedAddress = null)
        addressJob = viewModelScope.launch {
            val result = try { reverseGeocodingProvider.addressFor(point) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ReverseGeocodingResult.Unavailable }
            if (!accepts(requestId) || token != addressGeneration) return@launch
            _uiState.value = when (result) {
                is ReverseGeocodingResult.Found -> _uiState.value.copy(
                    addressLookup = AddressLookupStatus.FOUND, suggestedAddress = result.address,
                )
                ReverseGeocodingResult.Unavailable -> _uiState.value.copy(
                    addressLookup = AddressLookupStatus.UNAVAILABLE,
                    suggestedAddress = null,
                )
            }
        }
    }

    private fun cancelAddressLookup() {
        addressGeneration++
        addressJob?.cancel()
        addressJob = null
        if (_uiState.value.addressLookup == AddressLookupStatus.LOADING) {
            _uiState.value = _uiState.value.copy(addressLookup = AddressLookupStatus.IDLE)
        }
    }

    fun mapLoad(requestId: String, attempt: Int, load: AddressMapLoad) {
        if (accepts(requestId) && _uiState.value.retry == attempt) {
            _uiState.value = _uiState.value.copy(load = load)
        }
    }

    fun retryMap(requestId: String) {
        if (!accepts(requestId)) return
        _uiState.value = _uiState.value.copy(load = AddressMapLoad.LOADING, retry = _uiState.value.retry + 1, moving = false)
    }

    private fun accepts(requestId: String) = active && _uiState.value.requestId == requestId
}
