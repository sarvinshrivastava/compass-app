package com.pubpeb.compass.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.CompassAccuracy
import com.pubpeb.compass.ui.theme.AppPalette
import com.pubpeb.compass.ui.theme.appColors

/**
 * Explains how to calibrate the magnetometer, and says when it has worked.
 *
 * ### Why this is a dialog and not a gate
 * The compass keeps running behind it and the dialog can always be dismissed.
 * "The compass never waits for anything" is the rule this app is built on; a
 * calibration step that blocked a heading would break it for the one user who
 * most needs a rough bearing right now — and the reading is *degraded*, not
 * absent, so refusing to show it would be a lie in the opposite direction.
 *
 * ### Why it is both automatic and manual
 * It opens on its own when the platform reports the heading is not trustworthy,
 * because most people do not know that a phone compass needs calibrating at all
 * and will simply conclude the app is wrong. It is also reachable from Settings
 * at any time, because the automatic trigger is the platform's opinion and
 * someone who thinks the heading looks wrong should not have to wait for the
 * platform to agree before they can do anything about it.
 *
 * ### Why it watches its own success
 * The instructions end by themselves. [accuracy] is live, so the dialog shows
 * the state change as it happens and switches to a success panel the moment the
 * magnetometer reports a usable reading — the single most common complaint
 * about calibration prompts elsewhere is that they never say whether it worked.
 */
@Composable
fun CalibrationDialog(
    accuracy: CompassAccuracy,
    onDismiss: () -> Unit,
) {
    val c = appColors
    val succeeded = accuracy.isTrustworthy

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.bgCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "CALIBRATION",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                AccuracyRow(accuracy = accuracy, colors = c)

                if (succeeded) {
                    Text(
                        text = "Done — the heading is reliable again.",
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                    Text(
                        text = "Phone compasses drift, so this will come back " +
                            "from time to time. It is normal, and it is not a fault.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                } else {
                    FigureEightDiagram(
                        tint = c.textPrimary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 132.dp, height = 84.dp),
                    )

                    Text(
                        text = "Move the phone through a figure eight, turning " +
                            "your wrist as you go so every side of it gets to " +
                            "face a different direction. Five or six passes is " +
                            "usually enough.",
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "IF IT WILL NOT SETTLE",
                        color = c.textSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    InterferenceHint("Step away from anything with a magnet in it — " +
                        "speakers, laptops, phone mounts, headphones.")
                    InterferenceHint("Get out of the car. A vehicle body is a large " +
                        "piece of steel and no compass works reliably inside one.")
                    InterferenceHint("Go outdoors if you can. Reinforced concrete and " +
                        "steel framing distort the field indoors, sometimes badly.")

                    Text(
                        text = "This panel will say so as soon as the reading is " +
                            "good. You can close it and keep using the compass " +
                            "at any time.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (succeeded) "Close" else "Not now",
                            color = c.textPrimary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live accuracy state.
 *
 * This is the one live region in the dialog, and it is the right place for one:
 * it changes a handful of times over the whole interaction rather than
 * continuously, and its changing is the entire point of the screen.
 */
@Composable
private fun AccuracyRow(accuracy: CompassAccuracy, colors: AppPalette) {
    val (label, tint) = accuracyLabel(accuracy, colors)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Sensor accuracy: $label"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tint, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InterferenceHint(text: String) {
    val c = appColors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "·",
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

/**
 * Sensor accuracy as a phrase and a colour.
 *
 * Shared with the Settings dialog's Calibrate row so both surfaces name the
 * same state the same way. Two independent mappings had already started to
 * drift — Settings said nothing at all above LOW, which is exactly when
 * somebody who distrusts the heading goes looking.
 */
internal fun accuracyLabel(
    accuracy: CompassAccuracy,
    c: AppPalette,
): Pair<String, Color> = when (accuracy) {
    CompassAccuracy.HIGH -> "GOOD" to c.levelOk
    CompassAccuracy.MEDIUM -> "USABLE" to c.levelOk
    CompassAccuracy.LOW -> "POOR" to c.levelWarn
    CompassAccuracy.UNRELIABLE -> "NOT USABLE" to c.levelWarn
    CompassAccuracy.NO_SIGNAL -> "NO SIGNAL" to c.levelCritical
}

/**
 * The figure-eight motion, animated.
 *
 * A still diagram of this gesture is ambiguous — people read the crossing loop
 * as a flat path to trace on a table rather than a movement through space —
 * and the instruction "figure of eight" on its own is the single most
 * complained-about part of every phone compass. A dot tracing the path shows
 * the direction and the pace without another sentence of copy.
 */
@Composable
private fun FigureEightDiagram(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val c = appColors
    val reduceMotion = rememberReduceMotion()

    // AD-13 exemption, named: `infiniteRepeatable` needs a
    // DurationBasedAnimationSpec, which the MotionSpecs helpers do not return.
    // Reduce-motion is honoured differently here and more strongly — the
    // travelling dot is not drawn at all, leaving the static path, so there is
    // no motion to gate in the first place.
    val transition = rememberInfiniteTransition(label = "figure8")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Constants.Ui.FIGURE_EIGHT_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "figure8Phase",
    )

    Canvas(modifier = modifier) {
        val path = lemniscatePath(size)
        drawPath(
            path = path,
            color = c.textSecondary.copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx()),
        )

        // A user who has asked the system for less motion gets the path and no
        // travelling dot; the diagram still reads, it just stops moving.
        if (!reduceMotion) {
            drawCircle(
                color = tint,
                radius = 5.dp.toPx(),
                center = lemniscatePoint(size, t),
            )
        }
    }
}

/**
 * Lemniscate of Gerono, which is the figure eight people actually draw:
 * `x = sin(θ)`, `y = sin(θ)·cos(θ)`, θ over a full turn.
 *
 * Chosen over the more familiar Bernoulli lemniscate because this one is
 * defined by a plain parameterisation with no pole at the crossing, so the
 * travelling dot in [FigureEightDiagram] can be placed with the same two lines
 * that draw the curve.
 */
private fun lemniscatePath(size: Size): Path = Path().apply {
    val steps = 96
    for (i in 0..steps) {
        val p = lemniscatePoint(size, i / steps.toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun lemniscatePoint(size: Size, t: Float): Offset {
    val theta = t * 2f * Math.PI.toFloat()
    val halfW = size.width / 2f * 0.88f
    val halfH = size.height / 2f * 0.88f
    return Offset(
        x = size.width / 2f + halfW * kotlin.math.sin(theta),
        // ×2 because max|sinθ·cosθ| is 0.5 — without it the figure fills only
        // half the height it is given and reads as a flat squiggle.
        y = size.height / 2f + halfH * 2f * kotlin.math.sin(theta) * kotlin.math.cos(theta),
    )
}
