package com.pubpeb.mycompass.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.hardware.GeomagneticField
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pubpeb.mycompass.sensor.SensorRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Holds compass state and bridges [SensorRepository] → Compose UI.
 *
 * Exposed state:
 *  - [azimuth]     – current heading in degrees [0, 360); magnetic or true depending on [trueNorth].
 *  - [arAzimuth]   – heavily low-pass-filtered heading (α=0.05) for stable AR label positioning.
 *  - [tilt]        – device pitch in degrees (positive = top tilted toward user).
 *  - [roll]        – device roll in degrees (positive = left edge raised).
 *  - [trueNorth]   – toggle between magnetic and true north.
 *  - [declination] – computed magnetic declination (degrees).
 */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo = SensorRepository(application)
    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    /**
     * Heavily low-pass-filtered azimuth (second-stage α = 0.05) intended for
     * AR label positioning. The sensor layer already applies α = 0.15 to the
     * raw hardware values; this adds a second stage that dramatically reduces
     * jitter when the phone is held upright during AR mode, at the cost of a
     * slightly longer settling time (~20 sensor events ≈ 400 ms at SENSOR_DELAY_UI).
     *
     * Uses shortest-arc blending to handle the 0°/360° wrap-around correctly —
     * a naïve weighted average of e.g. 1° and 359° would produce 180° instead of 0°.
     */
    private val _arAzimuth = MutableStateFlow(0f)
    val arAzimuth: StateFlow<Float> = _arAzimuth.asStateFlow()

    private val _tilt = MutableStateFlow(0f)
    val tilt: StateFlow<Float> = _tilt.asStateFlow()

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _trueNorth = MutableStateFlow(false)
    val trueNorth: StateFlow<Boolean> = _trueNorth.asStateFlow()

    private val _declination = MutableStateFlow(0f)
    val declination: StateFlow<Float> = _declination.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(sensorRepo.isAvailable)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    /**
     * True when the device is held upright (portrait, camera facing a scene).
     * Derived from the raw gravity vector in [SensorRepository] — NOT from the remapped
     * pitch — so it remains accurate even after remapCoordinateSystem() changes orientation[1].
     * Used by [MainScreen] to auto-trigger AR mode without relying on a pitch threshold.
     */
    private val _isUpright = MutableStateFlow(false)
    val isUpright: StateFlow<Boolean> = _isUpright.asStateFlow()

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Raw magnetic azimuth from sensor layer (already lightly filtered at α=0.15). */
    private var magneticAzimuth = 0f

    /**
     * Heavy second-stage low-pass filter for [arAzimuth].
     * α = 0.05 → very smooth, ~20 events to reach 63 % of a step change.
     * Uses shortest-arc blending: find the signed shortest arc from current
     * to new value (always in (-180, +180]), then scale it by α and add.
     * This avoids the wrap-around artefact of naïvely mixing degree values.
     */
    private var arAzimuthFiltered = 0f
    private val arAlpha = 0.05f

    init {
        sensorRepo.sensorData
            .onEach { data ->
                magneticAzimuth = data.azimuth
                val display = computeDisplayAzimuth()
                _azimuth.value = display

                // Second-stage heavy LPF for AR — shortest-arc blend
                val arc = ((display - arAzimuthFiltered + 540f) % 360f) - 180f
                arAzimuthFiltered = (arAzimuthFiltered + arAlpha * arc + 360f) % 360f
                _arAzimuth.value = arAzimuthFiltered

                _tilt.value = data.pitch
                _roll.value = data.roll
                _isUpright.value = data.isUpright
            }
            .launchIn(viewModelScope)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun toggleTrueNorth() {
        _trueNorth.value = !_trueNorth.value
        val display = computeDisplayAzimuth()
        _azimuth.value = display
        snapArFilter(display)
    }

    /** Immediately snaps the AR azimuth filter to [value], preventing slow drift after heading resets. */
    private fun snapArFilter(value: Float) {
        arAzimuthFiltered = value
        _arAzimuth.value = value
    }

    /**
     * Fetches a single GPS fix and computes the magnetic declination via [GeomagneticField].
     * Call this after ACCESS_FINE_LOCATION is granted.
     */
    @SuppressLint("MissingPermission")
    fun updateDeclination() {
        viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val location = suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                        .addOnCanceledListener { cont.cancel() }
                    cont.invokeOnCancellation { cts.cancel() }
                }
                location ?: run {
                    Log.d(TAG, "updateDeclination: GPS fix returned null, using magnetic north")
                    return@launch
                }
                val geoField = GeomagneticField(
                    location.latitude.toFloat(),
                    location.longitude.toFloat(),
                    location.altitude.toFloat(),
                    System.currentTimeMillis(),
                )
                _declination.value = geoField.declination
                _azimuth.value = computeDisplayAzimuth()
            } catch (_: SecurityException) {
                // Permission was revoked between grant and call — safe to ignore.
            } catch (e: Exception) {
                Log.w(TAG, "updateDeclination failed", e)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun computeDisplayAzimuth(): Float {
        val raw = if (_trueNorth.value) {
            (magneticAzimuth + _declination.value + 360f) % 360f
        } else {
            magneticAzimuth
        }
        return raw
    }

    companion object {
        private const val TAG = "CompassViewModel"
    }
}
