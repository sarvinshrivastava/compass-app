package com.pubpeb.mycompass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pubpeb.mycompass.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrackColor   = Color(0xFF2A2E38)
private val FillOk       = Color(0xFF4CAF80)    // green — device is reasonably flat
private val FillWarning  = Color(0xFFE6A817)    // amber — moderate tilt
private val FillCritical = AppColors.AccentRed  // red   — very tilted, accuracy degrades

/**
 * Horizontal bar that visualises device tilt (pitch).
 *
 * The fill colour transitions green → amber → red as tilt increases away from
 * flat (|pitch| > 15° → amber, > 35° → red).  The bar fill represents
 * normalised tilt magnitude clamped to [0, 1].
 */
@Composable
fun TiltIndicator(
    pitchDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val absTilt = abs(pitchDegrees).coerceIn(0f, 90f)
    val normalised = absTilt / 90f

    val animatedFill by animateFloatAsState(
        targetValue = normalised,
        animationSpec = tween(durationMillis = 200),
        label = "tiltFill",
    )

    val barColor = when {
        absTilt > 35f -> FillCritical
        absTilt > 15f -> FillWarning
        else          -> FillOk
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)

        // Background track
        drawRoundRect(
            color = TrackColor,
            size = size,
            cornerRadius = cornerRadius,
        )

        // Filled portion — grows from the centre outward to indicate tilt direction
        val fillWidth = size.width * animatedFill
        val startX = (size.width - fillWidth) / 2f
        if (fillWidth > 0f) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX, 0f),
                size = Size(fillWidth, size.height),
                cornerRadius = cornerRadius,
            )
        }

        // Centre mark
        drawLine(
            color = AppColors.TextSecondary,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = Stroke.HairlineWidth,
        )
    }
}
