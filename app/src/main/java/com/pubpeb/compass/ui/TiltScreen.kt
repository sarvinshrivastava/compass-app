package com.pubpeb.compass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.viewmodel.CompassViewModel
import kotlin.math.roundToInt

/**
 * Full-screen tilt/levelling screen (page 1 in the pager).
 *
 * The [BubbleLevelIndicator] *is* the screen — there is no section header,
 * because a full-bleed spirit level is not ambiguous about what it is, and the
 * label existed only to fill the space above the circle.
 *
 * The numeric readouts sit below it as detail, not as the answer: the bubble's
 * position already tells you which way to move the phone, which two signed
 * integers do not.
 */
@Composable
fun TiltScreen(
    viewModel: CompassViewModel,
) {
    val colors = appColors
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val roll  by viewModel.roll.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDeep)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BubbleLevelIndicator(
            pitchDegrees = pitch,
            rollDegrees  = roll,
            modifier     = Modifier.size(260.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AxisReadout(label = "PITCH", value = pitch)
            Spacer(modifier = Modifier.width(48.dp))
            AxisReadout(label = "ROLL",  value = roll)
        }
    }
}

@Composable
private fun AxisReadout(label: String, value: Float) {
    val colors = appColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${value.roundToInt()}°",
            color = colors.textSecondary,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
        )
    }
}
