package com.pubpeb.mycompass.ui

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ── Colour palette (matches existing screens) ─────────────────────────────────
private val BgDeep          = Color(0xFF0D0F14)
private val TextPrimary     = Color(0xFFCCD6F6)
private val TextSecondary   = Color(0xFF8090B0)
private val AccentRed       = Color(0xFFE63946)
private val LabelBackground = Color(0xCC0D0F14)   // BgDeep at 80% alpha

// ── Cardinal direction data ───────────────────────────────────────────────────
private data class CardinalDir(val angleDeg: Float, val label: String)

private val CARDINALS = listOf(
    CardinalDir(  0f, "N"),
    CardinalDir( 45f, "NE"),
    CardinalDir( 90f, "E"),
    CardinalDir(135f, "SE"),
    CardinalDir(180f, "S"),
    CardinalDir(225f, "SW"),
    CardinalDir(270f, "W"),
    CardinalDir(315f, "NW"),
)

/**
 * Fallback half-FOV used before the camera sensor data is available, or if
 * the query fails. 30° (60° total) is a reasonable estimate for a typical
 * phone rear camera in portrait orientation.
 */
private const val DEFAULT_FOV_HALF_DEG = 30f

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Fullscreen AR overlay: live camera preview behind floating cardinal-direction
 * labels positioned by [azimuth].
 *
 * @param azimuth  Heavily low-pass-filtered heading (from CompassViewModel.arAzimuth)
 *                 for stable label positioning. Do **not** pass the raw azimuth here.
 * @param onExitAr Called when the user taps the top-right back button.
 *
 * - Requests CAMERA permission on first show; shows a graceful prompt if denied.
 * - If camera hardware is unavailable the preview stays blank but labels still
 *   animate correctly (sensor-only fallback).
 * - The actual horizontal FOV is queried from CameraX / Camera2 and used to
 *   correctly map bearing offsets to screen positions. Falls back to 60° total
 *   if the query fails.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ArOverlayScreen(
    azimuth: Float,
    onExitAr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted ->
                ArContent(azimuth = azimuth, onExitAr = onExitAr)

            cameraPermission.status.shouldShowRationale ->
                CameraPermissionLayout(
                    title       = "Camera Needed for AR",
                    body        = "Camera access is required to show the AR compass overlay. " +
                                  "Without it the flat compass rose is still available.",
                    buttonLabel = "Allow Camera",
                    onRequest   = { cameraPermission.launchPermissionRequest() },
                    onExitAr    = onExitAr,
                )

            else ->
                CameraPermissionLayout(
                    title       = "Camera Access",
                    body        = "The AR overlay uses your camera to show cardinal directions " +
                                  "over the live scene. Your camera feed is never stored or transmitted.",
                    buttonLabel = "Grant Camera",
                    onRequest   = { cameraPermission.launchPermissionRequest() },
                    onExitAr    = onExitAr,
                )
        }
    }
}

// ── AR content (camera + overlay + exit button) ───────────────────────────────

@Composable
private fun ArContent(
    azimuth: Float,
    onExitAr: () -> Unit,
) {
    // fovHalfDeg is shared mutable state written by CameraPreview (once the camera
    // binds and we can query sensor dimensions) and read by CardinalOverlay.
    // mutableFloatStateOf avoids unnecessary recompositions vs. mutableStateOf<Float>.
    val fovHalfDeg = remember { mutableFloatStateOf(DEFAULT_FOV_HALF_DEG) }

    // Hoisted Camera object — populated once CameraPreview binds successfully.
    // Exposed here for downstream use (e.g. torch control in Task 3).
    var camera by remember { mutableStateOf<Camera?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 — live camera background; also queries FOV and writes fovHalfDeg
        CameraPreview(
            onFovMeasured  = { measuredHalfFov -> fovHalfDeg.floatValue = measuredHalfFov },
            onCameraBound  = { camera = it },
            modifier       = Modifier.fillMaxSize(),
        )

        // Layer 2 — cardinal direction labels (FOV-aware positioning)
        CardinalOverlay(
            azimuth    = azimuth,
            fovHalfDeg = fovHalfDeg.floatValue,
            modifier   = Modifier.fillMaxSize(),
        )

        // Layer 3 — exit button (top-right, consistent with TopInfoBar placement)
        IconButton(
            onClick  = onExitAr,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Explore,
                contentDescription = "Back to compass",
                tint               = TextPrimary,
            )
        }

        // Layer 4 — degree + cardinal HUD (top-centre, same vertical zone as the
        // HeadingReadout on the flat compass screen — just below the icon row).
        ArHeadingHud(
            azimuth  = azimuth,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),   // clears the 48dp IconButton + 8dp top padding
        )

        // Layer 5 — torch bubble (added in Task 3)
    }
}

// ── AR heading HUD ────────────────────────────────────────────────────────────

