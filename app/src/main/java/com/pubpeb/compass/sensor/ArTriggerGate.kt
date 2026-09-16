package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants
import kotlin.math.max

/**
 * Decides when the AR camera view should engage, from a stream of tilt readings.
 *
 * Extracted from the ViewModel so it can be tested without Android: the only
 * environmental dependency is [nowMs], which production wires to
 * `SystemClock::elapsedRealtime` and tests replace with a fake counter.
 *
 * ### Why a band and a dwell, not a threshold
 * The original implementation was `|gravity.y| > |gravity.z|`, which is exactly
 * `tan(tilt) > 1` — a hard 45° boundary. 45° is how people hold a phone while
 * walking and glancing, so a single noisy sample either side of it flipped the
 * whole screen into a camera view and back. Entry now needs
 * [Constants.Ar.ENTER_DEG] sustained for a dwell that scales with how
 * decisive the lift was; exit is immediate below [Constants.Ar.EXIT_DEG],
 * because someone lowering the phone wants the compass back without waiting.
 *
 * The gap between the two thresholds is the hysteresis band: once engaged, the
 * view survives down to 50°, so a hand drifting between 50° and 65° holds
 * steady instead of oscillating.
 */
class ArTriggerGate(
    private val enterDeg: Float = Constants.Ar.ENTER_DEG,
    private val exitDeg: Float = Constants.Ar.EXIT_DEG,
    private val dwellMs: Long = Constants.Ar.DWELL_MS,
    private val dwellMinMs: Long = Constants.Ar.DWELL_MIN_MS,
    private val decisiveDeg: Float = Constants.Ar.DECISIVE_DEG,
    private val portraitRollDeg: Float = Constants.Ar.PORTRAIT_ROLL_DEG,
    private val portraitRollExitDeg: Float = Constants.Ar.PORTRAIT_ROLL_EXIT_DEG,
    private val nowMs: () -> Long,
) {
    var isUpright: Boolean = false
        private set

    /** When tilt first rose above [enterDeg]; null whenever it has fallen back. */
    private var uprightSince: Long? = null

    /**
     * Highest tilt seen since the dwell started. Reset with the dwell.
     *
     * Peak rather than current, because the dwell should be charged against how
     * decisive the *gesture* was, not where the hand happens to be at the
     * instant the timer expires — a fast raise to eye level that settles back
     * to 70° is still a fast raise to eye level.
     */
    private var peakTiltDeg = 0f

    /**
     * True while the dwell is running — tilt is past [enterDeg] but has not yet
     * held there for [dwellMs].
     *
     * Exposed so the UI can spend the dwell doing the work AR entry would
     * otherwise do after it. Getting the CameraX provider takes a few hundred
     * milliseconds and used to run *after* the gate opened, stacking onto the
     * 800 ms wait; started here it is finished before the gate opens and the
     * transition is that much shorter.
     *
     * This is a *warning*, not a decision: it goes true on a single sample
     * above [enterDeg] and false on a single sample below, so it flips at the
     * sensor rate for a hand hovering near the threshold. **Anything it drives
     * must be debounced.**
     *
     * It does now gate opening the camera, which an earlier version of this
     * doc forbade. That changed deliberately once the entry cost was measured:
     * the delay is the sensor's first frame (~411 ms), and nothing but an
     * actual bind starts that clock. The privacy-indicator cost is real and
     * accepted — see the note at the use site in `CompassScreen`, which also
     * carries the debounce that makes this signal safe to act on.
     */
    val isArming: Boolean
        get() = !isUpright && uprightSince != null

    /**
     * Feeds one tilt reading in and returns the resulting state.
     *
     * @param tiltDeg angle from flat, [0, 90] — see
     *   [SensorRepository.tiltFromFlat].
     */
    fun update(tiltDeg: Float, screenRollDeg: Float): Boolean {
        // Rolled toward landscape is not a request for the camera, however
        // steeply the phone is tilted — see Constants.Ar.PORTRAIT_ROLL_DEG.
        // Checked before anything else so a device rolled while already engaged
        // drops straight back out rather than holding the camera open.
        // Hysteresis on roll too: engaged, the view survives out to the wider
        // exit band; not engaged, arming needs the tighter one. A bare edge
        // here chattered on any hand held near 45°, and because the reset below
        // also cleared the dwell and the peak, a steady lift at that roll could
        // never accumulate enough of either to engage at all.
        // Only meaningful once the device is off flat. screenRoll is derived
        // from the x/y components of gravity, which are both near zero when the
        // phone lies flat — so the angle there is pure noise, and testing it
        // during a raise cleared the dwell and the earned peak at random.
        val rollIsMeaningful = tiltDeg > exitDeg
        val rollLimit = if (isUpright) portraitRollExitDeg else portraitRollDeg
        if (rollIsMeaningful && rollFromUpright(screenRollDeg) > rollLimit) {
            isUpright = false
            uprightSince = null
            peakTiltDeg = 0f
            return false
        }

        if (isUpright) {
            if (tiltDeg < exitDeg) {
                isUpright = false
                uprightSince = null
                peakTiltDeg = 0f
            }
            return isUpright
        }

        if (tiltDeg <= enterDeg) {
            // Dipping back below the entry threshold restarts the dwell, so a
            // brief wobble past it never accumulates toward engagement.
            uprightSince = null
            peakTiltDeg = 0f
            return false
        }

        val now = nowMs()
        val since = uprightSince ?: now.also { uprightSince = it }
        peakTiltDeg = max(peakTiltDeg, tiltDeg)
        if (now - since >= dwellFor(peakTiltDeg)) isUpright = true
        return isUpright
    }

    /**
     * How long this lift has to be held, given how far past [enterDeg] it went.
     *
     * [dwellMs] at the threshold, falling linearly to [dwellMinMs] at
     * [decisiveDeg] and staying there beyond it. The anti-flicker property is
     * preserved where it is actually needed — a hand hovering at 65-66° still
     * pays the full dwell — while a deliberate raise to eye level gets in
     * roughly three times faster, which is what closes the gap between entry
     * and exit that made the mode feel sticky on the way in.
     */
    fun dwellFor(tiltDeg: Float): Long {
        val span = decisiveDeg - enterDeg
        if (span <= 0f) return dwellMinMs
        val t = ((tiltDeg - enterDeg) / span).coerceIn(0f, 1f)
        return (dwellMs - (dwellMs - dwellMinMs) * t).toLong()
    }

    /**
     * How far the device is rolled from upright, in degrees [0, 90].
     *
     * Folds onto a half turn so 0° and 180° both read as 0: someone holding
     * the phone upside down is still holding it portrait.
     */
    private fun rollFromUpright(screenRollDeg: Float): Float {
        val wrapped = ((screenRollDeg % 180f) + 180f) % 180f      // [0, 180)
        return if (wrapped > 90f) 180f - wrapped else wrapped
    }

    /** Forces the gate closed — used when the user switches auto-entry off. */
    fun reset() {
        isUpright = false
        uprightSince = null
        peakTiltDeg = 0f
    }
}
