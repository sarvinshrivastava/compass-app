package com.pubpeb.compass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pubpeb.compass.Constants
import com.pubpeb.compass.ui.theme.appColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Single-axis level for a device held upright against something.
 *
 * ### Why this exists alongside [BubbleLevelIndicator]
 * The bullseye reads two axes at once, which is right for a phone lying on a
 * surface and wrong for one held against a door frame: there, `pitch` is pinned
 * near 90°, the bullseye's `normY` clamps to 1, and the bubble sits dead
 * against the rim reporting nothing. The question in this posture is
 * one-dimensional — *is this line plumb?* — so the instrument is too.
 *
 * ### What it draws
 * A horizontal rule that rotates against a fixed reference line. When the two
 * coincide, the edge the phone is held against is plumb. That is the reverse of
 * the bullseye's convention (there the *indicator* moves); it is deliberate,
 * because a rotating rule is read directly against the thing being measured —
 * the user is looking at a frame edge and a line, and judging parallel.
 *
 * ### Signalling level
 * Three channels at once, the same as the bullseye and for the same reason:
 * colour, a solid fill on the reference line, and a haptic tick on arrival.
 * Colour alone fails deuteranopia.
 *
 * @param rollDegrees rotation about the viewing axis, 0 = plumb. This must come
 *   from [com.pubpeb.compass.sensor.SensorRepository.screenRoll] and **not**
 *   from `getOrientation`'s roll, which degenerates at exactly the ±90° pitch
 *   this instrument is used at.
 * @param modifier caller sets the size.
 */
@Composable
fun PlumbLevelIndicator(
    rollDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val haptics = LocalHapticFeedback.current

    // Fold to the nearest quarter turn: held upside down or on its side, the
    // phone is still being asked the same question about the same edge, so the
    // instrument should answer it rather than read ±180 off.
    val folded = foldToQuarterTurn(rollDegrees)

    val spec = instrumentSpec()

    val animRoll by animateFloatAsState(
        targetValue = folded,
        animationSpec = spec,
        label = "plumbRoll",
    )

    // Read off the animated value so colour, fill and haptic all agree with the
    // line actually on screen — same rule as the bullseye.
    val isLevel = abs(animRoll) < Constants.Level.PLUMB_LEVEL_DEG

    val lineColor = when {
        isLevel -> colors.levelOk
        abs(animRoll) < Constants.Level.PLUMB_MAX_DEG -> colors.levelWarn
        else -> colors.levelCritical
    }

    LaunchedEffect(isLevel) {
        if (isLevel) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val referenceAlpha by animateFloatAsState(
        targetValue = if (isLevel) Constants.Level.RING_FILL_ALPHA else 0.35f,
        animationSpec = chromeSpec(Constants.Ui.INSTRUMENT_FILL_MS),
        label = "plumbReference",
    )

    val description = if (isLevel) {
        "Plumb"
    } else {
        "Off plumb by ${abs(folded).roundToInt()} degrees"
    }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfSpan = size.minDimension / 2f * 0.92f

        // ── Fixed reference line — what "plumb" looks like ────────────────
        drawLine(
            color = colors.levelOk.copy(alpha = referenceAlpha),
            start = Offset(center.x - halfSpan, center.y),
            end = Offset(center.x + halfSpan, center.y),
            strokeWidth = 1.5f.dp.toPx(),
        )

        // ── Fixed end ticks, so the reference reads as a scale not a stray rule ──
        val tickHalf = halfSpan * 0.08f
        for (side in listOf(-1f, 1f)) {
            drawLine(
                color = colors.crosshair,
                start = Offset(center.x + side * halfSpan, center.y - tickHalf),
                end = Offset(center.x + side * halfSpan, center.y + tickHalf),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // ── The moving rule ───────────────────────────────────────────────
        // Screen Y grows downward, so a positive (clockwise-from-the-user)
        // roll must rotate the rule counter-clockwise on screen for it to stay
        // aligned with the world edge the phone is held against.
        rotate(degrees = -animRoll, pivot = center) {
            drawLine(
                color = lineColor,
                start = Offset(center.x - halfSpan, center.y),
                end = Offset(center.x + halfSpan, center.y),
                strokeWidth = 3.dp.toPx(),
            )
            // A dot at each end gives the rule a direction to read its tilt by;
            // a bare line reads as symmetric and its small angles get lost.
            for (side in listOf(-1f, 1f)) {
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(center.x + side * halfSpan, center.y),
                )
            }
        }

        // ── Centre pivot ──────────────────────────────────────────────────
        drawCircle(
            color = colors.roseRing,
            radius = 5.dp.toPx(),
            center = center,
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
}

/**
 * Folds a roll in [-180, +180] onto [-45, +45] — the offset from the nearest
 * quarter turn.
 *
 * The user holding a phone sideways, or upside down, against a door frame is
 * asking the same question about the same edge. Reporting "170° off plumb"
 * there would be arithmetically true and useless; what they want to know is
 * that the edge is 10° out.
 *
 * ```
 *    0° ->   0°      90° ->  0°     -90° ->  0°     180° ->  0°
 *   10° ->  10°     100° -> 10°     -80° -> 10°     170° -> -10°
 *  -10° -> -10°      44° -> 44°      46° -> -44°
 * ```
 */
internal fun foldToQuarterTurn(deg: Float): Float {
    val wrapped = ((deg % 90f) + 90f) % 90f      // [0, 90)
    return if (wrapped > 45f) wrapped - 90f else wrapped
}
