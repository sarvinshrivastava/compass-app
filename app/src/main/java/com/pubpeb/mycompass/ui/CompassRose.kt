package com.pubpeb.mycompass.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── Design palette ──────────────────────────────────────────────────────────
private val ColorBackground   = Color(0xFF111318)
private val ColorRing         = Color(0xFF2A2E38)
private val ColorTickMinor    = Color(0xFF4A5060)
private val ColorTickMajor    = Color(0xFF8090B0)
private val ColorTickCardinal = Color(0xFFCCD6F6)
private val ColorNeedleNorth  = Color(0xFFE63946)
private val ColorNeedleSouth  = Color(0xFFCCD6F6)
private val ColorCenter       = Color(0xFF1E2230)
private val ColorDegreeText   = Color(0xFFCCD6F6)
private val ColorCardinalText = Color(0xFFFFFFFF)
private val ColorNorthLabel   = Color(0xFFE63946)

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
 * Canvas-drawn compass rose that rotates opposite to [azimuth] so the heading
 * label always points "up" (toward the top of the screen).
 *
 * Animation: spring physics with medium stiffness for a natural feel.
 *
 * // TODO Phase 2 – ARCore hook:
 * //   Accept an optional `arOverlay: @Composable BoxScope.() -> Unit` parameter
 * //   and render it on top of this Canvas inside a Box so AR anchors can be
 * //   drawn in the same coordinate space.
 */
@Composable
fun CompassRose(
    azimuth: Float,
    modifier: Modifier = Modifier,
) {
    // ── Shortest-arc accumulator ──────────────────────────────────────────────
    // animateFloatAsState interpolates linearly between floats. Without this fix,
    // crossing 0°/360° (e.g. 359°→0°) produces a delta of -359°, spinning the
    // rose backwards almost a full turn instead of nudging it +1°.
    //
    // The fix maintains a *continuous* (unbounded) angle. On every recomposition
    // we compute the shortest signed arc from the previous value and accumulate it,
    // so the animator always takes the short path around the circle.
    //
    // Delta formula: ((new - old + 540) % 360) - 180  →  always in (-180, +180]
    // Verification:
    //   359°→0°:  ((0-359+540)%360)-180 = (181%360)-180 = 181-180 = +1°  ✓
    //   0°→359°:  ((359-0+540)%360)-180 = (899%360)-180 = 179-180 = -1°  ✓
    val continuousAngle = remember { mutableFloatStateOf(azimuth) }
    val prevAzimuth     = remember { mutableFloatStateOf(azimuth) }

    val delta = ((azimuth - prevAzimuth.floatValue + 540f) % 360f) - 180f
    continuousAngle.floatValue += delta
    prevAzimuth.floatValue = azimuth

    val animatedAngle by animateFloatAsState(
        targetValue = continuousAngle.floatValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "compassRotation",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.92f

        // ── Background circle ──────────────────────────────────────────────
        drawCircle(color = ColorBackground, radius = radius, center = center)
        drawCircle(color = ColorRing, radius = radius, center = center, style =
            androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))

        // ── Rotating rose ─────────────────────────────────────────────────
        // The rose rotates -azimuth so that North stays at the top of the
        // physical display while the device heading changes.
        rotate(degrees = -animatedAngle, pivot = center) {
            drawTicks(center, radius)
            drawCardinalLabels(center, radius)
        }

        // ── Static needle (always points up) ──────────────────────────────
        drawNeedle(center, radius)

        // ── Center cap ────────────────────────────────────────────────────
        drawCircle(color = ColorCenter, radius = 10.dp.toPx(), center = center)
        drawCircle(
            color = ColorTickCardinal,
            radius = 10.dp.toPx(),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f.dp.toPx()),
        )
    }
}

// ── Drawing helpers ──────────────────────────────────────────────────────────

private fun DrawScope.drawTicks(center: Offset, radius: Float) {
    for (deg in 0 until 360) {
        val angle = Math.toRadians(deg.toDouble())
        val isCardinal = deg % 90 == 0
        val isMajor    = deg % 10 == 0
        val tickLen = when {
            isCardinal -> radius * 0.18f
            isMajor    -> radius * 0.10f
            else       -> radius * 0.05f
        }
        val strokePx = when {
            isCardinal -> 2.5f.dp.toPx()
            isMajor    -> 1.5f.dp.toPx()
            else       -> 0.8f.dp.toPx()
        }
        val color = when {
            isCardinal -> ColorTickCardinal
            isMajor    -> ColorTickMajor
            else       -> ColorTickMinor
        }

        val outerX = center.x + radius * sin(angle).toFloat()
        val outerY = center.y - radius * cos(angle).toFloat()
        val innerX = center.x + (radius - tickLen) * sin(angle).toFloat()
        val innerY = center.y - (radius - tickLen) * cos(angle).toFloat()

        drawLine(
            color = color,
            start = Offset(outerX, outerY),
            end = Offset(innerX, innerY),
            strokeWidth = strokePx,
        )
    }
}

private fun DrawScope.drawCardinalLabels(center: Offset, radius: Float) {
    val labelRadius = radius * 0.68f
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
        paint.color = if (label == "N") ColorNorthLabel.toArgb()
                      else if (isMainCardinal) ColorCardinalText.toArgb()
                      else ColorDegreeText.toArgb()
        paint.isFakeBoldText = isMainCardinal

        drawContext.canvas.nativeCanvas.drawText(
            label,
            x,
            y + paint.textSize / 3f,   // vertically centre the glyph
            paint,
        )
    }

    // Draw degree numbers every 30° (skip cardinal positions)
    val degPaint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 9.dp.toPx()
        color = ColorDegreeText.copy(alpha = 0.6f).toArgb()
    }
    val degRadius = radius * 0.52f
    for (deg in 0 until 360 step 30) {
        if (deg % 90 == 0) continue   // cardinals already labelled
        val angle = Math.toRadians(deg.toDouble())
        val x = center.x + degRadius * sin(angle).toFloat()
        val y = center.y - degRadius * cos(angle).toFloat()
        drawContext.canvas.nativeCanvas.drawText(
            deg.toString(),
            x,
            y + degPaint.textSize / 3f,
            degPaint,
        )
    }
}

private fun DrawScope.drawNeedle(center: Offset, radius: Float) {
    val tipN  = Offset(center.x, center.y - radius * 0.60f)
    val tipS  = Offset(center.x, center.y + radius * 0.50f)
    val halfW = radius * 0.04f

    // North half — red
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(tipN.x, tipN.y)
            lineTo(center.x - halfW, center.y)
            lineTo(center.x + halfW, center.y)
            close()
        },
        color = ColorNeedleNorth,
    )
    // South half — silver
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(tipS.x, tipS.y)
            lineTo(center.x - halfW, center.y)
            lineTo(center.x + halfW, center.y)
            close()
        },
        color = ColorNeedleSouth,
    )
}
