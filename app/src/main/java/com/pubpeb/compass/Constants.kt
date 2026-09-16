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

        /**
         * How long a remembered declination stays usable, in milliseconds.
         *
         * Declination drifts by roughly a degree over a few hundred kilometres
         * and by a fraction of a degree per year, so a value from last week is
         * still right for anyone who has not crossed a continent. Thirty days
         * keeps it useful for the person who opens the app in the same city
         * every weekend while bounding how wrong it can be for the person who
         * flew somewhere — and a live fix, whenever one arrives, overwrites it
         * before the user sees the stale one for more than a moment.
         */
        const val DECLINATION_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * Within this many degrees of the locked bearing, the user is on it.
         *
         * Wide enough to be reachable while walking with the phone in one
         * hand — the heading itself wanders by more than a degree at every
         * step — and narrow enough that "on course" means something over a
         * kilometre.
         */
        const val LOCK_ON_COURSE_DEG = 3f
    }

    /**
     * AR auto-entry gate.
     *
     * The [ENTER_DEG] / [EXIT_DEG] gap is a hysteresis band and the whole point
     * of the design: a single 45° threshold fired at the angle people naturally
     * hold a phone, so the camera flickered on and off continuously.
     *
     * ### These were relaxed once, deliberately
     * Entry was 65° with an 800 ms dwell. Those numbers were set when the dwell
     * was the *only* thing standing between a noisy tilt reading and an
     * unwanted camera. Three independent defences have since been added — the
     * device must be held near portrait, AR is scoped to the compass page, and
     * auto-entry needs a fresh lift rather than an inherited posture — so the
     * dwell no longer has to be conservative on its own, and 65° was asking for
     * a deliberately steep raise that the other guards already imply.
     *
     * **The floor is 45°**, not a matter of taste: that is the angle people
     * hold a phone at while walking and glancing, and anything at or below it
     * fires continuously. `ArTriggerGateTest` pins that with an oscillation
     * case; entry must stay comfortably above it.
     */
    object Ar {
        /** Tilt from flat above which AR may engage. */
        const val ENTER_DEG = 58f

        /**
         * How far the device may be rolled from upright-portrait and still
         * count as portrait for AR.
         *
         * Tilt alone is not intent. Lying on your side in bed holding the phone
         * rolled toward landscape produces the same tilt-from-flat as raising
         * it to sight a horizon, and opening the camera there is startling and
         * useless — you are not looking at a scene, you are looking at a phone.
         *
         * Measured on the folded roll, so upside-down portrait still counts;
         * 45° is the natural midpoint between portrait and landscape.
         */
        const val PORTRAIT_ROLL_DEG = 45f

        /**
         * Roll past which AR *disengages*, once engaged.
         *
         * The 10° gap is a hysteresis band, for the same reason
         * [ENTER_DEG]/[EXIT_DEG] have one: a bare edge chatters for any hand
         * held near it. Without this, a phone rolled around 45° flipped the
         * portrait test on every sample, and because leaving the band also
         * cleared the dwell and the peak, the user could hold a steady lift
         * indefinitely and never accumulate enough of either to engage.
         */
        const val PORTRAIT_ROLL_EXIT_DEG = 55f

        /** Tilt below which AR disengages. The 13° gap is the hysteresis band. */
        const val EXIT_DEG = 45f

        /**
         * How long tilt must stay above [ENTER_DEG] before AR engages, when the
         * lift is only just past the threshold.
         *
         * This is the *maximum* dwell — see [DWELL_MIN_MS] and [DECISIVE_DEG].
         */
        const val DWELL_MS = 450L

        /**
         * Dwell for an unmistakably deliberate lift.
         *
         * The dwell exists to reject a hand hovering near [ENTER_DEG], not to
         * make everyone wait. Someone who raises the phone to eye level to look
         * at a horizon has already declared their intent by how far they moved
         * it, and charging them the full dwell is what made entry feel a
         * second slower than exit.
         */
        const val DWELL_MIN_MS = 200L

        /**
         * Tilt at or beyond which a lift counts as fully decisive, so the dwell
         * has shortened all the way to [DWELL_MIN_MS]. Between [ENTER_DEG] and
         * here the dwell scales linearly.
         *
         * Measured against the *peak* tilt seen during the dwell, not the
         * current one: a quick decisive raise that settles back to 70° is still
         * a decisive raise.
         */
        const val DECISIVE_DEG = 75f

        /**
         * How long a lift must persist before the camera is pre-opened, and how
         * long the warm survives a dip below the threshold.
         *
         * [ArTriggerGate.isArming] flips on a single sample either side of
         * [ENTER_DEG], so a hand hovering there toggles it at the sensor rate.
         * Wired straight to a camera bind that strobes the hardware and the
         * platform's privacy indicator. The debounce makes pre-opening mean
         * "this lift is real", and the release delay stops ordinary noise from
         * tearing a warm camera down a few milliseconds before it is needed.
         */
        const val WARM_DEBOUNCE_MS = 180L
        const val WARM_RELEASE_MS = 400L

        /** Second-stage filter for AR label positions — very smooth, ~400 ms lag. */
        const val SMOOTHING_ALPHA = 0.05f

        /**
         * Second-stage filter for the AR *horizon*, i.e. label Y.
         *
         * Elevation used to be passed to the overlay raw while azimuth got
         * [SMOOTHING_ALPHA], so the labels were smooth horizontally and
         * jittery vertically — the same hand tremor, filtered on one axis and
         * not the other. Wrist flexion also produces more angular noise in
         * pitch than in yaw, so the unfiltered axis was the noisier one.
         *
         * Two speeds rather than one, because the horizon has a constraint
         * azimuth does not: it is matched against the camera image behind it.
         * A flat [SMOOTHING_ALPHA] here would hold the labels still but let
         * them visibly swim behind the scene on a deliberate tilt. See
         * [com.pubpeb.compass.util.adaptiveLowPass].
         */
        const val ELEVATION_SLOW_ALPHA = 0.04f

        /** Alpha once the step reaches [ELEVATION_FAST_THRESHOLD_DEG]. */
        const val ELEVATION_FAST_ALPHA = 0.35f

        /**
         * Step size at which an elevation change counts as fully deliberate.
         *
         * Raised from 2.5° after testing on a real phone: with the quadratic
         * ramp, a 1° tremor against a 2.5° threshold still resolved to
         * alpha 0.090, nearly twice azimuth's flat 0.05, and the labels
         * visibly shimmered while the horizontal position sat still. At 6°
         * the same tremor lands on 0.049 — parity with azimuth — while a real
         * tilt still crosses into the fast alpha within a few degrees.
         */
        const val ELEVATION_FAST_THRESHOLD_DEG = 6.0f

        /** Half-FOV assumed before the camera reports its real optics. */
        const val DEFAULT_FOV_HALF_DEG = 30f

        /** Labels sit slightly above centre so they clear the heading HUD. */
        const val HORIZON_BIAS_FRACTION = -0.12f

        /**
         * How far elevation must move before the horizon commits to a new
         * position, and how long that displacement must hold.
         *
         * The rule sits over a live camera, so it cannot simply be pinned: held
         * level it lands on the real skyline, and a fixed line would then
         * visibly disagree with the actual horizon the moment the phone tilts —
         * a falsehood drawn on top of a photo-real scene, invisible when level
         * and glaring when not. Pinning it would also have to pin the chips,
         * since they share its Y; that is the all-labels-pinned design this app
         * already tried and reverted.
         *
         * A dead zone gets the same stillness honestly. Hand tremor and the
         * pitch wobble of a braced, eye-level grip live well inside 9°, so in
         * normal use the rule does not move at all; a deliberate re-aim at a
         * ridge or a valley floor does clear it, and the line follows. Same
         * hysteresis-plus-dwell idiom as [ArTriggerGate], applied to the same
         * class of problem: a noisy signal around the value people actually
         * hold.
         */
        const val HORIZON_DEAD_ZONE_DEG = 9f
        const val HORIZON_COMMIT_MS = 250L

        /**
         * Hard limit on how far the horizon may travel from centre, as a
         * fraction of half the viewport height.
         *
         * Was 1.5, which let the rule and every chip leave the screen entirely
         * on a firm tilt. At 0.35 they stay in a comfortable middle band, which
         * removes most of the perceived "flying around" on its own.
         */
        const val HORIZON_TRAVEL_CLAMP = 0.35f

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

        /**
         * Posture band for switching the level's instrument.
         *
         * A bullseye reads two axes at once and is the right instrument for a
         * phone lying on a surface. Held up against a door frame it is the
         * wrong one entirely: `pitch` is pinned near 90°, `normY` clamps to 1,
         * and the bubble sits dead against the rim saying nothing. That posture
         * wants a single-axis reading of rotation about the viewing axis.
         *
         * Same hysteresis discipline as [Ar]: enter above [UPRIGHT_ENTER_DEG],
         * fall back below [UPRIGHT_EXIT_DEG]. Without the gap a phone propped
         * at the boundary swaps instrument on sensor noise.
         */
        const val UPRIGHT_ENTER_DEG = 60f
        const val UPRIGHT_EXIT_DEG = 45f

        /**
         * Rotation from plumb producing full deflection of the upright level's
         * bar. Tighter than [MAX_TILT_DEG] because the question in this posture
         * ("is this frame straight?") is asked in single degrees, not in
         * "roughly which way do I tip it".
         */
        const val PLUMB_MAX_DEG = 20f

        /** Rotation from plumb within which the upright level reads as true. */
        const val PLUMB_LEVEL_DEG = 0.4f

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
        /** Crossfade between the flat compass and the AR overlay. */
        const val MODE_TRANSITION_MS = 300

        /**
         * How long the lock marker and its readout take to fade out when a
         * bearing is cleared.
         *
         * Not instant, deliberately. Setting a lock costs nothing if it was an
         * accident — you set it again. *Clearing* one destroys a bearing the
         * user may be part-way through following, and an element that vanishes
         * between two frames is invisible to anyone not looking straight at it.
         * A fade reads as "something just happened" in peripheral vision, which
         * is the only warning this app's interaction vocabulary allows: a
         * confirmation dialog is ruled out by the readout's own no-dialogs
         * philosophy, and a long-press-to-clear would invent a gesture used
         * nowhere else here.
         */
        const val LOCK_FADE_MS = 450

        /** Fade for the pager dots when AR takes over. */
        const val CHROME_FADE_MS = 200

        /** Target ring / reference line filling on arrival at level. */
        const val INSTRUMENT_FILL_MS = 180

        /** Pager dot tinting as the active page changes. */
        const val PAGER_DOT_MS = 250

        /** One full pass of the calibration figure-eight. */
        const val FIGURE_EIGHT_MS = 2_600

        /**
         * How long the heading must stay untrustworthy before the calibration
         * panel offers itself.
         *
         * [com.pubpeb.compass.sensor.CompassAccuracy] starts at `UNRELIABLE` by
         * design — claiming confidence the platform has not granted would be
         * worse — and normally resolves within a second or two as the
         * magnetometer warms up. Prompting on that opening state would put a
         * calibration dialog in front of every cold start, which teaches the
         * user to dismiss it without reading it.
         */
        const val CALIBRATION_PROMPT_DELAY_MS = 4_000L

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

        /**
         * Last declination this device resolved, in degrees, and when.
         *
         * **The position it came from is deliberately not stored.** Only the
         * derived correction is — the same rule [DeclinationRepository] follows
         * on the way in, and the one the privacy policy and the Data Safety
         * form are both written against. A cached latitude and longitude would
         * be a location history in the preferences file.
         *
         * Reused on the next launch so true north survives a cold start
         * instead of waiting on a fresh fix, or on a permission the user may no
         * longer be able to grant.
         */
        const val KEY_LAST_DECLINATION = "last_declination_deg"
        const val KEY_LAST_DECLINATION_AT = "last_declination_at"

        /** Bearing the user has locked, in degrees, or -1 for none. */
        const val KEY_LOCKED_BEARING = "locked_bearing_deg"

        /**
         * Prefix for the "this permission has been requested at least once"
         * markers. The full permission name is appended, so one key per
         * permission. See [com.pubpeb.compass.util.PermissionAskLog].
         */
        const val KEY_ASKED_PREFIX = "permission_asked_"
    }

}