/**
 * Compact degree + cardinal readout pinned to the top-centre of the AR view,
 * mirroring the position of [HeadingReadout] on the flat compass screen — just
 * below the icon row so the heading is always visible without covering the scene.
 *
 * Uses the same semi-transparent dark chip style as [CardinalLabelChip] so it
 * blends naturally over the camera feed without obscuring the scene.
 *
 * Layout:  "247°   SW"  — degree in monospace (left), cardinal in regular (right)
 */
@Composable
private fun ArHeadingHud(
    azimuth: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(LabelBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Degree number — monospace so width doesn't jump as digits change
        Text(
            text       = "${azimuth.roundToInt()}°",
            color      = TextPrimary,
            fontSize   = 36.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            lineHeight = 36.sp,
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        // Cardinal label — accent colour for N, primary for others
        val cardinal = arCardinalLabel(azimuth)
        Text(
            text       = cardinal,
            color      = if (cardinal == "N") AccentRed else TextSecondary,
            fontSize   = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
    }
}

/** Cardinal abbreviation for the AR HUD (uses same thresholds as the rest of the app). */
private fun arCardinalLabel(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "N"
    azimuth < 67.5f  -> "NE"
    azimuth < 112.5f -> "E"
    azimuth < 157.5f -> "SE"
    azimuth < 202.5f -> "S"
    azimuth < 247.5f -> "SW"
    azimuth < 292.5f -> "W"
    else             -> "NW"
}

// ── CameraX preview + FOV query ───────────────────────────────────────────────

/**
 * Renders the camera preview via CameraX and, once the camera is bound, queries
 * the actual horizontal field-of-view from Camera2 characteristics and reports
 * it via [onFovMeasured]. The bound [Camera] object is delivered to [onCameraBound]
 * for use by the caller (e.g. torch control).
 *
 * Camera binding is performed inside a [DisposableEffect] keyed on [lifecycleOwner]
 * so it runs exactly once per composition (not on every recomposition) and the
 * provider is unbound on disposal.
 *
 * FOV computation:
 *   HFOV = 2 * atan(sensorWidth / (2 * focalLength))   (in radians → degrees)
 *
 * Both `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and `SENSOR_INFO_PHYSICAL_SIZE` are
 * reported by the Camera2 HAL and are stable across frames. We take the first
 * focal length from the list (the widest angle, which is what the Preview use-case
 * defaults to).
 *
 * For a portrait-oriented device the relevant sensor dimension is the **width**
 * (horizontal in landscape, but that's what maps to the horizontal screen axis in
 * portrait). `SizeF.width` from `SENSOR_INFO_PHYSICAL_SIZE` gives this correctly.
 *
 * @param onFovMeasured  Called once with the measured half-FOV in degrees.
 *                       The [ArContent] caller stores this in a remembered state.
 * @param onCameraBound  Called once with the bound [Camera] object so the caller
 *                       can hoist it for downstream use (e.g. torch control).
 */
@Composable
private fun CameraPreview(
    onFovMeasured: (halfFovDeg: Float) -> Unit,
    onCameraBound: (Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create PreviewView once; stable reference passed to AndroidView factory.
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind the camera exactly once per lifecycle owner; unbind on disposal.
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    val preview  = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )

                    // Deliver bound Camera to caller for hoisting.
                    onCameraBound(camera)

                    // ── Query actual horizontal FOV via Camera2 interop ────────
                    // Camera2CameraInfo gives access to CameraCharacteristics.
                    val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
                    val focalLengths = cam2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    )
                    val sensorSize = cam2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
                    )

                    if (focalLengths != null && focalLengths.isNotEmpty() &&
                        sensorSize != null) {
                        val focalLength  = focalLengths[0]       // mm, shortest (widest)
                        // For portrait mode the camera sensor is physically landscape.
                        // The horizontal axis of the portrait screen maps to the
                        // narrower (height) dimension of the landscape sensor.
                        // We use sensorWidth here because:
                        //   - PreviewView fills the screen in FILL_CENTER mode
                        //   - The camera is rotated 90° in portrait, so the sensor
                        //     height maps to screen width
                        // Use the smaller of width/height to match what's visible
                        // in portrait (sensor height = physical narrower side).
                        val portraitSensorWidth = minOf(sensorSize.width, sensorSize.height)
                        val hfovRad  = 2.0 * atan((portraitSensorWidth / (2.0 * focalLength)))
                        val halfFovDeg = Math.toDegrees(hfovRad).toFloat() / 2f
                        Log.d(
                            "ArOverlay",
                            "Camera HFOV: ${Math.toDegrees(hfovRad).toInt()}° " +
                            "(focal=${focalLength}mm, sensorW=${portraitSensorWidth}mm)"
                        )
                        onFovMeasured(halfFovDeg)
                    } else {
                        Log.w("ArOverlay", "Could not read camera characteristics — using default FOV")
                        onFovMeasured(DEFAULT_FOV_HALF_DEG)
                    }
                } catch (e: Exception) {
                    // Emulator or camera-less device: preview blank, labels still work
                    Log.w("ArOverlay", "Camera bind failed: ${e.message}")
                    onFovMeasured(DEFAULT_FOV_HALF_DEG)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) { }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

