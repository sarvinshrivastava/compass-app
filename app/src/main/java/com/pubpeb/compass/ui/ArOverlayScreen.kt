package com.pubpeb.compass.ui

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.Constants
import com.pubpeb.compass.util.bearingToCardinal
import com.pubpeb.compass.util.normalizedHeading
import com.pubpeb.compass.util.shortestArcDelta
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt

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


// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Fullscreen AR overlay: live camera preview behind floating cardinal-direction
 * labels, placed by heading *and* by where the device is pointing vertically.
 *
 * @param azimuth      Deadbanded display heading — drives the numeric HUD only, so
 *                     the number here is the same number the flat compass shows for
 *                     the same posture instead of trailing it by the AR filter.
 * @param arAzimuth    Heavily low-pass-filtered heading (CompassViewModel.arAzimuth).
 *                     Drives label *positions* only, where the extra smoothing is
 *                     what keeps the labels from vibrating over the scene.
 * @param cameraElevationDeg Signed elevation of the rear-camera axis above the
 *                     horizon (CompassViewModel.cameraElevation), used to place labels on the
 *                     horizon rather than on a fixed screen line.
 * @param onExitAr     Called when the user leaves AR — the close button or system back.
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
    arAzimuth: Float,
    cameraElevationDeg: Float,
    onExitAr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // AR is a mode, not a screen, so the system back gesture must leave the mode.
    // Without this, back fell through to the activity and quit the app from what
    // the user reads as a sub-view. Registered at the root so it also covers the
    // permission states below — a denied prompt must be escapable the same way.
    BackHandler(enabled = true) { onExitAr() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted ->
                ArContent(
                    azimuth      = azimuth,
                    arAzimuth    = arAzimuth,
                    cameraElevationDeg = cameraElevationDeg,
                    onExitAr     = onExitAr,
                )

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

// ── AR content (camera + overlay + chrome) ────────────────────────────────────

@Composable
private fun ArContent(
    azimuth: Float,
    arAzimuth: Float,
    cameraElevationDeg: Float,
    onExitAr: () -> Unit,
) {
    var fovHalfDeg by remember { mutableFloatStateOf(Constants.Ar.DEFAULT_FOV_HALF_DEG) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Leaving AR must not leave the torch burning behind the compass rose.
    DisposableEffect(Unit) {
        onDispose { camera?.cameraControl?.enableTorch(false) }
    }

    // The torch can be turned off by the system (thermal throttling, another app
    // grabbing the camera), so the button follows the camera's real state rather
    // than a local guess about it.
    camera?.let { cam ->
        DisposableEffect(cam) {
            val observer = Observer<Int> { state -> isTorchOn = state == TorchState.ON }
            cam.cameraInfo.torchState.observeForever(observer)
            onDispose { cam.cameraInfo.torchState.removeObserver(observer) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 — live camera, edge to edge. Insets deliberately not applied:
        // the preview should fill the display, cutout and all.
        CameraPreview(
            onCameraBound = { camera = it },
            onFovMeasured = { measuredHalfFov -> fovHalfDeg = measuredHalfFov },
            modifier      = Modifier.fillMaxSize(),
        )

        // Layer 2 — cardinal labels. Also un-inset: the FOV → screen mapping is
        // defined against the full preview viewport, so shrinking this box would
        // put every label at the wrong angle.
        CardinalOverlay(
            azimuth      = arAzimuth,
            cameraElevationDeg = cameraElevationDeg,
            fovHalfDeg   = fovHalfDeg,
            modifier     = Modifier.fillMaxSize(),
        )

        // Layer 3 — chrome. This is the only layer that gets inset, so the HUD and
        // buttons clear the camera cutout and gesture bar on real hardware while
        // the scene behind them stays full-bleed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ExitArButton(
                onExitAr = onExitAr,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )

            ArHeadingHud(
                azimuth  = azimuth,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )

            // Torch only exists if a camera actually bound.
            camera?.let { cam ->
                TorchButton(
                    isTorchOn = isTorchOn,
                    onToggle  = { cam.cameraControl.enableTorch(!isTorchOn) },
                    modifier  = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}

// ── Exit control ──────────────────────────────────────────────────────────────

/**
 * Leaves AR mode. Top-left and an X, because that is where and what a "close this
 * mode" control is everywhere else on the platform — the old compass glyph in the
 * top right read as "navigate to the compass view", not "get out of here".
 *
 * Sits on the near-opaque `chipBg` ground so it stays legible against a bright
 * sky or a dark interior alike.
 */
@Composable
private fun ExitArButton(
    onExitAr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = appColors
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.chipBg, CircleShape)
            .clickable(onClick = onExitAr)
            .semantics {
                contentDescription = "Exit AR view"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.Close,
            contentDescription = null,
            tint               = c.textPrimary,
            modifier           = Modifier.size(24.dp),
        )
    }
}

// ── AR heading HUD ────────────────────────────────────────────────────────────

/**
 * Compact degree + cardinal readout pinned to the top-centre of the AR view.
 *
 * Fed from the deadbanded display heading, *not* the AR positioning filter: the
 * filtered value trails by ~400 ms, so toggling between the flat screen and AR
 * used to show two different numbers for one physical heading.
 *
 * Layout:  "247°   SW"  — degree in monospace (left), cardinal in regular (right)
 */
@Composable
private fun ArHeadingHud(
    azimuth: Float,
    modifier: Modifier = Modifier,
) {
    val c = appColors

    // 359.7° rounds to 360, which is not a heading — fold it back to 0.
    val degrees  = normalizedHeading(azimuth)
    val cardinal = bearingToCardinal(degrees.toFloat())

    // Screen readers should hear the heading change, but only when the number a
    // sighted user sees actually changes — keyed on the rounded degree so sensor
    // noise below the deadband never re-announces the same reading.
    val spokenHeading = remember(degrees) { "Heading $degrees degrees, $cardinal" }

    Row(
        modifier = modifier
            .background(c.chipBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenHeading
                liveRegion         = LiveRegionMode.Polite
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Degree number — monospace so width doesn't jump as digits change
        Text(
            text       = "$degrees°",
            color      = c.textPrimary,
            fontSize   = 36.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            lineHeight = 36.sp,
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            text          = cardinal,
            color         = if (cardinal == "N") c.northLabel else c.textSecondary,
            fontSize      = 18.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
    }
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
                        onFovMeasured(Constants.Ar.DEFAULT_FOV_HALF_DEG)
                    }
                } catch (e: Exception) {
                    // Emulator or camera-less device: preview blank, labels still work
                    Log.w("ArOverlay", "Camera bind failed", e)
                    onFovMeasured(Constants.Ar.DEFAULT_FOV_HALF_DEG)
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
 * Positions floating cardinal direction labels across the screen from [azimuth],
 * [cameraElevationDeg] and the measured camera [fovHalfDeg].
 *
 * Horizontal:
 *   delta  = shortestArc(dirAngle − azimuth)    (−180, +180]
 *   fracX  = delta / fovHalfDeg                  (−1 = left edge, +1 = right edge)
 *   xPx    = fracX × (viewportWidth / 2)
 *
 * Vertical: the labels mark the horizon, so they must move with it. The vertical
 * half-FOV follows from the measured horizontal one and the viewport aspect ratio
 * (the preview is FILL_CENTER, so degrees-per-pixel is the same on both axes),
 * and the camera's elevation above the horizon maps through it to a screen offset.
 * Pinning all eight labels to one fixed line, as this did before, broke the exact
 * illusion the mode exists for.
 *
 * Labels outside [Constants.Ar.CULL_MARGIN] × the viewport half-extent are culled so they slide
 * in gracefully from the edges rather than popping into view.
 */
@Composable
private fun CardinalOverlay(
    azimuth: Float,
    cameraElevationDeg: Float,
    fovHalfDeg: Float,
    modifier: Modifier = Modifier,
) {
    // Shared with CompassRose — prevents wrap-around jumps across 0°/360°.
    val continuousAzimuth = rememberContinuousAngle(azimuth)

    // The arAzimuth flow already has a heavy LPF applied in the ViewModel, so
    // we use Spring.DampingRatioNoBouncy + StiffnessMediumLow here for a smooth
    // but not over-damped visual transition. The heavy ViewModel filter is the
    // primary stability mechanism; this animation spec handles only render-frame
    // smoothing between StateFlow emissions.
    val animatedAzimuth by animateFloatAsState(
        targetValue   = continuousAzimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "arAzimuth",
    )

    // Guard against degenerate FOV (shouldn't happen but prevents division by zero)
    val safeFovHalfDeg = if (fovHalfDeg > 1f) fovHalfDeg else Constants.Ar.DEFAULT_FOV_HALF_DEG

    BoxWithConstraints(modifier = modifier) {
        val density      = LocalDensity.current
        val widthPx      = with(density) { maxWidth.toPx() }
        val heightPx     = with(density) { maxHeight.toPx() }
        val halfWidthPx  = widthPx / 2f
        val halfHeightPx = heightPx / 2f

        // Vertical half-FOV from the horizontal one and the viewport aspect ratio.
        // Clamped because a zero-width viewport mid-layout would otherwise send
        // this to infinity and park every label off-screen.
        val vFovHalfDeg = if (widthPx > 0f) {
            (safeFovHalfDeg * (heightPx / widthPx)).coerceIn(Constants.Ar.V_FOV_HALF_MIN_DEG, Constants.Ar.V_FOV_HALF_MAX_DEG)
        } else {
            safeFovHalfDeg
        }

        // Screen Y grows downward, so a positive elevation (camera aimed up) gives
        // a positive offset — raising the phone pushes the horizon, and the labels
        // on it, down the screen.
        val fracY = (cameraElevationDeg / vFovHalfDeg).coerceIn(-1.5f, 1.5f)
        val animFracY by animateFloatAsState(
            targetValue   = fracY,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow,
            ),
            label = "labelHorizon",
        )
        val yOffsetPx = (animFracY * halfHeightPx + Constants.Ar.HORIZON_BIAS_FRACTION * halfHeightPx).roundToInt()

        for (direction in CARDINALS) {
            // Signed angular offset from screen centre: (−180, +180]
            val delta180 = shortestArcDelta(direction.angleDeg, animatedAzimuth)

            // Normalise to screen fraction using the real camera FOV:
            //   fracX = −1 means the direction is exactly at the left viewport edge
            //   fracX = +1 means it's exactly at the right viewport edge
            val fracX = delta180 / safeFovHalfDeg

            if (abs(fracX) > Constants.Ar.CULL_MARGIN) continue

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
                    .offset { IntOffset(xOffsetPx, yOffsetPx) },
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
    val c = appColors

    val textColor = when {
        isNorth    -> c.northLabel
        isCardinal -> c.textPrimary
        else       -> c.textSecondary
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
            // Near-opaque chip ground: scene brightness behind a label is
            // unpredictable, and a translucent ground made these unreadable
            // against a bright sky.
            .background(c.chipBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ── Torch button ─────────────────────────────────────────────────────────────

/**
 * One fixed toggle, bottom-right, one gesture: tap.
 *
 * It replaced a floating bubble with long-press-drag, snap-to-edge and a bespoke
 * vertical brightness slider — four invented interactions on a single control, and
 * the slider only existed on API 35+, so most users got a button that behaved
 * unlike its own affordance. Torch brightness is not worth that.
 *
 * On-state is signalled with contrast weight rather than red: red belongs to
 * north and to nothing else in this app.
 */
@Composable
private fun TorchButton(
    isTorchOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = appColors

    val glowAlpha by animateFloatAsState(
        targetValue   = if (isTorchOn) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label         = "torchGlow",
    )
    val borderColor = if (isTorchOn) c.textPrimary else c.textSecondary.copy(alpha = 0.5f)
    val iconTint    = if (isTorchOn) c.textPrimary else c.textSecondary

    Box(
        modifier = modifier
            .size(56.dp)
            .then(
                if (glowAlpha > 0f) {
                    Modifier.drawBehind {
                        drawCircle(
                            color  = c.textPrimary.copy(alpha = 0.22f * glowAlpha),
                            radius = size.minDimension,
                        )
                    }
                } else Modifier
            )
            .clip(CircleShape)
            .background(color = c.chipBg, shape = CircleShape)
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
            imageVector        = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(26.dp),
        )
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
    val c = appColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgDeep)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Same close affordance as the AR view — the user is never trapped on the
        // permission screen, by button or by back gesture.
        ExitArButton(
            onExitAr = onExitAr,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = title,
                color      = c.textPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text       = body,
                color      = c.textSecondary,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                colors  = ButtonDefaults.buttonColors(containerColor = c.buttonBg),
                shape   = RoundedCornerShape(10.dp),
            ) {
                Text(text = buttonLabel, color = c.textPrimary, fontSize = 14.sp)
            }
        }
    }
}
