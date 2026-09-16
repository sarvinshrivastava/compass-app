package com.pubpeb.compass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pubpeb.compass.ui.theme.appColors

/**
 * Why true north needs a location, asked before the system asks.
 *
 * ### Why this exists
 * The mode tag used to fire `launchPermissionRequest()` directly. A fresh user
 * therefore met a bare system location prompt, triggered by tapping three
 * letters they had no reason to understand, for a benefit nothing had
 * explained. That is the highest-friction possible moment to ask, and a
 * decline there spends the one request Android will ever grant before any
 * trust has been built.
 *
 * ### Why it is not shown on every toggle
 * Once permission is granted, switching modes is a plain toggle with no dialog.
 * This is a consent step, not a confirmation — and this app does not confirm
 * things. It reappears only when the ask would otherwise be cold again, which
 * is once per denial cycle rather than once ever: somebody who tapped "Not now"
 * a week ago still deserves the context before a system interrupt.
 */
@Composable
fun NorthModeDialog(
    state: NorthModeAvailability,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = appColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.bgCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "TRUE NORTH",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                when (state) {
                    NorthModeAvailability.NEVER_ASKED -> {
                        Body(
                            "Your compass points at magnetic north — where the Earth's " +
                                "field actually pulls — which is a few degrees off from " +
                                "true north, the direction maps use."
                        )
                        Body(
                            "To correct for that we need a rough idea of where you are. " +
                                "Your phone works out the correction and keeps only that " +
                                "number: the angle, never your position, and nothing that " +
                                "leaves the phone."
                        )
                    }

                    NorthModeAvailability.DECLINED -> {
                        Body(
                            "Still needs a rough location to work out the correction. " +
                                "Only the angle is kept — never your position."
                        )
                    }

                    NorthModeAvailability.BLOCKED -> {
                        Body(
                            "Location access is off for this app, so true north is not " +
                                "available. Magnetic north keeps working normally either way."
                        )
                        Body("You can turn it back on in system settings.")
                    }

                    // Granted never opens this dialog — switching is a plain toggle.
                    NorthModeAvailability.GRANTED -> Unit
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Not now", color = c.textSecondary, fontSize = 14.sp)
                    }
                    when (state) {
                        NorthModeAvailability.BLOCKED -> TextButton(onClick = onOpenSettings) {
                            Text(text = "Open settings", color = c.textPrimary, fontSize = 14.sp)
                        }
                        // No action offered when blocked-by-nothing: the caller
                        // only opens this dialog in states where one applies.
                        else -> TextButton(onClick = onAllow) {
                            Text(text = "Allow location", color = c.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    val c = appColors
    Text(text = text, color = c.textPrimary, fontSize = 15.sp, lineHeight = 21.sp)
}

/**
 * Whether true north can be reached, and if not, why.
 *
 * Four states rather than a boolean because each needs different copy and a
 * different action — and because "denied for good" is invisible to Android's
 * own API (it reports the same not-granted/no-rationale pair as "never asked"),
 * which is what `util/PermissionAskLog` exists to disambiguate.
 */
enum class NorthModeAvailability {
    /** Permission held; switching is a plain toggle. */
    GRANTED,

    /** Never asked. Explain fully, then ask. */
    NEVER_ASKED,

    /** Asked and declined, but Android will still show the prompt. */
    DECLINED,

    /** Denied for good. The in-app prompt is spent; only settings remain. */
    BLOCKED,
}