// ── Cardinal label overlay ────────────────────────────────────────────────────

/**
 * Positions floating cardinal direction labels across the screen based on [azimuth]
 * and the measured camera [fovHalfDeg].
 *
 * Label X position formula:
 *   delta  = shortestArc(dirAngle − azimuth)    (−180, +180]
 *   fracX  = delta / fovHalfDeg                  (−1 = left edge, +1 = right edge)
 *   xPx    = fracX × (screenWidth / 2)
 *
 * Labels outside ±1.3 × screen half-width are culled to let them slide in
 * gracefully from the edges rather than popping into view.
 */
@Composable
private fun CardinalOverlay(
    azimuth: Float,
    fovHalfDeg: Float,
    modifier: Modifier = Modifier,
) {
    // Shortest-arc accumulator — same technique as CompassRose.kt — prevents
    // wrap-around jumps when azimuth crosses 0°/360°.
    val continuousAzimuth = remember { mutableFloatStateOf(azimuth) }
    val prevAzimuth       = remember { mutableFloatStateOf(azimuth) }

    val delta = ((azimuth - prevAzimuth.floatValue + 540f) % 360f) - 180f
    continuousAzimuth.floatValue += delta
    prevAzimuth.floatValue = azimuth

    // The arAzimuth flow already has a heavy LPF applied in the ViewModel, so
    // we use Spring.DampingRatioNoBouncy + StiffnessMediumLow here for a smooth
    // but not over-damped visual transition. The heavy ViewModel filter is the
    // primary stability mechanism; this animation spec handles only render-frame
    // smoothing between StateFlow emissions.
    val animatedAzimuth by animateFloatAsState(
        targetValue   = continuousAzimuth.floatValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "arAzimuth",
    )

    // Guard against degenerate FOV (shouldn't happen but prevents division by zero)
    val safeFovHalfDeg = if (fovHalfDeg > 1f) fovHalfDeg else DEFAULT_FOV_HALF_DEG

    BoxWithConstraints(modifier = modifier) {
        val density      = LocalDensity.current
        val halfWidthPx  = with(density) { maxWidth.toPx()  / 2f }
        val halfHeightPx = with(density) { maxHeight.toPx() / 2f }

        // Place labels 30% above the vertical centre of the screen
        val verticalOffsetPx = (-halfHeightPx * 0.30f).roundToInt()

        for (direction in CARDINALS) {
            // Signed angular offset from screen centre: (−180, +180]
            val delta180 = ((direction.angleDeg - animatedAzimuth + 540f) % 360f) - 180f

            // Normalise to screen fraction using the real camera FOV:
            //   fracX = −1 means the direction is exactly at the left viewport edge
            //   fracX = +1 means it's exactly at the right viewport edge
            val fracX = delta180 / safeFovHalfDeg

            // Cull labels that are more than 1.3× past the screen edge.
            // The 0.3× margin means labels start rendering slightly off-screen
            // and slide in, so they never pop into view at the exact edge.
            if (abs(fracX) > 1.3f) continue

            // Each label has its own animated X fraction for smooth individual sliding.
            // Correct inside a for-loop: CARDINALS is a fixed-size, fixed-order list,
            // so Compose assigns the same slot table entry on every recomposition.
            val animFracX by animateFloatAsState(
                targetValue   = fracX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMediumLow,
                ),
                label = "label_${direction.label}",
            )

            val xOffsetPx = (animFracX * halfWidthPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(xOffsetPx, verticalOffsetPx) },
            ) {
                CardinalLabelChip(
                    label      = direction.label,
                    isNorth    = direction.label == "N",
                    isCardinal = direction.angleDeg % 90f == 0f,
                )
            }
        }
    }
}

// ── Individual label chip ─────────────────────────────────────────────────────

