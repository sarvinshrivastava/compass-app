package com.pubpeb.mycompass.ui

import android.Manifest
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
import com.pubpeb.mycompass.viewmodel.CompassViewModel
import kotlin.math.roundToInt

// ── Colour constants ──────────────────────────────────────────────────────────
private val BgDeep        = Color(0xFF0D0F14)
private val BgCard        = Color(0xFF161A22)
private val TextPrimary   = Color(0xFFCCD6F6)
private val TextSecondary = Color(0xFF8090B0)
private val AccentGreen   = Color(0xFF4CAF80)
private val ButtonBg      = Color(0xFF1E2230)

/**
 * Root compass screen — handles the location permission flow then delegates to
 * [CompassContent].
 *
 * // TODO Phase 2 – ARCore hook:
 * //   Add an AR-toggle button here that navigates to the ARCore-backed composable
 * //   when `viewModel.arSessionAvailable` is true and the camera permission is granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel = viewModel(),
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
                CompassContent(viewModel = viewModel)
            locationPermission.status.shouldShowRationale ->
                PermissionRationale(onRequest = { locationPermission.launchPermissionRequest() })
            else ->
                PermissionRequest(onRequest = { locationPermission.launchPermissionRequest() })
        }
    }
}

// ── Main compass UI ───────────────────────────────────────────────────────────

@Composable
private fun CompassContent(viewModel: CompassViewModel) {
    val azimuth     by viewModel.azimuth.collectAsStateWithLifecycle()
    val trueNorth   by viewModel.trueNorth.collectAsStateWithLifecycle()
    val declination by viewModel.declination.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // Settings dialog overlay
    if (showSettings) {
        SettingsDialog(
            trueNorth = trueNorth,
            declination = declination,
            onToggleTrueNorth = viewModel::toggleTrueNorth,
            onDismiss = { showSettings = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Top bar: settings icon only (right-aligned) ───────────────────
        TopInfoBar(onSettingsClick = { showSettings = true })

        // ── Heading readout: centred, just above the rose ─────────────────
        HeadingReadout(azimuth = azimuth)

        // ── Compass rose (fills remaining horizontal space) ───────────────
        Box(contentAlignment = Alignment.Center) {
            CompassRose(
                azimuth = azimuth,
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
private fun TopInfoBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
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
            text = bearingLabel(azimuth),
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun bearingLabel(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "NORTH"
    azimuth < 67.5f  -> "NE"
    azimuth < 112.5f -> "EAST"
    azimuth < 157.5f -> "SE"
    azimuth < 202.5f -> "SOUTH"
    azimuth < 247.5f -> "SW"
    azimuth < 292.5f -> "WEST"
    else             -> "NW"
}
