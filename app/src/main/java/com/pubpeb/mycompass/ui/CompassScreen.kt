package com.pubpeb.mycompass.ui

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pubpeb.mycompass.util.bearingToFullLabel
import com.pubpeb.mycompass.ui.theme.AppColors
import com.pubpeb.mycompass.viewmodel.CompassViewModel
import kotlin.math.roundToInt

// ── Colour constants ──────────────────────────────────────────────────────────
private val BgDeep        = AppColors.BgDeep
private val BgCard        = AppColors.BgCard
private val TextPrimary   = AppColors.TextPrimary
private val TextSecondary = AppColors.TextSecondary
private val AccentGreen   = Color(0xFF4CAF80)    // keep — CompassScreen-specific
private val ButtonBg      = Color(0xFF1E2230)    // keep — CompassScreen-specific

/**
 * Root compass screen — handles the location permission flow then delegates to
 * [CompassContent].
 *
 * [isArMode] and [onArModeChange] are hoisted to [com.pubpeb.mycompass.ui.MainScreen]
 * so the pager can disable horizontal swipe while the camera overlay is active.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel = viewModel(),
    isArMode: Boolean = false,
    onArModeChange: (Boolean) -> Unit = {},
) {
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            viewModel.updateDeclination()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgDeep,
    ) {
        when {
            locationPermission.status.isGranted ->
                CompassContent(
                    viewModel      = viewModel,
                    isArMode       = isArMode,
                    onArModeChange = onArModeChange,
                )
            locationPermission.status.shouldShowRationale ->
                PermissionRationale(onRequest = { locationPermission.launchPermissionRequest() })
            else ->
                PermissionRequest(onRequest = { locationPermission.launchPermissionRequest() })
        }
    }
}

// ── Main compass UI ───────────────────────────────────────────────────────────

@Composable
private fun CompassContent(
    viewModel: CompassViewModel,
    isArMode: Boolean,
    onArModeChange: (Boolean) -> Unit,
) {
    val azimuth     by viewModel.azimuth.collectAsStateWithLifecycle()
    val arAzimuth   by viewModel.arAzimuth.collectAsStateWithLifecycle()
    val trueNorth   by viewModel.trueNorth.collectAsStateWithLifecycle()
    val declination by viewModel.declination.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // Settings dialog sits outside the Crossfade — it's a Dialog overlay and
    // must not be re-created when the AR/flat mode transitions.
    if (showSettings) {
        SettingsDialog(
            trueNorth         = trueNorth,
            declination       = declination,
            onToggleTrueNorth = viewModel::toggleTrueNorth,
            onDismiss         = { showSettings = false },
        )
    }

    // 300 ms crossfade between the flat compass rose and the AR camera overlay.
    Crossfade(
        targetState   = isArMode,
        animationSpec = tween(durationMillis = 300),
        label         = "arCrossfade",
    ) { arActive ->
        if (arActive) {
            ArOverlayScreen(
                azimuth  = arAzimuth,   // heavy-filtered (α=0.05) for stable AR labels
                onExitAr = { onArModeChange(false) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CompassFlatContent(
                azimuth         = azimuth,
                onSettingsClick = { showSettings = true },
                onEnterAr       = { onArModeChange(true) },
            )
        }
    }
}

// ── Flat compass layout (rose + readout + top bar) ────────────────────────────

@Composable
private fun CompassFlatContent(
    azimuth: Float,
    onSettingsClick: () -> Unit,
    onEnterAr: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Top bar: camera + settings icons (right-aligned) ──────────────
        TopInfoBar(
            onSettingsClick = onSettingsClick,
            onEnterAr       = onEnterAr,
        )

        // ── Heading readout: centred, just above the rose ─────────────────
        HeadingReadout(azimuth = azimuth)

        // ── Compass rose (fills remaining horizontal space) ───────────────
        Box(contentAlignment = Alignment.Center) {
            CompassRose(
                azimuth  = azimuth,
                modifier = Modifier
                    .fillMaxWidth()
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
    onSettingsClick: () -> Unit,
    onEnterAr: () -> Unit,
) {
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
                tint               = TextSecondary,
            )
        }
        // Settings icon
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector        = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint               = TextSecondary,
            )
        }
    }
}

// ── Heading readout (above rose) ──────────────────────────────────────────────

@Composable
private fun HeadingReadout(azimuth: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${azimuth.roundToInt()}°",
            color = TextPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            lineHeight = 52.sp,
        )
        Text(
            text = bearingToFullLabel(azimuth),
            color = TextSecondary,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        )
    }
}

// ── Settings dialog ───────────────────────────────────────────────────────────

@Composable
private fun SettingsDialog(
    trueNorth: Boolean,
    declination: Float,
    onToggleTrueNorth: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Text(
                    text = "SETTINGS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                // ── True / Magnetic north toggle ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "True North",
                            color = TextPrimary,
                            fontSize = 15.sp,
                        )
                        if (trueNorth) {
                            Text(
                                text = "Decl ${String.format("%.1f", declination)}°",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Switch(
                        checked = trueNorth,
                        onCheckedChange = { onToggleTrueNorth() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentGreen,
                            checkedTrackColor = AccentGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ButtonBg,
                        ),
                    )
                }

                // ── Close ─────────────────────────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBg),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "CLOSE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

// ── Permission screens ────────────────────────────────────────────────────────

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    PermissionLayout(
        title = "Location Access",
        body = "MyCompass uses your GPS location to compute the local magnetic declination " +
               "and show true north. Your location is never stored or transmitted.",
        buttonLabel = "Grant Location",
        onRequest = onRequest,
    )
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    PermissionLayout(
        title = "Permission Needed",
        body = "Location access is needed to correct for magnetic declination. " +
               "Without it, only magnetic north is shown.",
        buttonLabel = "Allow Location",
        onRequest = onRequest,
    )
}

@Composable
private fun PermissionLayout(
    title: String,
    body: String,
    buttonLabel: String,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = body,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3550)),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(text = buttonLabel, color = TextPrimary, fontSize = 14.sp)
        }
    }
}

