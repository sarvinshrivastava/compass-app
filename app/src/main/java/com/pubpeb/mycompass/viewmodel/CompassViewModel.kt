package com.pubpeb.mycompass.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.hardware.GeomagneticField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pubpeb.mycompass.sensor.SensorRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Holds compass state and bridges [SensorRepository] → Compose UI.
 *
 * Exposed state:
 *  - [azimuth]     – current heading in degrees [0, 360); magnetic or true depending on [trueNorth].
 *  - [tilt]        – device pitch in degrees (positive = top tilted toward user).
 *  - [roll]        – device roll in degrees (positive = left edge raised).
 *  - [trueNorth]   – toggle between magnetic and true north.
 *  - [declination] – computed magnetic declination (degrees).
 *
 * // TODO Phase 2 – ARCore hook:
 * //   Add an ArSession field here and collect pose updates from ArFrame.getCamera().
 * //   Blend or override azimuth with ARCore's camera-based heading when the session is tracking.
 * //   Expose an `arSessionAvailable: StateFlow<Boolean>` so the UI can show an AR toggle.
 */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo = SensorRepository(application)
    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

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

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Raw magnetic azimuth from sensor layer. */
    private var magneticAzimuth = 0f

    init {
        sensorRepo.sensorData
            .onEach { data ->
                magneticAzimuth = data.azimuth
                _azimuth.value = computeDisplayAzimuth()
                _tilt.value = data.pitch
                _roll.value = data.roll
            }
            .launchIn(viewModelScope)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun toggleTrueNorth() {
        _trueNorth.value = !_trueNorth.value
        _azimuth.value = computeDisplayAzimuth()
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
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { location: Location? ->
                        location ?: return@addOnSuccessListener
                        val geoField = GeomagneticField(
                            location.latitude.toFloat(),
                            location.longitude.toFloat(),
                            location.altitude.toFloat(),
                            System.currentTimeMillis(),
                        )
                        _declination.value = geoField.declination
                        // Recompute displayed heading with new declination
                        _azimuth.value = computeDisplayAzimuth()
                    }
            } catch (_: SecurityException) {
                // Permission was revoked between grant and this call — safe to ignore.
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
}
