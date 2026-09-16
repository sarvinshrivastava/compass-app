package com.pubpeb.compass.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.ArTriggerGate
import com.pubpeb.compass.sensor.CameraWarmGate
import com.pubpeb.compass.sensor.HorizonCommitGate
import com.pubpeb.compass.sensor.CompassAccuracy
import com.pubpeb.compass.sensor.CompassSensorData
import com.pubpeb.compass.sensor.DeclinationRepository
import com.pubpeb.compass.sensor.LevelPosture
import com.pubpeb.compass.sensor.LevelPostureGate
import com.pubpeb.compass.sensor.SensorRepository
import com.pubpeb.compass.telemetry.Telemetry
import com.pubpeb.compass.ui.theme.ThemeMode
import com.pubpeb.compass.util.adaptiveLowPass
import com.pubpeb.compass.util.shortestArcDelta
import com.pubpeb.compass.util.shouldPublishHeading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /**
     * Smoothed elevation for AR *horizon placement* only — the vertical twin of
     * [arAzimuth], and for the same reason.
     *
     * Label X went through a heavy second-stage filter and label Y went through
     * none, so the overlay was steady horizontally and jittery vertically off
     * one hand's tremor. Two-speed rather than flat, because unlike a bearing
     * this value is checked against the camera image behind it: see
     * [Constants.Ar.ELEVATION_SLOW_ALPHA].
     */
    // Internal: the AR overlay reads [arHorizonElevation], which is this value
    // after HorizonCommitGate has decided the horizon may move. Published
    // separately once; that left two elevation flows where AD-1 allows one.
    private val _arCameraElevation = MutableStateFlow(0f)

    /**
     * Rotation about the device's viewing axis, degrees [-180, +180], 0 = plumb.
     * The upright level's whole reading. Meaningless while [levelPosture] is
     * [LevelPosture.FLAT] — see [SensorRepository.screenRoll].
     */
    private val _screenRoll = MutableStateFlow(0f)
    val screenRoll: StateFlow<Float> = _screenRoll.asStateFlow()

    /** Which instrument the level screen should draw. */
    private val _levelPosture = MutableStateFlow(LevelPosture.FLAT)
    val levelPosture: StateFlow<LevelPosture> = _levelPosture.asStateFlow()

    private val _accuracy = MutableStateFlow(CompassAccuracy.NO_SIGNAL)
    val accuracy: StateFlow<CompassAccuracy> = _accuracy.asStateFlow()

    private val _trueNorth =
        MutableStateFlow(prefs.getBoolean(Constants.Prefs.KEY_TRUE_NORTH, true))
    val trueNorth: StateFlow<Boolean> = _trueNorth.asStateFlow()

    private val _declination = MutableStateFlow(0f)
    val declination: StateFlow<Float> = _declination.asStateFlow()

    /** True once a declination — fresh or remembered — is available. */
    private val _hasDeclination = MutableStateFlow(false)
    val hasDeclination: StateFlow<Boolean> = _hasDeclination.asStateFlow()

    /**
     * Bearing the user has locked, in degrees, or null when none is set.
     *
     * "Hold this direction" is the question a compass is most often actually
     * asked, and answering it from the raw heading means doing arithmetic in
     * your head while walking. Of the largest compass apps on Play none offers
     * it; the ones that do are the specialist navigation tools.
     *
     * Persisted, because the bearing you are following outlives the app being
     * in the foreground — losing it to a pocket or a phone call would make the
     * feature useless for the one activity it exists for.
     */
    private val _lockedBearing = MutableStateFlow(
        prefs.getFloat(Constants.Prefs.KEY_LOCKED_BEARING, -1f).takeIf { it >= 0f }
    )
    val lockedBearing: StateFlow<Float?> = _lockedBearing.asStateFlow()

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
     * Whether this device can measure a heading at all.
     *
     * Constant for the life of the process — hardware does not appear — but
     * published as state so the UI reads it the same way it reads everything
     * else rather than reaching back into the repository.
     *
     * Separate from [hasAccelerometer] deliberately. A phone with no
     * magnetometer cannot show a compass and *can* show the level, and
     * [sensorAvailable] answers neither question on its own: it was already
     * computed here, collected by nothing, so a device with no magnetometer
     * showed a confident, frozen `0° NORTH` — the exact failure the accuracy
     * watchdog exists to prevent, arriving through the one door it does not
     * cover.
     */
    val hasMagnetometer: StateFlow<Boolean> =
        MutableStateFlow(sensorRepo.hasMagnetometer).asStateFlow()

    /** Whether this device can measure tilt, which is all the level needs. */
    val hasAccelerometer: StateFlow<Boolean> =
        MutableStateFlow(sensorRepo.hasAccelerometer).asStateFlow()

    /**
     * True when the device has been held upright long enough to mean it.
     * Always false while [arAutoSwitch] is off.
     */
    private val _isUpright = MutableStateFlow(false)
    val isUpright: StateFlow<Boolean> = _isUpright.asStateFlow()

    /**
     * False until the first sensor sample has been processed.
     *
     * [isUpright] starts false because there is nothing else it could start as,
     * and the hardware only starts when the UI subscribes — which happens after
     * the first composition. So for the opening frames "not upright" means
     * "not known yet", and anything treating those two as the same thing is
     * wrong: the AR arming rule did, and consequently a cold start with the
     * phone already raised dropped the user straight into the camera.
     *
     * Set last in [onSensorData], so a reader that recomposes on this flip sees
     * every other value already settled.
     */
    private val _sensorReady = MutableStateFlow(false)
    val sensorReady: StateFlow<Boolean> = _sensorReady.asStateFlow()

    /**
     * True while AR entry is pending its dwell — the phone is up but the gate
     * has not opened yet.
     *
     * The UI uses this to warm the CameraX provider during the wait instead of
     * after it. Nothing user-visible may hang off this: it is set on tilt
     * alone and falls back the moment the phone dips, and it must never open
     * the camera. See [ArTriggerGate.isArming].
     */
    // Internal: its only consumer is [cameraWarmGate], which debounces it into
    // [cameraWarm]. Exposing the raw signal invites exactly the undebounced use
    // its own KDoc forbids.
    private val _arArming = MutableStateFlow(false)

    /**
     * Whether the camera may be opened ahead of AR engaging — the debounced
     * form of [arArming], which flips at the sensor rate for a hand hovering
     * near the threshold. See [CameraWarmGate].
     */
    private val _cameraWarm = MutableStateFlow(false)
    val cameraWarm: StateFlow<Boolean> = _cameraWarm.asStateFlow()

    /**
     * Elevation the AR horizon rule is drawn at — committed, not live, so the
     * rule holds still until the device is genuinely re-aimed. See
     * [HorizonCommitGate].
     */
    private val _arHorizonElevation = MutableStateFlow(0f)
    val arHorizonElevation: StateFlow<Float> = _arHorizonElevation.asStateFlow()

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Raw magnetic azimuth from the sensor layer (already lightly filtered). */
    private var magneticAzimuth = 0f

    /** Last value actually published to [_azimuth] — the deadband reference. */
    private var publishedAzimuth = 0f

    /** Heavy second-stage low-pass filter state for [arAzimuth]. */
    private var arAzimuthFiltered = 0f

    /** Two-speed second-stage filter state for [arCameraElevation]. */
    private var arElevationFiltered = 0f

    private val arGate = ArTriggerGate(nowMs = SystemClock::elapsedRealtime)

    private val levelGate = LevelPostureGate()

    private val horizonGate = HorizonCommitGate(nowMs = SystemClock::elapsedRealtime)

    private val cameraWarmGate = CameraWarmGate(nowMs = SystemClock::elapsedRealtime)

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
        restoreRememberedDeclination()
    }

    /**
     * Reinstates the last declination this device resolved, if it is recent
     * enough to still be true.
     *
     * Without this, true north was unavailable on every cold start until a
     * fresh fix arrived — and permanently unavailable to anyone who had denied
     * location, even though the correct value for their city was sitting in
     * the preferences from the last time they granted it. A real review asked
     * for exactly this: "It does not remember its last declination for use."
     *
     * Only the angle is stored, never the position — see
     * [Constants.Prefs.KEY_LAST_DECLINATION].
     */
    private fun restoreRememberedDeclination() {
        val storedAt = prefs.getLong(Constants.Prefs.KEY_LAST_DECLINATION_AT, 0L)
        if (storedAt <= 0L) return

        // Wall-clock, not elapsedRealtime: this has to survive a reboot, which
        // is the whole point, and elapsedRealtime resets there.
        val age = System.currentTimeMillis() - storedAt
        if (age !in 0..Constants.Heading.DECLINATION_MAX_AGE_MS) return

        _declination.value = prefs.getFloat(Constants.Prefs.KEY_LAST_DECLINATION, 0f)
        _hasDeclination.value = true
        Log.i(TAG, "restored declination ${_declination.value} (age ${age}ms)")
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

        // A locked bearing is held in display space, so switching what "0°"
        // means has to move it by the same amount the heading moves. Otherwise
        // toggling the tag mid-walk silently rotates the direction the user is
        // following by the declination — the one error a bearing lock must not
        // make, and a silent one, since both numbers still look plausible.
        if (_hasDeclination.value) shiftLockedBearing(if (next) 1f else -1f)

        val display = computeDisplayAzimuth()
        publishedAzimuth = display
        _azimuth.value = display
        snapArFilter(display)
    }

    /**
     * Locks the current heading as a bearing to follow, or clears the lock.
     *
     * Locks to the *displayed* heading, so a lock taken in true-north mode
     * means the true bearing the user was looking at. Toggling true north
     * afterwards shifts both the heading and the lock by the same declination,
     * which keeps "you are 4° left of it" correct in either mode.
     */
    fun toggleBearingLock() {
        val next = if (_lockedBearing.value != null) null else _azimuth.value
        _lockedBearing.value = next
        prefs.edit()
            .putFloat(Constants.Prefs.KEY_LOCKED_BEARING, next ?: -1f)
            .apply()
        Log.i(TAG, "lockedBearing=$next")
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
            cameraWarmGate.reset()
            horizonGate.reset()
            _isUpright.value = false
            // Otherwise a phone already raised when the switch is turned off
            // leaves these latched true, and the camera stays open for an entry
            // that can no longer happen.
            _arArming.value = false
            _cameraWarm.value = false
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

            // A lock taken before the fix landed is in MAGNETIC space, and this
            // assignment is about to move the displayed heading into TRUE
            // space. Shift it on the same edge, for the same reason
            // toggleTrueNorth does — otherwise the direction the user is
            // following silently rotates by the declination the moment GPS
            // answers, and every "N° LEFT/RIGHT" after that is wrong by exactly
            // that much, with both numbers still looking entirely plausible.
            //
            // The false -> true EDGE only. A refined declination while already
            // in true space needs no shift: the lock is already in that space.
            val enteringTrueSpace = !_hasDeclination.value && _trueNorth.value

            _declination.value = declination
            _hasDeclination.value = true
            if (enteringTrueSpace) shiftLockedBearing(1f)
            Log.i(TAG, "declination acquired: $declination")

            // Remember the angle — and only the angle — so the next cold start
            // has true north before any fix arrives.
            prefs.edit()
                .putFloat(Constants.Prefs.KEY_LAST_DECLINATION, declination)
                .putLong(Constants.Prefs.KEY_LAST_DECLINATION_AT, System.currentTimeMillis())
                .apply()

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

        // Elevation is not cyclic — it lives in [-90, +90] with no seam — so
        // this is a plain two-speed blend and deliberately not the shortest-arc
        // one above. Pointing the arc helper at it would be harmless but
        // misleading; pointing this one at a bearing would be a bug.
        arElevationFiltered = adaptiveLowPass(
            current       = arElevationFiltered,
            target        = data.cameraElevationDeg,
            slowAlpha     = Constants.Ar.ELEVATION_SLOW_ALPHA,
            fastAlpha     = Constants.Ar.ELEVATION_FAST_ALPHA,
            fastThreshold = Constants.Ar.ELEVATION_FAST_THRESHOLD_DEG,
        )
        _arCameraElevation.value = arElevationFiltered  // kept for the gate below

        _screenRoll.value = data.screenRollDeg
        _levelPosture.value = levelGate.update(data.tiltFromFlatDeg)

        publishAccuracy(data.accuracy)

        _isUpright.value =
            if (_arAutoSwitch.value) {
                arGate.update(data.tiltFromFlatDeg, data.screenRollDeg)
            } else {
                false
            }
        // Read after update(), so it reflects this sample rather than the last.
        _arArming.value = _arAutoSwitch.value && arGate.isArming
        _cameraWarm.value = cameraWarmGate.update(_arArming.value)

        // Last, deliberately — see the KDoc.
        _sensorReady.value = true

        // Fed the SMOOTHED elevation: the gate decides when the horizon may
        // move, the filter decides how it gets there.
        _arHorizonElevation.value = horizonGate.update(arElevationFiltered)
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

    /**
     * Moves a locked bearing between magnetic and true space.
     *
     * [direction] is +1 entering true space, -1 leaving it. A bearing lock is
     * held in *display* space, so anything that changes what 0° means has to
     * move the lock by the same amount the heading moves — there are two such
     * doors, [toggleTrueNorth] and a declination arriving mid-session, and both
     * must use this.
     */
    private fun shiftLockedBearing(direction: Float) {
        val locked = _lockedBearing.value ?: return
        val moved = (locked + direction * _declination.value + 360f) % 360f
        _lockedBearing.value = moved
        prefs.edit().putFloat(Constants.Prefs.KEY_LOCKED_BEARING, moved).apply()
    }

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
