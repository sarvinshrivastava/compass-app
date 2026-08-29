package com.pubpeb.compass.telemetry

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pubpeb.compass.sensor.CompassAccuracy

/**
 * The app's only contact with Firebase.
 *
 * Everything else calls these named methods, so the SDK types never leak into
 * the ViewModel or the UI — swapping vendors, or ripping telemetry out entirely,
 * is a one-file change.
 *
 * ### What is deliberately not measured
 * No screen views, no button taps, no retention or session funnels, no user IDs.
 * This is a compass: someone opens it, reads a bearing and leaves, and
 * instrumenting for engagement would create pressure to make that loop longer.
 * Every event below exists to answer one question about **what to delete or
 * keep**, and each is a low-cardinality counter.
 */
object Telemetry {

    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    /** Elapsed-realtime millis when the current foreground session began. */
    private var sessionStartedAt = 0L

    /** Seconds from session start to the first trustworthy heading; null until then. */
    private var secondsToStableHeading: Int? = null

    /** Set once per session, so the accuracy sample is a distribution not a stream. */
    private var accuracySampled = false

    /** Guards the one-per-session events against a rotation replaying them. */
    private var arReported = false
    private var levelReported = false

    /** Guards against re-registering the lifecycle observer. See [init]. */
    private var initialized = false

    /**
     * Wires up Firebase and the session observer. **Idempotent, and it must be.**
     *
     * This is called from `MainActivity.onCreate`, which runs again on every
     * configuration change because the Activity declares no `configChanges`.
     * Without this guard each rotation added another anonymous observer that
     * nothing ever removed — and worse, `LifecycleRegistry.addObserver`
     * synchronously replays missed events to bring a new observer to the
     * current state, so registering while the process was already STARTED fired
     * `onStart()` immediately and reset every once-per-session guard below.
     * That reintroduced, by a different route, the exact rotation-inflates-
     * analytics defect that moving to ProcessLifecycleOwner was meant to fix.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        analytics = FirebaseAnalytics.getInstance(context)
        crashlytics = FirebaseCrashlytics.getInstance()

        // Session boundaries come from the PROCESS lifecycle, not an Activity's.
        // An Activity restarts on every rotation, which would reset the
        // once-per-session guards below and emit a fragment-length session —
        // one user rotating twice would read as three visits and drag the
        // median session length down. ProcessLifecycleOwner does not fire on a
        // configuration change, so a session is a genuine foreground period.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = onSessionStart()
                override fun onStop(owner: LifecycleOwner) = onSessionEnd()
            }
        )
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private fun onSessionStart() {
        sessionStartedAt = SystemClock.elapsedRealtime()
        secondsToStableHeading = null
        accuracySampled = false
        arReported = false
        levelReported = false
    }

    /**
     * Records how long it took to reach a heading worth acting on. Called
     * repeatedly; only the first call in a session is kept.
     */
    fun onHeadingBecameStable() {
        if (secondsToStableHeading != null || sessionStartedAt == 0L) return
        secondsToStableHeading =
            ((SystemClock.elapsedRealtime() - sessionStartedAt) / 1000L).toInt()
    }

    /**
     * Validates the product's core claim: open, read a bearing, leave.
     *
     * If the median session runs to minutes rather than seconds, the thesis the
     * whole design rests on is wrong and the interface should be reconsidered.
     */
    private fun onSessionEnd() {
        if (sessionStartedAt == 0L) return
        val total = ((SystemClock.elapsedRealtime() - sessionStartedAt) / 1000L).toInt()
        log(EVENT_SESSION) {
            putInt(PARAM_TOTAL_SEC, total)
            secondsToStableHeading?.let { putInt(PARAM_SEC_TO_STABLE, it) }
        }
        sessionStartedAt = 0L
    }

    // ── Feature usage — each answers "does this earn its place?" ──────────────