@Composable
private fun CardinalLabelChip(
    label: String,
    isNorth: Boolean,
    isCardinal: Boolean,
) {
    val textColor = when {
        isNorth    -> AccentRed
        isCardinal -> TextPrimary
        else       -> TextSecondary
    }
    val fontSize = when {
        isNorth    -> 22.sp
        isCardinal -> 18.sp
        else       -> 14.sp
    }
    val fontWeight = if (isCardinal) FontWeight.SemiBold else FontWeight.Normal

    Text(
        text       = label,
        color      = textColor,
        fontSize   = fontSize,
        fontWeight = fontWeight,
        modifier   = Modifier
            .background(LabelBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ── Torch button ─────────────────────────────────────────────────────────────

@Composable
private fun TorchButton(
    isTorchOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glowAlpha by animateFloatAsState(
        targetValue   = if (isTorchOn) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label         = "torchGlow",
    )
    val borderColor = if (isTorchOn) AccentRed else Color(0x26CCD6F6)
    val iconTint    = if (isTorchOn) AccentRed else TextSecondary

    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (glowAlpha > 0f) {
                    Modifier.drawBehind {
                        drawCircle(
                            color  = AccentRed.copy(alpha = 0.25f * glowAlpha),
                            radius = size.minDimension,
                        )
                    }
                } else Modifier
            )
            .background(
                color = if (isTorchOn) Color(0x33E63946) else Color(0x14CCD6F6),
                shape = CircleShape,
            )
            .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
            .clickable(
                onClick = onToggle,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            )
            .semantics {
                contentDescription = if (isTorchOn) "Turn off flashlight" else "Turn on flashlight"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.FlashOn,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(24.dp),
        )
    }
}

// ── Brightness slider ────────────────────────────────────────────────────────

@Composable
private fun BrightnessSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeightDp = 70.dp
    val thumbRadiusDp = 8.dp
    val trackWidthDp  = 6.dp

    Box(
        modifier = modifier
            .width(trackWidthDp + thumbRadiusDp * 2 + 8.dp)
            .height(trackHeightDp + thumbRadiusDp * 2)
            .semantics {
                stateDescription = "Brightness ${(fraction * 100).roundToInt()}%"
            }
            .pointerInput(Unit) {
                val trackHeightPx = trackHeightDp.toPx()
                val thumbRadiusPx = thumbRadiusDp.toPx()
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val yInTrack = (change.position.y - thumbRadiusPx).coerceIn(0f, trackHeightPx)
                    val newFraction = 1f - (yInTrack / trackHeightPx)
                    onFractionChange(newFraction.coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .height(trackHeightDp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x26CCD6F6))
        )
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .height(trackHeightDp * fraction)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(TextPrimary, AccentRed),
                    )
                )
        )
        val thumbOffsetY = trackHeightDp * (1f - fraction)
        Box(
            modifier = Modifier
                .size(thumbRadiusDp * 2)
                .offset(y = thumbOffsetY)
                .align(Alignment.TopCenter)
                .background(TextPrimary, CircleShape)
                .border(2.dp, Color(0xFF0D0F14), CircleShape)
        )
    }
}

// ── Torch bubble (draggable, expandable) ─────────────────────────────────────

@Composable
private fun TorchBubble(
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    supportsBrightness: Boolean,
    brightnessFraction: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density        = LocalDensity.current
        val screenWidthPx  = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val bubbleWidthPx  = with(density) { 68.dp.toPx() }
        val bubbleHeightPx = with(density) { if (isTorchOn && supportsBrightness) 160.dp.toPx() else 68.dp.toPx() }

        val rightEdgeX = screenWidthPx - bubbleWidthPx - with(density) { 12.dp.toPx() }
        val leftEdgeX  = with(density) { 12.dp.toPx() }
        val defaultY = screenHeightPx - bubbleHeightPx - with(density) { 100.dp.toPx() }

        val offsetX = remember { Animatable(rightEdgeX) }
        var offsetY by remember { mutableFloatStateOf(defaultY) }
        val scope   = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                            }
                            offsetY = (offsetY + dragAmount.y).coerceIn(
                                0f,
                                screenHeightPx - bubbleHeightPx,
                            )
                        },
                        onDragEnd = {
                            val targetX = if (offsetX.value < screenWidthPx / 2f) leftEdgeX else rightEdgeX
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue   = targetX,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness    = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                    )
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(LabelBackground, RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        color = if (isTorchOn) Color(0x66E63946) else Color(0x26CCD6F6),
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(10.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness    = Spring.StiffnessMediumLow,
                        ),
                    ),
            ) {
                if (isTorchOn && supportsBrightness) {
                    BrightnessSlider(
                        fraction         = brightnessFraction,
                        onFractionChange = onBrightnessChange,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TorchButton(
                    isTorchOn = isTorchOn,
                    onToggle  = onToggleTorch,
                )
            }
        }
    }
}

// ── Camera permission UI ──────────────────────────────────────────────────────

@Composable
private fun CameraPermissionLayout(
    title: String,
    body: String,
    buttonLabel: String,
    onRequest: () -> Unit,
    onExitAr: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        // Back button — user is never trapped on the permission screen
        IconButton(
            onClick  = onExitAr,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Explore,
                contentDescription = "Back to compass",
                tint               = TextSecondary,
            )
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = title,
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text       = body,
                color      = TextSecondary,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A3550),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text = buttonLabel, color = TextPrimary, fontSize = 14.sp)
            }
        }
    }
}
