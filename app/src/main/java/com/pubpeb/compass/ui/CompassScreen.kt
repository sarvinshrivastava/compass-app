package com.pubpeb.compass.ui

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pubpeb.compass.Constants
import com.pubpeb.compass.sensor.CompassAccuracy
import com.pubpeb.compass.ui.theme.AppPalette
import com.pubpeb.compass.ui.theme.ThemeMode
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.util.bearingToFullLabel
import com.pubpeb.compass.util.normalizedHeading
import com.pubpeb.compass.viewmodel.CompassViewModel

/**
 * Root compass screen.
 *
 * **The compass is never gated on a permission.** A magnetometer heading needs
 * no consent to read, so [CompassContent] renders on the first frame; location
 * is requested only when the user taps the MAG tag, and only ever *upgrades*
 * the reading from magnetic to true north.
 *
 * [Manifest.permission.ACCESS_COARSE_LOCATION] is deliberate: declination
 * varies by roughly a degree over tens of kilometres, so a coarse fix is
 * indistinguishable from a fine one here — and reads far less alarming in the
 * system dialog, which is the difference between a grant and a denial.
 *
 * [isArMode] and [onArModeChange] are hoisted to [com.pubpeb.compass.ui.MainScreen]
 * so the pager can disable horizontal swipe while the camera overlay is active.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel = viewModel(),
    isArMode: Boolean = false,
    onArModeChange: (Boolean) -> Unit = {},
) {
    val c = appColors
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationGranted = locationPermission.status.isGranted

    // Only ever fires to fetch declination — never to unblock the UI.
    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.updateDeclination()
    }

    // Did asking for COARSE rather than FINE actually pay off in grant rate?
    // Reported once the user has been asked, not on the first composition.
    LaunchedEffect(locationPermission.status) {
        if (locationPermission.status.shouldShowRationale || locationGranted) {
            viewModel.onLocationOutcome(locationGranted)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = c.bgDeep,
    ) {
        CompassContent(
            viewModel         = viewModel,
            isArMode          = isArMode,
            onArModeChange    = onArModeChange,
            locationGranted   = locationGranted,
            onRequestLocation = { locationPermission.launchPermissionRequest() },
        )
    }
}

// ── Main compass UI ───────────────────────────────────────────────────────────

@Composable
private fun CompassContent(
    viewModel: CompassViewModel,
    isArMode: Boolean,
    onArModeChange: (Boolean) -> Unit,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
) {
    val azimuth        by viewModel.azimuth.collectAsStateWithLifecycle()
    val arAzimuth      by viewModel.arAzimuth.collectAsStateWithLifecycle()
    val cameraElev     by viewModel.cameraElevation.collectAsStateWithLifecycle()
    val trueNorth      by viewModel.trueNorth.collectAsStateWithLifecycle()
    val declination    by viewModel.declination.collectAsStateWithLifecycle()
    val hasDeclination by viewModel.hasDeclination.collectAsStateWithLifecycle()
    val accuracy       by viewModel.accuracy.collectAsStateWithLifecycle()
    val themeMode      by viewModel.themeMode.collectAsStateWithLifecycle()
    val arAutoSwitch   by viewModel.arAutoSwitch.collectAsStateWithLifecycle()

    // Sits outside the Crossfade: a Dialog is an overlay and must not be torn
    // down and rebuilt when the AR/flat transition runs.
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsDialog(
            arAutoSwitch        = arAutoSwitch,
            onArAutoSwitchChange = viewModel::setArAutoSwitch,
            onDismiss           = { showSettings = false },
        )
    }

    // Tapping the mode tag means "give me true north". If we cannot yet, that
    // means asking for location first — the toggle then applies on the next tap,
    // once a fix has produced a real declination.
    val onModeTagClick: () -> Unit = {
        if (locationGranted) viewModel.toggleTrueNorth() else onRequestLocation()
    }

    // 300 ms crossfade between the flat compass rose and the AR camera overlay.
    Crossfade(
        targetState   = isArMode,
        animationSpec = tween(durationMillis = Constants.Ui.MODE_TRANSITION_MS),
        label         = "arCrossfade",
    ) { arActive ->
        if (arActive) {
            ArOverlayScreen(
                // Deadbanded heading for the numeric HUD, so the AR readout never
                // disagrees with the flat screen's readout for the same bearing.
                azimuth            = azimuth,
                // Heavy-filtered (α=0.05) — label positions only, where the extra
                // smoothing is worth the lag.
                arAzimuth          = arAzimuth,
                cameraElevationDeg = cameraElev,
                onExitAr           = { onArModeChange(false) },
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            CompassFlatContent(
                azimuth           = azimuth,
                trueNorth         = trueNorth,
                hasDeclination    = hasDeclination,
                declination       = declination,
                accuracy          = accuracy,
                themeMode         = themeMode,
                onModeTagClick    = onModeTagClick,
                onToggleDaylight  = viewModel::toggleDaylightMode,
                onOpenSettings    = { showSettings = true },
                onEnterAr         = { onArModeChange(true) },
            )
        }
    }
}

// ── Flat compass layout (rose + readout + top bar) ────────────────────────────

@Composable
private fun CompassFlatContent(
    azimuth: Float,
    trueNorth: Boolean,
    hasDeclination: Boolean,
    declination: Float,
    accuracy: CompassAccuracy,
    themeMode: ThemeMode,
    onModeTagClick: () -> Unit,
    onToggleDaylight: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterAr: () -> Unit,
) {
    val c = appColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgDeep)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Top bar: camera + daylight toggle (right-aligned) ─────────────
        TopInfoBar(
            themeMode        = themeMode,
            onToggleDaylight = onToggleDaylight,
            onOpenSettings   = onOpenSettings,
            onEnterAr        = onEnterAr,
        )

        // ── Heading readout: the screen's hero, just above the rose ───────
        HeadingReadout(
            azimuth        = azimuth,
            trueNorth      = trueNorth,
            hasDeclination = hasDeclination,
            declination    = declination,
            accuracy       = accuracy,
            onModeTagClick = onModeTagClick,
        )

        // ── Compass rose ──────────────────────────────────────────────────
        // Capped so the rose supports the readout rather than
        // dominating it, and `weight(fill = false)` bounds its height so a
        // short or landscape viewport shrinks it instead of overflowing.
        Box(
            modifier           = Modifier.weight(1f, fill = false),
            contentAlignment   = Alignment.Center,
        ) {
            CompassRose(
                azimuth  = azimuth,
                modifier = Modifier
                    .sizeIn(maxWidth = Constants.Ui.ROSE_MAX_WIDTH)
                    .padding(8.dp),
            )
        }

        // Bottom spacer keeps the rose vertically balanced
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Top info bar ──────────────────────────────────────────────────────────────

@Composable
private fun TopInfoBar(
    themeMode: ThemeMode,
    onToggleDaylight: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterAr: () -> Unit,
) {
    val c = appColors
    val daylightOn = themeMode == ThemeMode.DAYLIGHT

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Camera icon — manually enter AR overlay mode
        IconButton(onClick = onEnterAr) {
            Icon(
                imageVector        = Icons.Filled.CameraAlt,
                contentDescription = "AR overlay",
                tint               = c.textSecondary,
            )
        }
        // Daylight mode — the outdoor high-contrast palette. Deliberately one
        // tap away, not buried in settings: it is needed exactly when the
        // screen is hardest to read.
        IconButton(onClick = onToggleDaylight) {
            Icon(
                imageVector        = if (daylightOn) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = if (daylightOn) "Leave daylight mode" else "Daylight mode",
                tint               = c.textSecondary,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector        = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint               = c.textSecondary,
            )
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

/**
 * Deliberately holds one switch.
 *
 * Everything else the user can change is reachable in one tap on the compass
 * itself: true north from the MAG/TRUE tag, daylight from the top bar. Only the
 * AR auto-switch belongs here, because it is a preference about behaviour
 * rather than a mode you flip while looking at a bearing.
 */
