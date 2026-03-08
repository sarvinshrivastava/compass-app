package com.pubpeb.mycompass.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pubpeb.mycompass.viewmodel.CompassViewModel
import kotlin.math.roundToInt

private val BgDeep        = Color(0xFF0D0F14)
private val TextPrimary   = Color(0xFFCCD6F6)
private val TextSecondary = Color(0xFF8090B0)

/**
 * Full-screen tilt/levelling screen (page 1 in the pager).
 *
 * Displays a 2-D [BubbleLevelIndicator] that reflects both pitch and roll,
 * along with a numeric readout of each axis below it.
 *
 * The bubble is green when the device is level, amber when moderately tilted,
 * and red when heavily tilted (>70 % of the 45° full-deflection range).
 */
@Composable
fun TiltScreen(
    viewModel: CompassViewModel,
) {
    val pitch by viewModel.tilt.collectAsStateWithLifecycle()
    val roll  by viewModel.roll.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Section header
        Text(
            text = "LEVEL",
            color = TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2-D bubble level — set to a comfortable square size
        BubbleLevelIndicator(
            pitchDegrees = pitch,
            rollDegrees  = roll,
            modifier     = Modifier.size(260.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Numeric pitch + roll readout
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${value.roundToInt()}°",
            color = TextPrimary,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
        )
    }
}
