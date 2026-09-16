package com.pubpeb.compass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pubpeb.compass.Constants

import com.pubpeb.compass.ui.theme.appColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rim radius as a fraction of the canvas half-size.
 *
 * Shared by the draw block and [buildLevelTickPath]: both need it, and when
 * each computed it independently a change to one would have silently left the
 * cached ticks misaligned with the circle they mark.
 */
private const val RIM_RADIUS_FRACTION = 0.92f

/**
 * Circular bubble-level that tracks both [pitchDegrees] (tilt front/back) and
 * [rollDegrees] (tilt left/right) as a 2D dot position inside a circle.
 *
 * ### Coordinate convention
 * - Pitch > 0  → top of device tilts toward user → bubble moves **down**
 * - Roll  > 0  → left edge of device rises        → bubble moves **left**  (negated)
 *
 * ### Mapping to normalised [-1, 1] space
 * ```
 * normX = (-roll  / maxTilt).coerceIn(-1, 1)
 * normY = ( pitch / maxTilt).coerceIn(-1, 1)
 * ```
 * The vector is then clamped to the unit circle so the dot never escapes the rim.
 *
 * ### Signalling "level"
 * Reaching level is the one event this instrument exists to report, so it is
 * announced on three channels at once: the dot turns green, the target ring
 * fills solid (colour alone fails deuteranopia — the fill is the redundant
 * channel), and a haptic tick fires on the transition. The motion is
 * critically damped rather than bouncy for the same reason: a bouncing bubble
 * crosses level twice and settles on neither pass.
 *
 * ### Colour thresholds (magnitude of tilt vector)
 * - |tilt| < 0.15  → green  (device is effectively level)
 * - |tilt| < 0.70  → amber  (moderate tilt)
 * - |tilt| ≥ 0.70  → red    (severe tilt; accuracy degrades)
 *
 * @param pitchDegrees  Device pitch from SensorRepository / ViewModel (-90…+90 °)
 * @param rollDegrees   Device roll  from SensorRepository / ViewModel (-90…+90 °)
 * @param modifier      Caller sets the size (e.g. `Modifier.size(240.dp)`)
 */
