package com.pubpeb.compass.util

import kotlin.math.abs

/**
 * Adaptive low-pass filter for a non-cyclic signal.
 *
 * A single fixed alpha cannot serve a signal that must be both *still* when the
 * user is still and *prompt* when the user moves. Heavy smoothing kills the
 * tremor but makes the value lag visibly behind the hand; light smoothing
 * tracks the hand but lets the tremor through.
 *
 * So the alpha is chosen per sample from the size of the step being asked for.
 * A step at or beyond [fastThreshold] is the user actually moving the device
 * and gets [fastAlpha]; a step of nothing is noise and gets [slowAlpha];
 * in between, the alpha ramps **quadratically** between the two:
 *
 * ```
 * t     = min(|step| / fastThreshold, 1)
 * alpha = slowAlpha + (fastAlpha - slowAlpha) * t²
 * ```
 *
 * The ramp is the whole design, and it was not the first attempt. Switching
 * hard between two speeds at the threshold looks equivalent and is not: once a
 * large move brings the residual *under* the threshold, a hard switch drops
 * straight to [slowAlpha] and the last couple of degrees then close at
 * tremor-rejection speed. On the AR horizon that read as the labels arriving
 * about two degrees low after a quick tilt and sagging into place over the
 * following second. The ramp has no such cliff — the closing speed decays with
 * the residual instead of falling off it — and `SmoothingUtilTest` pins both
 * halves of the behaviour.
 *
 * Squared rather than linear so that the small end stays genuinely slow:
 * tremor of ±0.5° against a 2.5° threshold lands at t² = 0.04, which is barely
 * off [slowAlpha], where a linear ramp would already have tripled it.
 *
 * **Non-cyclic only.** There is no shortest-arc handling here, so this must
 * never be pointed at a bearing — see [shortestArcDelta] for why a naive
 * blend of 1° and 359° gives 180°. Elevation, pitch and roll are all in
 * [-90, +90] and have no seam, which is what makes the plain subtraction safe.
 *
 * @param current the previous filter output
 * @param target  the newest raw reading
 * @return the new filter output
 */
fun adaptiveLowPass(
    current: Float,
    target: Float,
    slowAlpha: Float,
    fastAlpha: Float,
    fastThreshold: Float,
): Float {
    val delta = target - current
    val t = (abs(delta) / fastThreshold).coerceIn(0f, 1f)
    val alpha = slowAlpha + (fastAlpha - slowAlpha) * t * t
    return current + alpha * delta
}
