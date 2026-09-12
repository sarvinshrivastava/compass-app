package com.pubpeb.compass.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.ArTriggerGate
import com.pubpeb.compass.sensor.CompassAccuracy
import com.pubpeb.compass.sensor.CompassSensorData
import com.pubpeb.compass.sensor.DeclinationRepository
import com.pubpeb.compass.sensor.SensorRepository
import com.pubpeb.compass.telemetry.Telemetry
import com.pubpeb.compass.ui.theme.ThemeMode
import com.pubpeb.compass.util.shortestArcDelta
import com.pubpeb.compass.util.shouldPublishHeading
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds compass state and bridges [SensorRepository] → Compose UI.
 *
 * Three behaviours here are deliberate product decisions rather than plumbing:
 *
 *  1. **The compass never waits for a permission.** [azimuth] is live from the
 *     first sensor event. Location only ever *upgrades* the reading to true
 *     north; it is never a precondition for showing one.
 *  2. **Sensors follow the UI, not the ViewModel.** The hardware flow is shared
 *     with [SharingStarted.WhileSubscribed], so it unregisters shortly after
 *     the last screen stops collecting. `viewModelScope` outlives `onStop`, so
 *     collecting on it directly kept both sensors hot in the user's pocket.
 *  3. **Confidence is first-class.** [accuracy] is published so the UI can say
 *     when the heading should not be trusted, including a synthesised
 *     [CompassAccuracy.NO_SIGNAL] when the sensor flow stops emitting.
 */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo = SensorRepository(application)
    private val declinationRepo = DeclinationRepository(application)
    private val prefs = application.getSharedPreferences(
        Constants.Prefs.NAME,
        Context.MODE_PRIVATE,
    )

    // ── Public state ──────────────────────────────────────────────────────────

    private val _azimuth = MutableStateFlow(0f)

    /**
     * Display heading in degrees [0, 360) — magnetic or true depending on
     * [trueNorth]. Guarded by [Constants.Heading.DEADBAND_DEG]: a phone sitting
     * still shows a still number instead of flickering ±1–2° at 52 sp.
     */
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    /**
     * Heavily low-pass-filtered azimuth for AR *label positioning* only. The AR
     * heading readout uses [azimuth] instead, so the number never lags behind
     * the flat screen's number for the same heading.
     */
    private val _arAzimuth = MutableStateFlow(0f)
    val arAzimuth: StateFlow<Float> = _arAzimuth.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    /**
     * Signed elevation of the rear-camera axis above the horizon, degrees
     * [-90, +90]. Positive = aimed at the sky. The AR overlay must place its
     * horizon with this and never with [pitch], which folds up and down onto
     * the same value — see [com.pubpeb.compass.sensor.CompassSensorData].
     */
    private val _cameraElevation = MutableStateFlow(0f)
    val cameraElevation: StateFlow<Float> = _cameraElevation.asStateFlow()

    private val _accuracy = MutableStateFlow(CompassAccuracy.NO_SIGNAL)
    val accuracy: StateFlow<CompassAccuracy> = _accuracy.asStateFlow()

    private val _trueNorth =
        MutableStateFlow(prefs.getBoolean(Constants.Prefs.KEY_TRUE_NORTH, true))
    val trueNorth: StateFlow<Boolean> = _trueNorth.asStateFlow()

    private val _declination = MutableStateFlow(0f)
    val declination: StateFlow<Float> = _declination.asStateFlow()

    /** True once a location fix has produced a real declination for this place. */
    private val _hasDeclination = MutableStateFlow(false)
    val hasDeclination: StateFlow<Boolean> = _hasDeclination.asStateFlow()

    private val _themeMode = MutableStateFlow(
        if (prefs.getBoolean(Constants.Prefs.KEY_DAYLIGHT, false)) {
            ThemeMode.DAYLIGHT
        } else {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /**
     * Whether raising the phone switches to the AR camera view on its own.
     *
     * On by default — the posture is a strong signal of intent. Off means the
     * camera button in the top bar is the only way in, which is the right
     * choice for anyone who found the automatic switch startling.
     */
    private val _arAutoSwitch =
        MutableStateFlow(prefs.getBoolean(Constants.Prefs.KEY_AR_AUTO, true))
    val arAutoSwitch: StateFlow<Boolean> = _arAutoSwitch.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(sensorRepo.isAvailable)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    /**
     * True when the device has been held upright long enough to mean it.
     * Always false while [arAutoSwitch] is off.
     */
    private val _isUpright = MutableStateFlow(false)
    val isUpright: StateFlow<Boolean> = _isUpright.asStateFlow()

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Raw magnetic azimuth from the sensor layer (already lightly filtered). */
    private var magneticAzimuth = 0f

    /** Last value actually published to [_azimuth] — the deadband reference. */
    private var publishedAzimuth = 0f

    /** Heavy second-stage low-pass filter state for [arAzimuth]. */
    private var arAzimuthFiltered = 0f

    private val arGate = ArTriggerGate(nowMs = SystemClock::elapsedRealtime)

    /** Elapsed-realtime millis of the most recent sensor emission. */
    @Volatile private var lastEmissionAt = 0L

    /** Last accuracy actually logged, so transitions log once rather than per event. */
    private var lastLoggedAccuracy: CompassAccuracy? = null

    /**
     * The hardware flow, shared so the sensors are registered only while a
     * screen is collecting.
     *
     * [SharingStarted.WhileSubscribed] with a stop timeout means a rotation or
     * a brief navigation does not thrash the sensor registration, but pocketing
     * the phone releases it. `awaitClose` in the repository unregisters the
     * listener once the upstream is cancelled.
     */
    private val sharedSensorData = sensorRepo.sensorData
        .onEach(::onSensorData)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = SENSOR_STOP_TIMEOUT_MS,
            ),
            initialValue = null,
        )

    init {
        // Collecting the shared flow is what starts the hardware; the UI drives
        // this via observeWhileVisible() rather than the ViewModel holding it open.
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Keeps the sensors running for as long as the calling scope lives.
     *
     * The UI calls this from a `repeatOnLifecycle(STARTED)` block, which is what
     * ties sensor registration to the screen actually being visible.
     */
    suspend fun observeWhileVisible() {
        sharedSensorData.collect { /* onSensorData does the work upstream */ }
    }

    fun toggleTrueNorth() {
        val next = !_trueNorth.value
        _trueNorth.value = next
        prefs.edit().putBoolean(Constants.Prefs.KEY_TRUE_NORTH, next).apply()
        Log.i(TAG, "trueNorth=$next declination=${_declination.value}")
        val display = computeDisplayAzimuth()
        publishedAzimuth = display
        _azimuth.value = display
        snapArFilter(display)
    }

    fun onArOpened(auto: Boolean) = Telemetry.onArOpened(auto)

    fun onLevelOpened() = Telemetry.onLevelOpened()

    fun onLocationOutcome(granted: Boolean) = Telemetry.onLocationOutcome(granted)

    fun toggleDaylightMode() {
        val next = if (_themeMode.value == ThemeMode.DAYLIGHT) {
            ThemeMode.SYSTEM
        } else {
            ThemeMode.DAYLIGHT
        }
        _themeMode.value = next
        prefs.edit()
            .putBoolean(Constants.Prefs.KEY_DAYLIGHT, next == ThemeMode.DAYLIGHT)
            .apply()
        Log.i(TAG, "themeMode=$next")
        Telemetry.onDaylightToggled(next == ThemeMode.DAYLIGHT)
    }

    fun setArAutoSwitch(enabled: Boolean) {
        _arAutoSwitch.value = enabled
        prefs.edit().putBoolean(Constants.Prefs.KEY_AR_AUTO, enabled).apply()
        Log.i(TAG, "arAutoSwitch=$enabled")
        if (!enabled) {
            // Drop out of any auto-engaged session immediately; a manual override
            // in the UI is unaffected because it does not read isUpright.
            arGate.reset()
            _isUpright.value = false
        }
    }

    /**
     * Fetches a single location fix and computes magnetic declination via
     * [GeomagneticField]. Safe to call when permission is absent — it fails
     * quietly and the app keeps showing magnetic north.
     */
    /**
     * Upgrades the heading from magnetic to true north, if it can.
     *
     * Delegates the whole Play Services interaction to [DeclinationRepository];
     * this only decides what to do with the answer. Failure is not exceptional —
     * the compass simply stays on magnetic north.
     */
    fun updateDeclination() {
        viewModelScope.launch {
            val declination = declinationRepo.fetchDeclination()
            Telemetry.onDeclinationOutcome(acquired = declination != null)
            if (declination == null) return@launch

            _declination.value = declination
            _hasDeclination.value = true
            Log.i(TAG, "declination acquired: $declination")

            val display = computeDisplayAzimuth()
            publishedAzimuth = display
            _azimuth.value = display
        }
    }

    // ── Sensor pipeline ───────────────────────────────────────────────────────

    private fun onSensorData(data: CompassSensorData) {
        lastEmissionAt = SystemClock.elapsedRealtime()

        magneticAzimuth = data.azimuth
        val display = computeDisplayAzimuth()

        if (shouldPublishHeading(display, publishedAzimuth)) {
            publishedAzimuth = display
            _azimuth.value = display
        }

        // Second-stage heavy LPF for AR label positions — shortest-arc blend, so
        // a naive average of 1° and 359° cannot produce 180°.
        val arc = shortestArcDelta(display, arAzimuthFiltered)
        arAzimuthFiltered = (arAzimuthFiltered + Constants.Ar.SMOOTHING_ALPHA * arc + 360f) % 360f
        _arAzimuth.value = arAzimuthFiltered

        _pitch.value = data.pitch
        _roll.value = data.roll
        _cameraElevation.value = data.cameraElevationDeg

        publishAccuracy(data.accuracy)

        _isUpright.value =
            if (_arAutoSwitch.value) arGate.update(data.tiltFromFlatDeg) else false
    }

    /** Publishes accuracy and logs only the transitions, not every event. */
    private fun publishAccuracy(accuracy: CompassAccuracy) {
        _accuracy.value = accuracy
        if (accuracy != lastLoggedAccuracy) {
            Log.i(TAG, "accuracy ${lastLoggedAccuracy ?: "—"} -> $accuracy")
            // Every crash report carries the sensor state it happened in.
            Telemetry.setAccuracyKey(accuracy)
            // The sensor going silent is invisible otherwise: no exception, no
            // crash, just a frozen heading nobody thinks to report.
            //
            // This fires on the FIRST NO_SIGNAL of a session as well as on a
            // transition into it. Requiring a prior good reading meant the worst
            // case — a device where fusion never works at all, so the very first
            // watchdog tick reports NO_SIGNAL with no prior accuracy — was the
            // one case that stayed silent. "My compass never worked" is exactly
            // the report this exists to catch.
            if (accuracy == CompassAccuracy.NO_SIGNAL &&
                lastLoggedAccuracy != CompassAccuracy.NO_SIGNAL
            ) {
                Telemetry.reportSensorStall()
            }
            lastLoggedAccuracy = accuracy
        }
        Telemetry.sampleAccuracy(accuracy)

        // Time from opening the app to a heading worth acting on. Telemetry owns
        // the session clock (see ProcessLifecycleOwner there) and keeps only the
        // first such moment per session.
        if (accuracy.isTrustworthy) Telemetry.onHeadingBecameStable()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Immediately snaps the AR azimuth filter to [value], preventing slow drift. */
    private fun snapArFilter(value: Float) {
        arAzimuthFiltered = value
        _arAzimuth.value = value
    }

    /**
     * Synthesises [CompassAccuracy.NO_SIGNAL] when the sensor flow goes quiet.
     *
     * `getRotationMatrix` returning false stops emissions entirely, which used
     * to freeze the UI at a confident "0° NORTH" with no indication anything
     * was wrong. Silence is now a reportable state.
     *
     * Runs only while a screen is subscribed, for the same reason the sensors do.
     */
    suspend fun watchForStaleSensor() {
        // Each subscription starts its own grace period. `lastEmissionAt` is a
        // field on the ViewModel, which outlives the screen: returning to the
        // app after more than STALE_AFTER_MS away left the PREVIOUS session's
        // timestamp in place, so the first tick compared against it, found it
        // stale, and published NO_SIGNAL before the sensors had a chance to
        // register. That fired SensorStalledException as a non-fatal — turning
        // the one diagnostic added to catch a real, invisible failure into
        // noise that buries the genuine cases.
        lastEmissionAt = SystemClock.elapsedRealtime()
        while (true) {
            delay(Constants.Sensor.WATCHDOG_POLL_MS)
            val last = lastEmissionAt
            val stale = last == 0L ||
                SystemClock.elapsedRealtime() - last > Constants.Sensor.STALE_AFTER_MS
            if (stale) publishAccuracy(CompassAccuracy.NO_SIGNAL)
        }
    }

    private fun computeDisplayAzimuth(): Float =
        if (_trueNorth.value && _hasDeclination.value) {
            (magneticAzimuth + _declination.value + 360f) % 360f
        } else {
            magneticAzimuth
        }

    companion object {
        private const val TAG = "CompassViewModel"

        /**
         * Grace period before the shared sensor flow tears down. Long enough to
         * survive a rotation or a quick app switch, short enough that pocketing
         * the phone genuinely releases the hardware.
         */
        private const val SENSOR_STOP_TIMEOUT_MS = 5_000L

    }
}
