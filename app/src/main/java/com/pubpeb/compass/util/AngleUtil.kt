package com.pubpeb.compass.util

import com.pubpeb.compass.Constants
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shortest signed arc from [from] to [to], always in [−180°, +180°).
 *
 * A bearing is cyclic, so plain subtraction is wrong across the 0°/360° seam:
 * 359° → 0° naively reads as −359°, which would spin a compass card almost a
 * full turn backwards instead of nudging it +1°.
 *
 * Adding 540 before the modulo keeps the operand positive for every reachable
 * input (the raw difference is in (−360, 360), so the sum is in (180, 900)),
 * which matters because Kotlin's `%` returns a negative result for a negative
 * left operand.
 *
 * Verification:
 * ```
 * 359° → 0°:  ((0 - 359 + 540) % 360) - 180 = (181 % 360) - 180 = +1°  ✓
 * 0° → 359°:  ((359 - 0 + 540) % 360) - 180 = (899 % 360) - 180 = -1°  ✓
 * 180° → 0°:  ((0 - 180 + 540) % 360) - 180 = (0 % 360) - 180  = -180° ✓
 * ```
 *
 * Note the closed lower bound: an exactly-opposite pair always returns −180,
 * never +180, so the range is [−180, +180) rather than the (−180, +180] the
 * original comment claimed. The two are the same bearing, so nothing downstream
 * cares — but the contract should say what the code does.
 *
 * This was previously copy-pasted at five call sites across three files, with
 * this explanation beside only one of them.
 */
fun shortestArcDelta(to: Float, from: Float): Float =
    ((to - from + 540f) % 360f) - 180f

/**
 * Rounds a bearing for display, folding 360° back to 0°.
 *
 * Without the modulo, 359.7° renders as "360°", which no compass should show.
 */
fun normalizedHeading(azimuth: Float): Int = azimuth.roundToInt() % 360

/**
 * Whether a new bearing has moved far enough from the last published one to be
 * worth showing.
 *
 * Measured on the shortest arc, so the deadband still works either side of due
 * north — a naive `abs(new - published)` reads 359.8° → 0.1° as a 359.7° jump
 * and publishes when it should suppress.
 */
fun shouldPublishHeading(
    new: Float,
    published: Float,
    deadbandDeg: Float = Constants.Heading.DEADBAND_DEG,
): Boolean = abs(shortestArcDelta(new, published)) >= deadbandDeg
