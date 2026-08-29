package com.pubpeb.compass

import androidx.compose.ui.unit.dp

/**
 * Every tunable value in the app, in one place.
 *
 * The rule: if a number carries meaning — a threshold, a duration, a filter
 * coefficient, a proportion — it belongs here, named. Numbers that are purely
 * local layout arithmetic (a `/ 2f` to find a centre, a `+ 540f` inside a
 * documented formula) stay where they are; hoisting those would obscure the
 * maths rather than clarify it.
 *
 * Grouped by the subsystem that owns them, so a change to AR behaviour is one
 * object to read rather than four files to grep.
 */
object Constants {

    /** Sensor acquisition and fusion. */
    object Sensor {
        /** Low-pass filter alpha on raw accelerometer/magnetometer arrays. */
        const val LPF_ALPHA = 0.15f

        /**
         * Below this gravity magnitude the device is in freefall (or the
         * sensor is lying) and the tilt angle is undefined — guard the divide.
         */
        const val MIN_GRAVITY_MAGNITUDE = 1e-3f

        /** Watchdog poll interval for detecting a stalled sensor flow. */
        const val WATCHDOG_POLL_MS = 1_000L

        /** Silence longer than this is reported as [com.pubpeb.compass.sensor.CompassAccuracy.NO_SIGNAL]. */
        const val STALE_AFTER_MS = 3_000L
    }

    /** Heading presentation. */
    object Heading {
        /**
         * The readout only moves once the bearing has changed by this much.
         * Without it a phone sitting still flickers ±1–2° at 52 sp.
         */
        const val DEADBAND_DEG = 0.5f
    }

    /**
     * AR auto-entry gate.
     *
     * The [ENTER_DEG] / [EXIT_DEG] gap is a hysteresis band and the whole point
     * of the design: a single 45° threshold fired at the angle people naturally
     * hold a phone, so the camera flickered on and off continuously.
     */
    object Ar {
        /** Tilt from flat above which AR may engage. */
        const val ENTER_DEG = 65f

        /** Tilt below which AR disengages. The 15° gap is the hysteresis band. */
        const val EXIT_DEG = 50f

        /** How long tilt must stay above [ENTER_DEG] before AR engages. */
        const val DWELL_MS = 800L

        /** Second-stage filter for AR label positions — very smooth, ~400 ms lag. */
        const val SMOOTHING_ALPHA = 0.05f

        /** Half-FOV assumed before the camera reports its real optics. */
        const val DEFAULT_FOV_HALF_DEG = 30f

        /** Labels sit slightly above centre so they clear the heading HUD. */
        const val HORIZON_BIAS_FRACTION = -0.12f

        /** Sanity clamp on the vertical half-FOV derived from aspect ratio. */
        const val V_FOV_HALF_MIN_DEG = 5f
        const val V_FOV_HALF_MAX_DEG = 80f

        /**
         * Labels beyond this multiple of half the screen width are skipped, so
         * they slide in from the edge rather than popping into existence.
         */
        const val CULL_MARGIN = 1.3f
    }

    /** Compass rose geometry, as fractions of the canvas half-size. */
    object Rose {
        /**
         * Leaves headroom outside the tick ring for the lubber index. At the
         * old 0.92 the index was clipped by the canvas edge.
         */
        const val RADIUS_FRACTION = 0.86f
    }

    /** Bubble level. */
    object Level {
        /** Tilt producing full deflection of the bubble to the rim. */
        const val MAX_TILT_DEG = 45f

        /** Below this normalised magnitude the device counts as level. */
        const val LEVEL_THRESHOLD = 0.15f

        /** Above this the tilt is severe enough to colour the dot red. */
        const val CRITICAL_THRESHOLD = 0.70f

        /** Target ring fills to this alpha on arrival at level. */
        const val RING_FILL_ALPHA = 0.85f

        const val HALO_ALPHA = 0.30f
        const val FILL_ALPHA = 0.80f
    }

    /** Shared UI timings and layout. */
    object Ui {
        /** Page count of the root pager: compass, level. */
        const val PAGE_COUNT = 2

        /** Crossfade between the flat compass and the AR overlay. */
        const val MODE_TRANSITION_MS = 300

        /** Fade for the pager dots when AR takes over. */
        const val CHROME_FADE_MS = 200

        /** The rose never grows past this, so it cannot dominate or overflow. */
        val ROSE_MAX_WIDTH = 320.dp
    }

    /** Home-screen tile. */
    object Widget {
        /** Below this width there is no room for the wordmark beside the mark. */
        val WORDMARK_MIN_WIDTH = 140.dp

        /**
         * The app's one fixed brand colour, mirrored by `@color/brand_red` for
         * the picker preview layout. A compass needle that is not red stops
         * reading as a compass needle, so this one value does not follow the
         * launcher's theme.
         */
        const val BRAND_RED = 0xFFE63946L
    }

    /** Persistence. */
    object Prefs {
        const val NAME = "compass_prefs"
        const val KEY_TRUE_NORTH = "true_north"
        const val KEY_DAYLIGHT = "daylight_mode"

        /** Whether AR engages on its own when the phone is raised. */
        const val KEY_AR_AUTO = "ar_auto_switch"

        /** One-shot marker for the removal of the old widget's DataStore file. */
        const val KEY_LEGACY_STORE_PURGED = "legacy_widget_store_purged"
    }

    /**
     * Left behind on any device that ran the widget-with-live-data build.
     * Nothing reads it; it is deleted once on first launch of this version.
     */
    object Legacy {
        const val WIDGET_DATASTORE_FILE = "datastore/compass_widget_prefs.preferences_pb"
    }
}
