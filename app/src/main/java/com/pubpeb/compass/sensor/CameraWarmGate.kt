package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants

/**
 * Decides when the camera may be opened ahead of AR engaging.
 *
 * [ArTriggerGate.isArming] goes true on a single sample above the entry
 * threshold and false on a single sample below it, so a hand hovering near that
 * angle flips it at the sensor rate. Wired straight to a camera bind, that
 * opens and closes the hardware repeatedly and strobes the platform's privacy
 * indicator while the user is still looking at the compass.
 *
 * So the raw signal is debounced in both directions, asymmetrically:
 *
 *  - Rising needs [debounceMs] — pre-opening the camera should mean "this lift
 *    is real", not "the hand crossed 65° once".
 *  - Falling gets [releaseMs], which is longer, so an ordinary dip does not
 *    tear down a warm camera a few milliseconds before it is needed.
 */
class CameraWarmGate(
    private val debounceMs: Long = Constants.Ar.WARM_DEBOUNCE_MS,
    private val releaseMs: Long = Constants.Ar.WARM_RELEASE_MS,
    private val nowMs: () -> Long,
) {
    /** True while the camera may be held open ahead of AR. */
    var isWarm: Boolean = false
        private set

    /** When the request first disagreed with [isWarm]; null while they agree. */
    private var disagreedAt: Long? = null

    /**
     * Feeds one reading of the raw request in and returns the debounced state.
     *
     * @param requested [ArTriggerGate.isArming], already ANDed with whether
     *   automatic AR entry is enabled at all.
     */
    fun update(requested: Boolean): Boolean {
        if (requested == isWarm) {
            disagreedAt = null
            return isWarm
        }

        val now = nowMs()
        val since = disagreedAt ?: now.also { disagreedAt = it }
        val required = if (requested) debounceMs else releaseMs
        if (now - since >= required) {
            isWarm = requested
            disagreedAt = null
        }
        return isWarm
    }

    /** Forces the camera cold — used when the user switches auto-entry off. */
    fun reset() {
        isWarm = false
        disagreedAt = null
    }
}