@Composable
fun BubbleLevelIndicator(
    pitchDegrees: Float,
    rollDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val colors   = appColors
    val haptics  = LocalHapticFeedback.current


    // ── Normalise tilt to [-1, 1] ─────────────────────────────────────────────
    var normX = (-rollDegrees  / Constants.Level.MAX_TILT_DEG).coerceIn(-1f, 1f)
    var normY = ( pitchDegrees / Constants.Level.MAX_TILT_DEG).coerceIn(-1f, 1f)

    // Clamp to unit circle so the dot stays inside the rim regardless of combined tilt
    val mag = sqrt(normX * normX + normY * normY)
    if (mag > 1f) {
        normX /= mag
        normY /= mag
    }

    // ── Animate X and Y independently ─────────────────────────────────────────
    // Critically damped, like the fluid in a real spirit level: it glides and
    // stops. Overshoot would signal "level" on the way past and again on the
    // way back, holding neither.
    val bubbleSpec = instrumentSpec()

    val animNormX by animateFloatAsState(
        targetValue   = normX,
        animationSpec = bubbleSpec,
        label         = "bubbleX",
    )
    val animNormY by animateFloatAsState(
        targetValue   = normY,
        animationSpec = bubbleSpec,
        label         = "bubbleY",
    )

    // State is read off the *animated* position so every channel — dot colour,
    // ring fill, haptic — agrees with where the bubble is actually drawn.
    val animMag = sqrt(animNormX * animNormX + animNormY * animNormY)
    val isLevel = animMag < Constants.Level.LEVEL_THRESHOLD

    val bubbleColor = when {
        isLevel                     -> colors.levelOk
        animMag < Constants.Level.CRITICAL_THRESHOLD -> colors.levelWarn
        else                        -> colors.levelCritical
    }

    // Keying on isLevel means this body runs only when the boolean flips, so
    // the tick fires once on arrival and never repeats while held level.
    LaunchedEffect(isLevel) {
        if (isLevel) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val ringFillAlpha by animateFloatAsState(
        targetValue   = if (isLevel) Constants.Level.RING_FILL_ALPHA else 0f,
        animationSpec = chromeSpec(Constants.Ui.INSTRUMENT_FILL_MS),
        label         = "levelRingFill",
    )

    // The Canvas is otherwise a blank node to a screen reader.
    val tiltDeg = sqrt(pitchDegrees * pitchDegrees + rollDegrees * rollDegrees).roundToInt()
    val description = if (isLevel) "Level" else "Tilted $tiltDeg degrees"

    // Tick geometry depends only on the canvas size, so build it once per size
    // rather than once per frame — see the draw call below for why that matters.
    val canvasSizePx = remember { mutableStateOf(IntSize.Zero) }
    val tickPath = remember(canvasSizePx.value) {
        val sz = canvasSizePx.value
        if (sz == IntSize.Zero) Path() else buildLevelTickPath(sz)
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .onSizeChanged { canvasSizePx.value = it },
    ) {
        val center       = Offset(size.width / 2f, size.height / 2f)
        val outerRadius  = size.minDimension / 2f * RIM_RADIUS_FRACTION
        val bubbleZone   = outerRadius * 0.68f   // radius the bubble centre can travel
        val bubbleRadius = outerRadius * 0.14f   // visual size of the bubble dot
        val levelZone    = outerRadius * 0.18f   // radius of the "target" ring
        val crossLen     = outerRadius * 0.88f   // half-length of crosshair lines

        // ── 1. Background circle ──────────────────────────────────────────
        drawCircle(color = colors.roseBg, radius = outerRadius, center = center)

        // ── 2. Ring border ────────────────────────────────────────────────
        // roseRing and crosshair both clear WCAG 1.4.11's 3:1 floor. At the old
        // 1.37:1 the frame the bubble is measured against was invisible, which
        // left a dot floating in the dark with nothing to read it against.
        drawCircle(
            color  = colors.roseRing,
            radius = outerRadius,
            center = center,
            style  = Stroke(width = 2.dp.toPx()),
        )

        // ── 3. Crosshair lines ────────────────────────────────────────────
        drawLine(
            color = colors.crosshair,
            start = Offset(center.x - crossLen, center.y),
            end   = Offset(center.x + crossLen, center.y),
            strokeWidth = 0.8f.dp.toPx(),
        )
        drawLine(
            color = colors.crosshair,
            start = Offset(center.x, center.y - crossLen),
            end   = Offset(center.x, center.y + crossLen),
            strokeWidth = 0.8f.dp.toPx(),
        )

        // ── 4. Level target ring — hairline normally, solid once level ─────
        if (ringFillAlpha > 0f) {
            drawCircle(
                color  = colors.levelOk.copy(alpha = ringFillAlpha),
                radius = levelZone,
                center = center,
            )
        }
        drawCircle(
            color  = colors.levelOk,
            radius = levelZone,
            center = center,
            style  = Stroke(width = 1.5f.dp.toPx()),
        )

        // ── 5. Degree tick marks — one cached Path, one draw call ──────────
        // This Canvas redraws on every spring frame while the bubble moves, so
        // rebuilding 24 segments here cost more per second than the compass
        // rose's 360 ever did. Same treatment as CompassRose.buildTickPaths.
        drawPath(tickPath, color = colors.crosshair, style = Stroke(width = 0.6f.dp.toPx()))

        // ── 6. Bubble dot (filled + border) ───────────────────────────────
        val dotCenter = Offset(
            center.x + animNormX * bubbleZone,
            center.y + animNormY * bubbleZone,
        )

        drawCircle(
            color  = bubbleColor.copy(alpha = Constants.Level.HALO_ALPHA),
            radius = bubbleRadius * 1.4f,     // soft glow halo
            center = dotCenter,
        )
        drawCircle(
            color  = bubbleColor.copy(alpha = Constants.Level.FILL_ALPHA),
            radius = bubbleRadius,
            center = dotCenter,
        )
        drawCircle(
            color  = bubbleColor,
            radius = bubbleRadius,
            center = dotCenter,
            style  = Stroke(width = 1.5f.dp.toPx()),
        )
    }
}

/**
 * Builds the 24 rim ticks (every 15°, longer at the cardinals) as one [Path].
 *
 * Cached by the caller against the canvas size. The bubble's Canvas redraws on
 * every spring animation frame while the phone is moving, so recomputing these
 * inline cost more per second than the compass rose's 360 ticks ever did —
 * the rose was already fixed this way on this branch, and this is the sibling
 * that was missed.
 */
private fun buildLevelTickPath(size: IntSize): Path {
    val path = Path()
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = min(size.width, size.height) / 2f * RIM_RADIUS_FRACTION

    for (deg in 0 until 360 step 15) {
        val rad = Math.toRadians(deg.toDouble())
        val isMain = deg % 90 == 0
        val tickInner = outerRadius - if (isMain) outerRadius * 0.12f else outerRadius * 0.06f
        val sinR = sin(rad).toFloat()
        val cosR = cos(rad).toFloat()
        path.moveTo(center.x + tickInner * sinR, center.y - tickInner * cosR)
        path.lineTo(center.x + outerRadius * sinR, center.y - outerRadius * cosR)
    }
    return path
}