    /**
     * Sampled once per session rather than per reading, so the result is the
     * share of *sessions* that saw a given confidence — the actual question —
     * rather than a count dominated by whoever left the app open longest.
     *
     * This is the most valuable number here: it is the only way to learn what
     * fraction of real users in the field are being shown a heading that cannot
     * be trusted.
     */
    fun sampleAccuracy(accuracy: CompassAccuracy) {
        if (accuracySampled) return
        accuracySampled = true
        log(EVENT_ACCURACY) { putString(PARAM_STATE, accuracy.name) }
    }

    /** Was the auto-trigger wanted, or does everyone reach for the button? */
    fun onArOpened(auto: Boolean) {
        if (arReported) return
        arReported = true
        log(EVENT_AR_OPENED) { putString(PARAM_TRIGGER, if (auto) "auto" else "manual") }
    }

    /** The review questioned whether the level belongs at all. This settles it. */
    fun onLevelOpened() {
        if (levelReported) return
        levelReported = true
        log(EVENT_LEVEL_OPENED)
    }

    /** Justifies carrying a third palette. */
    fun onDaylightToggled(enabled: Boolean) =
        log(EVENT_DAYLIGHT) { putBoolean(PARAM_ENABLED, enabled) }

    /** Did asking for COARSE rather than FINE actually pay off in grant rate? */
    fun onLocationOutcome(granted: Boolean) =
        log(EVENT_LOCATION) { putString(PARAM_OUTCOME, if (granted) "granted" else "denied") }

    /**
     * Whether a granted permission actually yielded a true-north correction.
     *
     * Separates "the fix never arrived" from "the magnetometer is off" — both
     * reach the user as "the heading is wrong", and without this they are
     * indistinguishable in the data.
     */
    fun onDeclinationOutcome(acquired: Boolean) =
        log(EVENT_DECLINATION) { putBoolean(PARAM_ACQUIRED, acquired) }

    // ── Crashlytics ───────────────────────────────────────────────────────────

    /**
     * Attaches the live sensor confidence to every crash report.
     *
     * A compass crash is far more interpretable when you can see whether the
     * magnetometer was healthy, uncalibrated, or silent at the moment it died.
     */
    fun setAccuracyKey(accuracy: CompassAccuracy) {
        crashlytics?.setCustomKey(KEY_ACCURACY, accuracy.name)
    }

    /**
     * Reports the sensor going silent as a non-fatal.
     *
     * `getRotationMatrix` returning false stops the flow with no exception and
     * no crash — the screen simply freezes on a stale heading. Without this it
     * is invisible: no user reports "my compass quietly stopped updating".
     */
    fun reportSensorStall() {
        crashlytics?.recordException(SensorStalledException())
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private inline fun log(name: String, params: Bundle.() -> Unit = {}) {
        analytics?.logEvent(name, Bundle().apply(params))
    }

    /** Carries a stable name and message so Crashlytics groups these together. */
    private class SensorStalledException :
        Exception("Sensor flow produced no reading for 3s (getRotationMatrix likely failing)")

    private const val EVENT_SESSION = "compass_session"
    private const val EVENT_ACCURACY = "accuracy_state"
    private const val EVENT_AR_OPENED = "ar_opened"
    private const val EVENT_LEVEL_OPENED = "level_opened"
    private const val EVENT_DAYLIGHT = "daylight_toggled"
    private const val EVENT_LOCATION = "location_outcome"
    private const val EVENT_DECLINATION = "declination_outcome"

    private const val PARAM_TOTAL_SEC = "total_sec"
    private const val PARAM_SEC_TO_STABLE = "sec_to_stable"
    private const val PARAM_STATE = "state"
    private const val PARAM_TRIGGER = "trigger"
    private const val PARAM_ENABLED = "enabled"
    private const val PARAM_OUTCOME = "outcome"
    private const val PARAM_ACQUIRED = "acquired"

    private const val KEY_ACCURACY = "compass_accuracy"
}
