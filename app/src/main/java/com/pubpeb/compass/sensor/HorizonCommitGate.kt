package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants
import kotlin.math.abs

/**
 * Decides where the AR horizon rule sits, from a stream of elevation readings.
 *
 * The rule holds still until the device has genuinely been re-aimed: elevation
 * must sit more than [deadZoneDeg] away from the committed value, and stay
 * there for [commitMs], before the horizon moves. Hand tremor and the pitch
 * wobble of a braced, eye-level grip both live well inside the dead zone, so in
 * normal use the rule does not move at all — which is the entire point.
 *
 * ### Why this is a class and not a `LaunchedEffect`
 * Not because the effect form was unfixable — it was fixed in place first, by
 * keying on a boolean instead of the live value. It is a class because the
 * original defect was **invisible without a test**, and a `LaunchedEffect`
 * cannot have one.
 *
 * The defect: the effect was keyed on the elevation itself, so the key changed
 * on every sensor emission, roughly sixteen times a second. The effect
 * restarted and cancelled its own `delay` long before the commit window could
 * elapse, the committed value never moved, and the horizon was frozen solid.
 * The comment above it asserted the opposite. Nothing failed, nothing logged,
 * and it reached a device that way.
 *
 * A fake clock catches it in milliseconds — see the "keeps jittering" case in
 * `HorizonCommitGateTest`, which fails against the keyed-on-a-float version and
 * passes here. That test is the actual reason this is a class.
 */
class HorizonCommitGate(
    private val deadZoneDeg: Float = Constants.Ar.HORIZON_DEAD_ZONE_DEG,
    private val commitMs: Long = Constants.Ar.HORIZON_COMMIT_MS,
    private val nowMs: () -> Long,
) {
    /** The elevation the horizon is currently drawn at. */
    var settledDeg: Float = 0f
        private set

    /** When the reading first left the dead zone; null whenever it is inside. */
    private var departedAt: Long? = null

    private var seeded = false

    /**
     * Feeds one elevation reading in and returns where the horizon should sit.
     *
     * @param elevationDeg signed camera elevation, degrees [-90, +90].
     */
    fun update(elevationDeg: Float): Float {
        // First reading defines the resting position rather than forcing a
        // commit window before anything can be drawn.
        if (!seeded) {
            seeded = true
            settledDeg = elevationDeg
            return settledDeg
        }

        if (abs(elevationDeg - settledDeg) <= deadZoneDeg) {
            // Back inside the band: the displacement was not sustained, so
            // whatever window was running is abandoned.
            departedAt = null
            return settledDeg
        }

        val now = nowMs()
        val since = departedAt ?: now.also { departedAt = it }
        if (now - since >= commitMs) {
            // Commit the NEWEST reading, not the one that tripped the
            // threshold — the device has been moving for the whole window.
            settledDeg = elevationDeg
            departedAt = null
        }
        return settledDeg
    }

    /**
     * Forgets where the horizon was, so the next reading re-seeds it.
     *
     * The seed is whatever sample arrives first, and the sensor flow is shared
     * with `WhileSubscribed` — so after the app has been away long enough for
     * that to lapse, the first sample back is typically the phone flat in a
     * pocket. Without this the horizon would be drawn at that elevation for a
     * full commit window before correcting.
     */
    fun reset() {
        seeded = false
        departedAt = null
        settledDeg = 0f
    }
}
