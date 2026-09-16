package com.pubpeb.compass.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
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
import com.pubpeb.compass.util.shortestArcDelta
import kotlin.math.abs
import kotlin.math.roundToInt
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
    lockedBearing: Float? = null,
    onToggleLock: (() -> Unit)? = null,
) {
    val c = appColors

    // ── Shortest-arc accumulator ──────────────────────────────────────────────
    // animateFloatAsState interpolates linearly between floats. Without this fix,
    // crossing 0°/360° (e.g. 359°→0°) produces a delta of -359°, spinning the
    // rose backwards almost a full turn instead of nudging it +1°.
    //
    // The accumulator and its derivation now live in rememberContinuousAngle.
    val continuousAngle = rememberContinuousAngle(azimuth)

    // NoBouncy, not MediumBouncy: a liquid-damped compass card settles, it does
    // not oscillate. The overshoot also made the card visibly disagree with the
    // un-animated degree readout above it every time the phone stopped turning.
    val rotationSpec: AnimationSpec<Float> = roseSpec()

    val animatedAngle by animateFloatAsState(
        targetValue   = continuousAngle,
        animationSpec = rotationSpec,
        label         = "compassRotation",
    )

    // Held so the marker can finish fading out after the bearing is gone —
    // see Constants.Ui.LOCK_FADE_MS for why clearing a lock is not instant.
    var lastLockedBearing by remember { mutableFloatStateOf(0f) }
    lockedBearing?.let { lastLockedBearing = it }
    val markerAlpha by animateFloatAsState(
        targetValue = if (lockedBearing != null) 1f else 0f,
        animationSpec = chromeSpec(Constants.Ui.LOCK_FADE_MS),
        label = "lockMarkerFade",
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

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
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

        // ── Locked bearing marker ──────────────────────────────────────────
        // Drawn OUTSIDE the rotate block, at (bearing − cardAngle), which is
        // the same screen position the card rotation would have produced. Doing
        // it this way rather than inside the block is what lets the degree
        // numeral beside it stay upright: text drawn inside `rotate` comes out
        // rotated with the card, and the counter-rotation dance that
        // drawCardinalLabels has to do for its glyphs is not worth repeating
        // for one number.
        if (markerAlpha > 0.01f) {
            val bearing = lockedBearing ?: lastLockedBearing
            drawLockMarker(
                center = center,
                radius = radius,
                screenAngleDeg = bearing - animatedAngle,
                offCourseDeg = shortestArcDelta(bearing, azimuth),
                color = c.levelOk.copy(alpha = markerAlpha),
                textColor = c.levelOk.copy(alpha = markerAlpha),
                density = this,
            )
        }

        // ── Fixed lubber index (outside the rotation — this is the heading) ─
        drawLubberIndex(center, radius, c.lubber)
    }

        // ── Lock control, at the hub ──────────────────────────────────────
        // The centre of the card is the one place on this canvas nothing else
        // occupies, and it puts the control on the instrument it acts on rather
        // than in the top bar filed between "daylight" and "settings" — which
        // grouped a live state of the primary instrument with two occasional
        // utility toggles.
        //
        // A plain tap, not a long-press on the rose: this app has no long-press
        // vocabulary anywhere else, and inventing one for a single control is
        // the cost ArOverlayScreen's torch button already declined to pay.
        if (onToggleLock != null) {
            val locked = lockedBearing != null
            // 56 dp, matching the AR tool buttons — the same control in the
            // app's other mode. The old 44 dp was also below Material's 48 dp
            // interactive minimum, so this fixes a real accessibility defect on
            // the way past.
            //
            // A visible ring, and not just a bigger tap target, is the actual
            // fix for "it doesn't read as a button": a chromeless icon in a
            // top-right row inherits discoverability from where it sits, and a
            // chromeless icon floating in the middle of a drawing inherits
            // nothing.
            //
            // Ring only, no filled surface. The obvious `buttonBg` chip renders
            // at roughly 1.2:1 against `roseBg` in the Daylight palette, which
            // is the same sub-3:1 failure Theme.kt already had to correct once
            // for `roseRing` — not worth reintroducing for a background.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (locked) c.levelOk.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (locked) c.levelOk else c.roseRing,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggleLock)
                    .semantics {
                        contentDescription = if (locked) {
                            "Clear locked bearing"
                        } else {
                            "Lock this bearing"
                        }
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    // Open shackle versus closed shackle — an actual silhouette
                    // difference. The previous pairing was filled-vs-outlined
                    // Lock, which is the same shape twice, leaving colour to
                    // carry the state alone. This app's own rule, stated on the
                    // bubble level, is that colour is never the only channel.
                    imageVector = if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = if (locked) c.levelOk else c.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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

/**
 * Marks a locked bearing on the rotating card.
 *
 * A hollow ring on the rim rather than a filled wedge: the lubber index is
 * already a solid arrow and a second solid mark reads as a second heading. This
 * one is a target the heading is meant to arrive at, and hollow-versus-solid is
 * the difference between "where you are pointing" and "where you meant to
 * point" without spending another colour.
 *
 * Green ([com.pubpeb.compass.ui.theme.AppPalette.levelOk]) because it is the
 * palette's existing "this is correct" colour — the same one the level uses on
 * arrival — and because red remains reserved for north alone.
 *
 * @param screenAngleDeg the locked direction already converted to SCREEN
 *   angle (bearing − card rotation), because this is drawn outside the card's
 *   `rotate` block so its degree numeral can stay upright.
 */
private fun DrawScope.drawLockMarker(
    center: Offset,
    radius: Float,
    screenAngleDeg: Float,
    offCourseDeg: Float,
    color: Color,
    textColor: Color,
    density: Density,
) {
    // 0° is up on the screen, and screen angles run clockwise from +x, so the
    // usual sin/cos swap applies — the same convention buildTickPaths uses.
    val rad = Math.toRadians(screenAngleDeg.toDouble())
    val r = radius - 6.dp.toPx()
    val x = center.x + (r * sin(rad)).toFloat()
    val y = center.y - (r * cos(rad)).toFloat()

    val onCourse = abs(offCourseDeg) <= Constants.Heading.LOCK_ON_COURSE_DEG

    // Hollow while there is still turning to do, solid once the heading has
    // arrived. This is the app's existing idiom for "you are there" — the
    // bubble level fills its target ring the same way, and reusing it means the
    // user learns one convention rather than two.
    if (onCourse) {
        drawCircle(color = color, radius = 5.5f.dp.toPx(), center = Offset(x, y))
    } else {
        drawCircle(
            color = color,
            radius = 5.5f.dp.toPx(),
            center = Offset(x, y),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    // Short spoke inward, so the mark still reads as belonging to a direction
    // when it sits against a dense band of minute ticks.
    val inner = radius - 18.dp.toPx()
    drawLine(
        color = color,
        start = Offset(x, y),
        end = Offset(
            center.x + (inner * sin(rad)).toFloat(),
            center.y - (inner * cos(rad)).toFloat(),
        ),
        strokeWidth = 1.5f.dp.toPx(),
    )

    // The magnitude, beside the mark rather than in the readout row. How far
    // round the rim the mark sits already says *which way* to turn; this says
    // how far. Suppressed once on course, because a filled dot already means
    // "zero, arrived" and a `0°` beside it would be noise.
    if (onCourse) return

    val label = "${abs(offCourseDeg).roundToInt()}°"
    val textPx = with(density) { 10.sp.toPx() }
    val paint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = textColor.toArgb()
        textSize = textPx
        textAlign = android.graphics.Paint.Align.CENTER
    }
    // Pushed further out along the same radial line than the mark itself, so it
    // sits outside the tick ring instead of on top of the minute ticks.
    val labelR = radius + 11.dp.toPx()
    val lx = center.x + (labelR * sin(rad)).toFloat()
    val ly = center.y - (labelR * cos(rad)).toFloat() + textPx / 3f
    drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
}
