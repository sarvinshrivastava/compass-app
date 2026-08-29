package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants

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
 * [Constants.Ar.ENTER_DEG] sustained for [Constants.Ar.DWELL_MS]; exit is
 * immediate below [Constants.Ar.EXIT_DEG], because someone lowering the phone
 * wants the compass back without waiting.
 *
 * The gap between the two thresholds is the hysteresis band: once engaged, the
 * view survives down to 50°, so a hand drifting between 50° and 65° holds
 * steady instead of oscillating.
 */
class ArTriggerGate(
    private val enterDeg: Float = Constants.Ar.ENTER_DEG,
    private val exitDeg: Float = Constants.Ar.EXIT_DEG,
    private val dwellMs: Long = Constants.Ar.DWELL_MS,
    private val nowMs: () -> Long,
) {
    var isUpright: Boolean = false
        private set

    /** When tilt first rose above [enterDeg]; null whenever it has fallen back. */
    private var uprightSince: Long? = null

    /**
     * Feeds one tilt reading in and returns the resulting state.
     *
     * @param tiltDeg angle from flat, [0, 90] — see
     *   [SensorRepository.tiltFromFlat].
     */
    fun update(tiltDeg: Float): Boolean {
        if (isUpright) {
            if (tiltDeg < exitDeg) {
                isUpright = false
                uprightSince = null
            }
            return isUpright
        }

        if (tiltDeg <= enterDeg) {
            // Dipping back below the entry threshold restarts the dwell, so a
            // brief wobble past it never accumulates toward engagement.
            uprightSince = null
            return false
        }

        val now = nowMs()
        val since = uprightSince ?: now.also { uprightSince = it }
        if (now - since >= dwellMs) isUpright = true
        return isUpright
    }

    /** Forces the gate closed — used when the user switches auto-entry off. */
    fun reset() {
        isUpright = false
        uprightSince = null
    }
}
