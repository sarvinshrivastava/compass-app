package com.pubpeb.compass.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.LevelPosture
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.viewmodel.CompassViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen tilt/levelling screen (page 1 in the pager).
 *
 * The indicator *is* the screen — there is no section header, because a
 * full-bleed spirit level is not ambiguous about what it is, and the label
 * existed only to fill the space above the circle.
 *
 * ### Two instruments, one screen
 * Which one shows is decided by how the phone is being held, because the two
 * postures ask different questions:
 *
 *  - **Flat on a surface** — two axes matter at once ("which corner is high?").
 *    [BubbleLevelIndicator], a bullseye.
 *  - **Held up against something** — one axis matters ("is this edge plumb?").
 *    [PlumbLevelIndicator], a single-axis rule.
 *
 * The bullseye alone covered only the first. In the upright posture its `normY`
 * clamps to 1 and the bubble parks against the rim, so the screen looked
 * broken exactly when someone held the phone against a door frame — the second
 * most obvious thing to do with a level app.
 *
 * The posture comes from [CompassViewModel.levelPosture], which applies
 * hysteresis; nothing here re-derives it from an angle.
 */
@Composable
fun TiltScreen(
    viewModel: CompassViewModel,
) {
    val colors = appColors
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val roll by viewModel.roll.collectAsStateWithLifecycle()
    val screenRoll by viewModel.screenRoll.collectAsStateWithLifecycle()
    val posture by viewModel.levelPosture.collectAsStateWithLifecycle()
    val hasAccelerometer by viewModel.hasAccelerometer.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDeep)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasAccelerometer) {
            MissingSensorNotice(
                headline = "No motion sensor",
                detail = "This device has no accelerometer, so it cannot measure tilt.",
            )
            return@Column
        }

        Crossfade(
            targetState = posture,
            animationSpec = tween(durationMillis = Constants.Ui.MODE_TRANSITION_MS),
            label = "levelPosture",
        ) { active ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (active) {
                    LevelPosture.FLAT -> {
                        BubbleLevelIndicator(
                            pitchDegrees = pitch,
                            rollDegrees = roll,
                            modifier = Modifier.size(260.dp),
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AxisReadout(label = "PITCH", value = pitch)
                            Spacer(modifier = Modifier.width(48.dp))
                            AxisReadout(label = "ROLL", value = roll)
                        }
                    }

                    LevelPosture.UPRIGHT -> {
                        PlumbLevelIndicator(
                            rollDegrees = screenRoll,
                            modifier = Modifier.size(260.dp),
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        AxisReadout(
                            label = "OFF PLUMB",
                            value = abs(foldToQuarterTurn(screenRoll)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shown in place of the instrument when the hardware it needs is absent.
 *
 * Saying so is the whole point: the alternative is an instrument that renders
 * perfectly and reads zero forever, which a user reasonably reports as the app
 * being broken rather than the phone lacking a part.
 */
@Composable
internal fun MissingSensorNotice(
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = headline,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = detail,
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
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
