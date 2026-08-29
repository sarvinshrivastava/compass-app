package com.pubpeb.compass.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pubpeb.compass.util.shortestArcDelta

/**
 * True when the user has turned system animations off.
 *
 * Read once and remembered: this is a global the user changes in Settings, not
 * something that varies per frame. Callers swap their animation spec for
 * `snap()` when it is true — a continuously rotating compass card is a genuine
 * problem for anyone with vestibular sensitivity.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Tracks [azimuth] as a continuous, unbounded angle so an animation always
 * takes the shortest path around the circle.
 *
 * `animateFloatAsState` interpolates linearly between floats, so feeding it a
 * bearing directly makes 359° → 0° a −359° sweep: the compass card spins almost
 * a full turn backwards instead of nudging forward one degree. Accumulating the
 * shortest signed arc instead keeps the animator on the short path.
 *
 * Both the compass rose and the AR label overlay need this, and each previously
 * carried its own copy of the accumulator. Sharing it also means the
 * write-during-composition tradeoff below lives in one audited place rather than
 * two: the write is idempotent for a given [azimuth] (a repeat invocation
 * computes a zero delta, because `prev` has already caught up), which is what
 * makes it safe against a replayed composition.
 */
@Composable
fun rememberContinuousAngle(azimuth: Float): Float {
    val continuous = remember { mutableFloatStateOf(azimuth) }
    val prev = remember { mutableFloatStateOf(azimuth) }

    continuous.floatValue += shortestArcDelta(azimuth, prev.floatValue)
    prev.floatValue = azimuth

    return continuous.floatValue
}