@Composable
private fun SettingsDialog(
    arAutoSwitch: Boolean,
    onArAutoSwitchChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = appColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.bgCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "SETTINGS",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-switch to AR",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "Raise the phone to open the camera view",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Switch(
                        checked = arAutoSwitch,
                        onCheckedChange = onArAutoSwitchChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = c.levelOk,
                            checkedTrackColor   = c.levelOk.copy(alpha = 0.35f),
                            uncheckedThumbColor = c.textSecondary,
                            uncheckedTrackColor = c.buttonBg,
                        ),
                    )
                }
            }
        }
    }
}

// ── Heading readout (above rose) ──────────────────────────────────────────────

/**
 * Degree · cardinal · mode-and-confidence — in descending order of how often
 * someone needs it.
 *
 * The third line carries the whole trust story. It stays silent while the
 * magnetometer is fine and speaks only when it is not, in place, at the size
 * it already occupies: no dialog, banner or snackbar, none of which a person
 * holding a phone at arm's length in the field will read.
 */
@Composable
private fun HeadingReadout(
    azimuth: Float,
    trueNorth: Boolean,
    hasDeclination: Boolean,
    declination: Float,
    accuracy: CompassAccuracy,
    onModeTagClick: () -> Unit,
) {
    val c = appColors

    // 359.7° rounds to 360, which is not a heading anyone reads — fold it to 0.
    val rounded    = normalizedHeading(azimuth)
    val fullLabel  = bearingToFullLabel(azimuth)
    val trueActive = trueNorth && hasDeclination
    val confidence = confidenceNote(accuracy, c)

    // Rebuilt only when the whole degree changes. Keyed on the raw azimuth this
    // fires on every sensor event and TalkBack narrates a continuous stream.
    val description = remember(rounded) {
        "Heading $rounded degrees, ${bearingToFullLabel(rounded.toFloat())}"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) {
                this.contentDescription = description
                // Polite, not Assertive: a heading change must never interrupt
                // whatever the user is already being told.
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text(
                text       = "$rounded°",
                color      = c.textPrimary,
                // Normal, not Light: hairline strokes disappear under glare,
                // which is precisely when this number matters most.
                fontSize   = 52.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                lineHeight = 52.sp,
            )
            Text(
                text          = fullLabel,
                color         = c.textPrimary,
                fontSize      = 16.sp,
                letterSpacing = 2.sp,
            )
        }

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Mode tag — the only control on this screen. sizeIn before
            // clickable so the 48.dp touch target is real, not just visual.
            Text(
                text          = if (trueActive) "TRUE" else "MAG",
                color         = c.textSecondary,
                fontSize      = 12.sp,
                letterSpacing = 1.sp,
                fontFamily    = FontFamily.Monospace,
                modifier      = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onModeTagClick)
                    .wrapContentSize(Alignment.Center),
            )
            if (trueActive) {
                Text(
                    text          = "· %.1f° DECL".format(declination),
                    color         = c.textSecondary,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp,
                    fontFamily    = FontFamily.Monospace,
                )
            }
            confidence?.let { (text, color) ->
                Text(
                    text          = text,
                    color         = color,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp,
                    fontFamily    = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Maps sensor confidence to the one line the user should see, or `null` when
 * the heading is trustworthy — silence is the "everything is fine" state, so
 * the warning colours never become background noise the eye learns to skip.
 */
private fun confidenceNote(
    accuracy: CompassAccuracy,
    c: AppPalette,
): Pair<String, Color>? = when (accuracy) {
    CompassAccuracy.HIGH, CompassAccuracy.MEDIUM -> null
    CompassAccuracy.LOW        -> "LOW ACCURACY"         to c.levelWarn
    CompassAccuracy.UNRELIABLE -> "CALIBRATE · FIGURE 8" to c.levelWarn
    CompassAccuracy.NO_SIGNAL  -> "NO SENSOR SIGNAL"     to c.levelCritical
}
