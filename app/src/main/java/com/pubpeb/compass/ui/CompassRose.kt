package com.pubpeb.compass.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.Constants
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val cardinals = mapOf(
    0f   to "N",
    45f  to "NE",
    90f  to "E",
    135f to "SE",
    180f to "S",
    225f to "SW",
    270f to "W",
    315f to "NW",
)

/**
 * Canvas-drawn compass card that rotates opposite to [azimuth], so north on the
 * card always points at real north while the device turns underneath it.
 *
 * **The card carries north; the fixed index carries the heading.** That split is
 * the whole point of a compass and it is what this component used to get wrong:
 * a red needle drawn outside the rotation always pointed at 12 o'clock, i.e. at
 * the user's own heading, while every physical compass on earth uses red for
 * north. Red ([com.pubpeb.compass.ui.theme.AppPalette.northLabel]) is now
 * spent on exactly one thing here — the "N" glyph on the rotating card — and the
 * fixed 12 o'clock index is a neutral lubber line.
 */
@Composable
fun CompassRose(
    azimuth: Float,
    modifier: Modifier = Modifier,
) {
    val c = appColors

    // ── Shortest-arc accumulator ──────────────────────────────────────────────
    // animateFloatAsState interpolates linearly between floats. Without this fix,
    // crossing 0°/360° (e.g. 359°→0°) produces a delta of -359°, spinning the
    // rose backwards almost a full turn instead of nudging it +1°.
    //
    // The accumulator and its derivation now live in rememberContinuousAngle.
    val continuousAngle = rememberContinuousAngle(azimuth)
    val reduceMotion = rememberReduceMotion()

    // NoBouncy, not MediumBouncy: a liquid-damped compass card settles, it does
    // not oscillate. The overshoot also made the card visibly disagree with the
    // un-animated degree readout above it every time the phone stopped turning.
    val rotationSpec: AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium,
        )
    }

    val animatedAngle by animateFloatAsState(
        targetValue   = continuousAngle,
        animationSpec = rotationSpec,
        label         = "compassRotation",
    )

    // Capture canvas size so tick paths can be cached between frames
    val canvasSizePx = remember { mutableStateOf(IntSize.Zero) }
    val (minorTickPath, majorTickPath, cardinalTickPath) = remember(canvasSizePx.value) {
        val sz = canvasSizePx.value
        if (sz == IntSize.Zero) Triple(Path(), Path(), Path())
        else {
            val center = Offset(sz.width / 2f, sz.height / 2f)
            val radius = min(sz.width.toFloat(), sz.height.toFloat()) / 2f * Constants.Rose.RADIUS_FRACTION
            buildTickPaths(center, radius)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { canvasSizePx.value = it },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * Constants.Rose.RADIUS_FRACTION

        // ── Background circle ──────────────────────────────────────────────
        drawCircle(color = c.roseBg, radius = radius, center = center)
        drawCircle(
            color  = c.roseRing,
            radius = radius,
            center = center,
            style  = Stroke(width = 2.dp.toPx()),
        )

        // ── Rotating card ─────────────────────────────────────────────────
        // The card rotates -azimuth so that North stays at the top of the
        // physical display while the device heading changes.
        // Labels are counter-rotated inside drawCardinalLabels() so their
        // text glyphs always read upright on screen regardless of card angle.
        rotate(degrees = -animatedAngle, pivot = center) {
            // Draw cached tick paths (rebuilt only on canvas size change)
            drawPath(minorTickPath,    color = c.tickMinor,    style = Stroke(width = 0.8f.dp.toPx()))
            drawPath(majorTickPath,    color = c.tickMajor,    style = Stroke(width = 1.5f.dp.toPx()))
            drawPath(cardinalTickPath, color = c.tickCardinal, style = Stroke(width = 2.5f.dp.toPx()))
            drawCardinalLabels(
                center             = center,
                radius             = radius,
                rotationAngle      = animatedAngle,
                northColor         = c.northLabel,
                cardinalColor      = c.cardinalText,
                interCardinalColor = c.tickMajor,
            )
        }

        // ── Fixed lubber index (outside the rotation — this is the heading) ─
        drawLubberIndex(center, radius, c.lubber)
    }
}

// ── Drawing helpers ──────────────────────────────────────────────────────────
//
// These are DrawScope/plain functions, not composables, so every colour they
// use arrives as a parameter — reading `appColors` here would silently pin one
// theme's palette into all three.

