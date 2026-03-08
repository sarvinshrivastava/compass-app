package com.pubpeb.mycompass.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

// ── Design palette ────────────────────────────────────────────────────────────
private val BubbleBg       = Color(0xFF111318)
private val BubbleRing     = Color(0xFF2A2E38)
private val CrosshairColor = Color(0xFF3A4050)
private val LevelZoneRing  = Color(0xFF4CAF80)   // green — marks the "flat" target
private val BubbleOk       = Color(0xFF4CAF80)   // green dot — device is level
private val BubbleTilted   = Color(0xFFE6A817)   // amber dot — tilted but usable
private val BubbleCritical = Color(0xFFE63946)   // red dot  — heavily tilted

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
    // ── Normalise tilt to [-1, 1] ─────────────────────────────────────────────
    val maxTilt = 45f   // degrees → full deflection; beyond this the dot is pinned to the edge

    var normX = (-rollDegrees  / maxTilt).coerceIn(-1f, 1f)
    var normY = ( pitchDegrees / maxTilt).coerceIn(-1f, 1f)

    // Clamp to unit circle so the dot stays inside the rim regardless of combined tilt
    val mag = sqrt(normX * normX + normY * normY)
    if (mag > 1f) {
        normX /= mag
        normY /= mag
    }

    // ── Animate X and Y independently with spring physics ────────────────────
    val animNormX by animateFloatAsState(
        targetValue = normX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bubbleX",
    )
    val animNormY by animateFloatAsState(
        targetValue = normY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bubbleY",
    )

    // Determine bubble colour based on tilt magnitude of the *animated* position
    val animMag = sqrt(animNormX * animNormX + animNormY * animNormY)
    val bubbleColor = when {
        animMag < 0.15f -> BubbleOk
        animMag < 0.70f -> BubbleTilted
        else            -> BubbleCritical
    }

    Canvas(modifier = modifier) {
        val center       = Offset(size.width / 2f, size.height / 2f)
        val outerRadius  = size.minDimension / 2f * 0.92f
        val bubbleZone   = outerRadius * 0.68f   // radius the bubble centre can travel
        val bubbleRadius = outerRadius * 0.14f   // visual size of the bubble dot
        val levelZone    = outerRadius * 0.18f   // radius of the "target" ring
        val crossLen     = outerRadius * 0.88f   // half-length of crosshair lines

        // ── 1. Background circle ──────────────────────────────────────────
        drawCircle(color = BubbleBg, radius = outerRadius, center = center)

        // ── 2. Ring border ────────────────────────────────────────────────
        drawCircle(
            color = BubbleRing,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // ── 3. Crosshair lines ────────────────────────────────────────────
        drawLine(
            color = CrosshairColor,
            start = Offset(center.x - crossLen, center.y),
            end   = Offset(center.x + crossLen, center.y),
            strokeWidth = 0.8f.dp.toPx(),
        )
        drawLine(
            color = CrosshairColor,
            start = Offset(center.x, center.y - crossLen),
            end   = Offset(center.x, center.y + crossLen),
            strokeWidth = 0.8f.dp.toPx(),
        )

        // ── 4. Level target ring (green circle) ────────────────────────────
        drawCircle(
            color = LevelZoneRing,
            radius = levelZone,
            center = center,
            style = Stroke(width = 1.5f.dp.toPx()),
        )

        // ── 5. Degree tick marks (every 15°, 4 cardinal ticks slightly longer) ──
        val tickPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = CrosshairColor.toArgb()
            strokeWidth = 0.6f.dp.toPx()
            style = android.graphics.Paint.Style.STROKE
        }
        for (deg in 0 until 360 step 15) {
            val rad      = Math.toRadians(deg.toDouble())
            val isMain   = deg % 90 == 0
            val tickOuter = outerRadius
            val tickInner = outerRadius - if (isMain) outerRadius * 0.12f else outerRadius * 0.06f
            drawContext.canvas.nativeCanvas.drawLine(
                center.x + (tickInner * kotlin.math.sin(rad)).toFloat(),
                center.y - (tickInner * kotlin.math.cos(rad)).toFloat(),
                center.x + (tickOuter * kotlin.math.sin(rad)).toFloat(),
                center.y - (tickOuter * kotlin.math.cos(rad)).toFloat(),
                tickPaint,
            )
        }

        // ── 6. Bubble dot (filled + border) ───────────────────────────────
        val dotX = center.x + animNormX * bubbleZone
        val dotY = center.y + animNormY * bubbleZone

        drawCircle(
            color = bubbleColor.copy(alpha = 0.30f),
            radius = bubbleRadius * 1.4f,     // soft glow halo
            center = Offset(dotX, dotY),
        )
        drawCircle(
            color = bubbleColor.copy(alpha = 0.80f),
            radius = bubbleRadius,
            center = Offset(dotX, dotY),
        )
        drawCircle(
            color = bubbleColor,
            radius = bubbleRadius,
            center = Offset(dotX, dotY),
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
}