/**
 * Builds three tick-mark Paths (minor, major, cardinal) from the given center and radius.
 * Called once per canvas size; the result is cached in the composable.
 */
private fun buildTickPaths(center: Offset, radius: Float): Triple<Path, Path, Path> {
    val minorPath    = Path()
    val majorPath    = Path()
    val cardinalPath = Path()

    for (deg in 0 until 360) {
        val angle      = Math.toRadians(deg.toDouble())
        val isCardinal = deg % 90 == 0
        val isMajor    = deg % 10 == 0
        val tickLen = when {
            isCardinal -> radius * 0.18f
            isMajor    -> radius * 0.10f
            else       -> radius * 0.05f
        }
        val path = when {
            isCardinal -> cardinalPath
            isMajor    -> majorPath
            else       -> minorPath
        }
        val outerX = center.x + radius * sin(angle).toFloat()
        val outerY = center.y - radius * cos(angle).toFloat()
        val innerX = center.x + (radius - tickLen) * sin(angle).toFloat()
        val innerY = center.y - (radius - tickLen) * cos(angle).toFloat()
        path.moveTo(outerX, outerY)
        path.lineTo(innerX, innerY)
    }

    return Triple(minorPath, majorPath, cardinalPath)
}

/**
 * Draws N/NE/E/SE/S/SW/W/NW labels at their positions on the card.
 *
 * Because this function is called inside a `rotate(-animatedAngle)` block, the
 * label *positions* orbit correctly around the card. However, without compensation
 * the text glyphs would also tilt with the card, making them hard to read.
 *
 * Fix: for each label, save the native canvas state, rotate by +[rotationAngle]
 * around the label's own (x, y) pivot to cancel the enclosing -[rotationAngle]
 * transform, draw the text upright, then restore. The label position still tracks
 * the card, but the glyph orientation is always screen-aligned.
 */
private fun DrawScope.drawCardinalLabels(
    center: Offset,
    radius: Float,
    rotationAngle: Float,
    northColor: Color,
    cardinalColor: Color,
    interCardinalColor: Color,
) {
    val labelRadius = radius * 0.68f
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val paint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    for ((deg, label) in cardinals) {
        val angle = Math.toRadians(deg.toDouble())
        val x = center.x + labelRadius * sin(angle).toFloat()
        val y = center.y - labelRadius * cos(angle).toFloat()

        val isMainCardinal = deg % 90 == 0f
        paint.textSize = if (isMainCardinal) 16.dp.toPx() else 11.dp.toPx()
        // Red lives here and nowhere else on this screen.
        paint.color = when {
            label == "N"   -> northColor.toArgb()
            isMainCardinal -> cardinalColor.toArgb()
            else           -> interCardinalColor.toArgb()
        }
        paint.isFakeBoldText = isMainCardinal

        // Counter-rotate around the label's own position so the glyph stays upright.
        // The enclosing rotate(-rotationAngle) block has already moved the canvas;
        // rotating +rotationAngle at (x, y) restores 0° net rotation for the text.
        nativeCanvas.save()
        nativeCanvas.rotate(rotationAngle, x, y)
        nativeCanvas.drawText(
            label,
            x,
            y + paint.textSize / 3f,   // vertically centre the glyph
            paint,
        )
        nativeCanvas.restore()
    }
}

/**
 * Draws the lubber line — the fixed index at 12 o'clock marking the direction
 * the device itself is pointing.
 *
 * Drawn *outside* the rotation on purpose: it is the one mark that must not
 * move. The card slides underneath it, and whatever bearing sits under the
 * apex is the heading — the same number shown at 52 sp above the rose.
 *
 * Deliberately not red: red is reserved for north on the card, and two red
 * marks pointing different ways is the exact confusion this replaced.
 */
private fun DrawScope.drawLubberIndex(center: Offset, radius: Float, color: Color) {
    val gap       = 3.dp.toPx()          // hairline run from apex down to the ring
    val height    = 13.dp.toPx()
    val halfWidth = 11.dp.toPx() / 2f

    val apexY = center.y - radius - gap
    val baseY = apexY - height

    drawPath(
        path = Path().apply {
            moveTo(center.x, apexY)                 // apex, pointing down at the ring
            lineTo(center.x - halfWidth, baseY)
            lineTo(center.x + halfWidth, baseY)
            close()
        },
        color = color,
    )
    drawLine(
        color       = color,
        start       = Offset(center.x, apexY),
        end         = Offset(center.x, center.y - radius),
        strokeWidth = 1.dp.toPx(),
    )
}
