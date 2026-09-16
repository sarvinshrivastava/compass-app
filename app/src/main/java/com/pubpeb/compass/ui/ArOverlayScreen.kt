package com.pubpeb.compass.ui

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.pubpeb.compass.util.PermissionAskLog
import com.pubpeb.compass.util.bearingToCardinal
import com.pubpeb.compass.util.bearingToSpokenLabel
import com.pubpeb.compass.util.normalizedHeading
import com.pubpeb.compass.util.openAppSettings
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
    horizonElevationDeg: Float,
    onExitAr: () -> Unit,
    modifier: Modifier = Modifier,
    lockedBearing: Float? = null,
    onToggleLock: (() -> Unit)? = null,
    onStreamingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Mirrors the persisted "we have asked" marker so that flipping it
    // recomposes this screen. The persisted value is the source of truth; this
    // is only its observable shadow for the current composition.
    var hasAsked by remember {
        mutableStateOf(PermissionAskLog.hasAsked(context, Manifest.permission.CAMERA))
    }

    val requestCamera = {
        PermissionAskLog.recordAsked(context, Manifest.permission.CAMERA)
        hasAsked = true
        cameraPermission.launchPermissionRequest()
    }

    // Denied twice on Android 11+ means denied for good: isGranted is false and
    // shouldShowRationale is ALSO false, which is indistinguishable from the
    // never-asked state without the marker above. Left conflated, this screen
    // showed first-run copy and a "Grant Camera" button that the system
    // auto-denies without ever drawing a dialog — a control that visibly does
    // nothing, forever.
    val permanentlyDenied = !cameraPermission.status.isGranted &&
        !cameraPermission.status.shouldShowRationale &&
        hasAsked

    // AR is a mode, not a screen, so the system back gesture must leave the mode.
    // Without this, back fell through to the activity and quit the app from what
    // the user reads as a sub-view. Registered at the root so it also covers the
    // permission states below — a denied prompt must be escapable the same way.
    BackHandler(enabled = true) { onExitAr() }

    // Everything except the camera-granted branch draws immediately and needs no
    // camera frame. Say so, or the caller — which holds the compass on screen
    // until this reports ready — keeps the whole overlay at alpha 0 and the
    // permission prompt is invisible underneath it. That made "tap AR with
    // camera denied" do nothing at all, with no way out but lowering the phone.
    val awaitingCamera = cameraPermission.status.isGranted
    LaunchedEffect(awaitingCamera) {
        if (!awaitingCamera) onStreamingChanged(true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            cameraPermission.status.isGranted ->
                ArContent(
                    azimuth      = azimuth,
                    arAzimuth    = arAzimuth,
                    horizonElevationDeg = horizonElevationDeg,
                    onExitAr     = onExitAr,
                    lockedBearing = lockedBearing,
                    onToggleLock  = onToggleLock,
                    onStreamingChanged = onStreamingChanged,
                )

            permanentlyDenied ->
                CameraPermissionLayout(
                    title       = "Camera Is Turned Off",
                    body        = "Camera access for this app is switched off in system " +
                                  "settings, so the AR overlay cannot start. You can turn it " +
                                  "back on from there. The flat compass works either way.",
                    buttonLabel = "Open Settings",
                    onRequest   = { context.openAppSettings() },
                    onExitAr    = onExitAr,
                )

            cameraPermission.status.shouldShowRationale ->
                CameraPermissionLayout(
                    title       = "Camera Needed for AR",
                    body        = "Camera access is required to show the AR compass overlay. " +
                                  "Without it the flat compass rose is still available.",
                    buttonLabel = "Allow Camera",
                    onRequest   = requestCamera,
                    onExitAr    = onExitAr,
                )

            else ->
                CameraPermissionLayout(
                    title       = "Camera Access",
                    body        = "The AR overlay uses your camera to show cardinal directions " +
                                  "over the live scene. Your camera feed is never stored or transmitted.",
                    buttonLabel = "Grant Camera",
                    onRequest   = requestCamera,
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
    horizonElevationDeg: Float,
    onExitAr: () -> Unit,
    lockedBearing: Float?,
    onToggleLock: (() -> Unit)?,
    onStreamingChanged: (Boolean) -> Unit,
) {
    val c = appColors
    var fovHalfDeg by remember { mutableFloatStateOf(Constants.Ar.DEFAULT_FOV_HALF_DEG) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

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
            onBindFailed  = { cameraFailed = true },
            onStreamingChanged = onStreamingChanged,
            modifier      = Modifier.fillMaxSize(),
        )

        // Layer 2 — cardinal labels. Also un-inset: the FOV → screen mapping is
        // defined against the full preview viewport, so shrinking this box would
        // put every label at the wrong angle.
        // Frozen while the camera is dead: labels sliding over a black screen
        // beside a "camera unavailable" message read as a second failure rather
        // than as the one being explained.
        if (!cameraFailed) {
            CardinalOverlay(
                azimuth      = arAzimuth,
                horizonElevationDeg = horizonElevationDeg,
                fovHalfDeg   = fovHalfDeg,
                lockedBearing = lockedBearing,
                headingDeg   = azimuth,
                modifier     = Modifier.fillMaxSize(),
            )
        }

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
                lockedBearing = lockedBearing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )

            // The labels keep tracking on a dead preview, which reads as a
            // broken AR mode rather than an unavailable camera. Say which.
            if (cameraFailed) {
                Text(
                    text       = "Camera unavailable.\nAnother app may be using it.",
                    color      = c.textPrimary,
                    fontSize   = 15.sp,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier   = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.chipBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }

            // ── Tool cluster, centred ─────────────────────────────────────
            // These were in the two bottom corners, mirroring each other. That
            // placement assumes the one-handed, phone-held-low grip whose thumb
            // arc fans into the corners — and AR is used at eye level, where
            // the phone is braced and that arc collapses to a small zone just
            // above the bottom bezel, centred. Reaching a corner from a braced
            // grip means unlocking the brace, which loses the bearing you were
            // sighting.
            //
            // Centring also gets the lock out of the left edge-swipe zone,
            // where a thumb reaching for it could trigger the back gesture —
            // which exits AR (see the BackHandler above). That was a real
            // hazard, not a theoretical one.
            //
            // Lock left, torch right, preserving the order they already had.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onToggleLock?.let { toggle ->
                    ArLockButton(
                        locked   = lockedBearing != null,
                        onToggle = toggle,
                    )
                }

                // Torch only exists if a camera actually bound. With one child
                // the Row simply centres it, which is the behaviour we want —
                // no special case, and no lone button stranded in an old corner.
                camera?.let { cam ->
                    TorchButton(
                        isTorchOn = isTorchOn,
                        onToggle  = { cam.cameraControl.enableTorch(!isTorchOn) },
                    )
                }
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
 * Degree readout pinned to the top-centre of the AR view, plus turn guidance
 * once a bearing is locked.
 *
 * Fed from the deadbanded display heading, *not* the AR positioning filter: the
 * filtered value trails by ~400 ms, so toggling between the flat screen and AR
 * used to show two different numbers for one physical heading.
 *
 * ### On the cardinal word
 * This was briefly removed as redundant with the chips on the horizon rule.
 * It is back, at the owner's request, after using both: the chip nearest the
 * centre restates it, but the pill is where the eye goes for a definitive
 * reading and having to find the right chip to confirm the cardinal was worse
 * than the duplication. Keeping it.
 *
 * The turn instruction sits on its own line beneath, appearing only while a
 * bearing is locked — that is the one thing the scene genuinely cannot show.
 *
 * Layout:  "247°  SW"       — no lock
 *          "247°  SW"       — locked
 *          "22° LEFT"
 */
@Composable
private fun ArHeadingHud(
    azimuth: Float,
    lockedBearing: Float?,
    modifier: Modifier = Modifier,
) {
    val c = appColors

    // 359.7° rounds to 360, which is not a heading — fold it back to 0.
    val degrees  = normalizedHeading(azimuth)
    val cardinal = bearingToCardinal(degrees.toFloat())
    val sectorKey = bearingToSpokenLabel(degrees.toFloat())

    // Read on focus: the exact heading, whenever the user asks for it.
    val spokenHeading = remember(degrees) { "Heading $degrees degrees, $sectorKey" }

    // Announced automatically: the sector only, which changes eight times per
    // rotation instead of the roughly 360 times the precise string does. Same
    // split, and the same reason, as the flat screen's readout — a live region
    // re-announces on every change of its description, so a per-degree
    // description makes TalkBack narrate continuously for as long as the phone
    // is moving.
    Column(
        modifier = modifier
            .background(c.chipBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Exactly the flat screen's split, and for the same reasons.
            //
            // NOT merged with a liveRegion on the parent: a merged node's
            // semantics include this degree Text, whose content changes ~360
            // times per rotation, and Compose emits a content-changed event for
            // a Text change just as it does for a description change — so the
            // live region would fire on every degree even with a sector-keyed
            // description. Merging also replaces the exact degree on focus with
            // whatever description the parent carries.
            Text(
                text       = "$degrees°",
                color      = c.textPrimary,
                fontSize   = 36.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                lineHeight = 36.sp,
                modifier   = Modifier.semantics { contentDescription = spokenHeading },
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text          = cardinal,
                color         = if (cardinal == "N") c.northLabel else c.textSecondary,
                fontSize      = 18.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                // The live region rides here: this Text's own content changes
                // eight times per rotation, which is the announce cadence we
                // want, and it has real bounds so it is actually in the tree.
                modifier      = Modifier.semantics {
                    contentDescription = sectorKey
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }

        // Same copy and the same colour logic as the flat screen's readout, so
        // the instruction does not change wording when the user raises the
        // phone mid-walk.
        // Unlike the flat screen this slot is NOT reserved when empty: the pill
        // is centred and free-floating, so it growing downward moves nothing
        // else on screen, and an always-present empty band under the degree
        // number would just be a taller pill over the scene for no reason.
        lockedBearing?.let { locked ->
            val delta = shortestArcDelta(locked, azimuth)
            val off = abs(delta).roundToInt()
            val onCourse = abs(delta) <= Constants.Heading.LOCK_ON_COURSE_DEG
            Text(
                text = when {
                    onCourse -> "ON COURSE"
                    delta > 0 -> "$off° RIGHT"
                    else -> "$off° LEFT"
                },
                color = if (onCourse) c.levelOk else c.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ── AR lock control ───────────────────────────────────────────────────────────

/**
 * Lock / unlock, bottom-left.
 *
 * Geometry is copied from [TorchButton] rather than chosen independently: two
 * controls of different sizes in opposite bottom corners reads as an accident.
 * The exit button at top-left stays 48 dp on purpose — it is a lower-emphasis
 * "leave this mode" affordance, not one of the two tools you use *inside* the
 * mode — and that difference is deliberate rather than an oversight.
 */
@Composable
private fun ArLockButton(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = appColors
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(c.chipBg, CircleShape)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (locked) "Clear locked bearing" else "Lock this bearing"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // Same glyph pair as the rose hub: open shackle versus closed, so
            // the state reads without relying on the tint.
            imageVector = if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = if (locked) c.levelOk else c.textPrimary,
            modifier = Modifier.size(24.dp),
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
// The FOV query below reads CameraCharacteristics through Camera2 interop, which
// CameraX marks experimental. Every AR label's position depends on it, so the
// opt-in is acknowledged here rather than suppressed project-wide: if a CameraX
// bump changes this API, the compiler must be able to say so at this call site.
//
// androidx.annotation.OptIn, fully qualified, NOT kotlin.OptIn. The marker is a
// Java @RequiresOptIn, so kotlin.OptIn compiles but leaves lint's
// UnsafeOptInUsageError unsatisfied — which is what fails `./gradlew lint`. It
// is spelled out here because kotlin.OptIn is also in use in this file, at the
// permission gate above.
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun CameraPreview(
    onFovMeasured: (halfFovDeg: Float) -> Unit,
    onCameraBound: (Camera) -> Unit,
    onBindFailed: () -> Unit,
    onStreamingChanged: (Boolean) -> Unit = {},
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
        // The provider future can resolve AFTER this effect is disposed — the
        // user leaves AR before the camera service answers. Unguarded, that late
        // listener binds the camera to a composition that is already gone: the
        // privacy indicator stays lit, the battery drains, other apps see the
        // camera as in use, and nothing is left alive to unbind it. It also
        // defeats the torch-off cleanup, which reads a `camera` that was still
        // null when dispose ran.
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        var previewStreamObserver: Observer<PreviewView.StreamState>? = null

        // Timing instrumentation for the AR entry cost. Each stage is logged
        // relative to the moment this effect ran, so the ~1 s that entry still
        // takes can be attributed rather than guessed at. Grep: `ar-timing`.
        val t0 = android.os.SystemClock.elapsedRealtime()
        fun mark(stage: String) =
            Log.i("ArOverlayScreen", "ar-timing $stage +${android.os.SystemClock.elapsedRealtime() - t0}ms")
        mark("effect-start")

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (disposed) return@addListener
                try {
                    val provider = future.get()
                    mark("provider-resolved")
                    boundProvider = provider
                    val preview  = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                    mark("bound")

                    // First frame actually on screen — the stage the user
                    // perceives as "the camera opened".
                    // Held so disposal can remove it. Left registered, one
                    // observer leaks per arm/disarm cycle, each retaining a dead
                    // PreviewView — and worse, tearing that old preview down
                    // drives it to IDLE, so the stale observer reports
                    // "not streaming" for a preview nobody is watching. If that
                    // lands after a newer instance already reported STREAMING,
                    // the caller hides AR and nothing ever restores it, because
                    // LiveData will not re-emit a value it already holds.
                    val streamObserver = Observer<PreviewView.StreamState> { state ->
                        if (disposed) return@Observer
                        val streaming = state == PreviewView.StreamState.STREAMING
                        if (streaming) mark("streaming")
                        onStreamingChanged(streaming)
                    }
                    previewStreamObserver = streamObserver
                    previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

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
                    // Another app holds the camera, the device has no back
                    // camera, or the HAL errored. All three used to leave the
                    // user staring at a black screen with cardinal labels
                    // floating on it and no explanation; the caller now says so.
                    Log.w("ArOverlay", "Camera bind failed", e)
                    // Release anyone gating a transition on the first frame:
                    // no camera means no frame is ever coming, and waiting for
                    // one would hang the user on the compass forever.
                    onStreamingChanged(true)
                    onFovMeasured(Constants.Ar.DEFAULT_FOV_HALF_DEG)
                    onBindFailed()
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            // Never ListenableFuture.get() here. onDispose runs on the main
            // thread and get() blocks it with no timeout — on a first
            // initialisation that has not resolved, or a wedged camera service,
            // that is an ANR on the way OUT of AR. The already-resolved provider
            // is captured above precisely so this path never blocks.
            previewStreamObserver?.let { previewView.previewStreamState.removeObserver(it) }
            boundProvider?.unbindAll()
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
    horizonElevationDeg: Float,
    fovHalfDeg: Float,
    lockedBearing: Float?,
    headingDeg: Float,
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
        animationSpec = instrumentSpec(),
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

        // ── Dead zone: the rule holds still unless you mean it ────────────
        // The settled elevation only moves once the live reading has been more
        // than HORIZON_DEAD_ZONE_DEG away from it for HORIZON_COMMIT_MS. Inside
        // that band — which is where hand tremor and a braced grip's wobble
        // both live — the rule does not move at all, which is the entire point.
        // Screen Y grows downward, so a positive elevation (camera aimed up) gives
        // a positive offset — raising the phone pushes the horizon, and the labels
        // on it, down the screen.
        //
        // `horizonElevationDeg` is already committed by HorizonCommitGate in the
        // ViewModel; this composable does not decide when the horizon may move.
        val fracY = (horizonElevationDeg / vFovHalfDeg)
            .coerceIn(-Constants.Ar.HORIZON_TRAVEL_CLAMP, Constants.Ar.HORIZON_TRAVEL_CLAMP)
        // Eases between committed positions now, rather than chasing a live
        // signal — so this is a transition, not a tracker.
        val animFracY by animateFloatAsState(
            targetValue   = fracY,
            animationSpec = horizonSpec(),
            label = "labelHorizon",
        )
        val yOffsetPx = (animFracY * halfHeightPx + Constants.Ar.HORIZON_BIAS_FRACTION * halfHeightPx).roundToInt()

        // ── The horizon rule ──────────────────────────────────────────────
        // Drawn before the labels so their chips sit on top of it and punch
        // their own gaps in it.
        //
        // This is the fix for "the labels vibrate vertically", and it is a
        // perceptual fix rather than a numerical one. Eight chips drifting
        // independently in mid-air read as broken; the identical motion applied
        // to one continuous line reads as a horizon being tracked, because the
        // eye follows a single object instead of eight. The damping change in
        // Constants.Ar is the other half — together they take the residual from
        // "shimmering" to "settling".
        val lockDelta = lockedBearing?.let { shortestArcDelta(it, headingDeg) }
        val onCourse = lockDelta != null &&
            abs(lockDelta) <= Constants.Heading.LOCK_ON_COURSE_DEG

        HorizonRule(
            yOffsetPx = yOffsetPx,
            widthPx = widthPx,
            heightPx = heightPx,
            // The notch marks where the device points. Tinting it green exactly
            // when the heading has arrived at the locked bearing is the AR twin
            // of the flat screen's ON COURSE turning green — green still means
            // "correct", and it appears only when that is true.
            onCourse = onCourse,
        )

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
                animationSpec = instrumentSpec(),
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

        // ── Locked bearing ────────────────────────────────────────────────
        // A glyph rather than a chip with a letter in it: a ninth labelled
        // chip on this rule would read as a ninth direction. It also gets its
        // own vertical lane above the line, so it never collides with a
        // cardinal chip when the two coincide, and so "the eight reference
        // points" and "the one place you are heading for" stay legible as two
        // different kinds of thing.
        if (lockedBearing != null && lockDelta != null) {
            val fracX = lockDelta / safeFovHalfDeg
            val animLockX by animateFloatAsState(
                targetValue = fracX.coerceIn(-Constants.Ar.CULL_MARGIN, Constants.Ar.CULL_MARGIN),
                animationSpec = instrumentSpec(),
                label = "lockMarker",
            )

            if (abs(fracX) <= Constants.Ar.CULL_MARGIN) {
                // On screen: mark it where it actually is.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                (animLockX * halfWidthPx).roundToInt(),
                                yOffsetPx - with(density) { 24.dp.roundToPx() },
                            )
                        },
                ) {
                    ArLockMarker(onCourse = onCourse)
                }
            } else {
                // Off screen — including directly behind the user, which is
                // where "which way do I turn" is the only useful thing left to
                // say. Same left/right convention as the flat screen, so the
                // meaning does not invert when the phone comes up.
                ArLockChevron(
                    toRight = lockDelta > 0,
                    degrees = abs(lockDelta).roundToInt(),
                    yOffsetPx = yOffsetPx,
                    modifier = Modifier.align(
                        if (lockDelta > 0) Alignment.CenterEnd else Alignment.CenterStart
                    ),
                )
            }
        }
    }
}

/** The on-screen locked-bearing glyph: the rose's marker, in screen space. */
@Composable
private fun ArLockMarker(onCourse: Boolean) {
    val c = appColors
    Canvas(modifier = Modifier.size(34.dp)) {
        val cx = size.width / 2f
        val top = size.height * 0.12f
        val ringY = top + 6.dp.toPx()

        // Stem down to the horizon rule, so the glyph is tied to a direction
        // rather than floating over the scene.
        drawLine(
            color = Color.Black.copy(alpha = 0.45f),
            start = Offset(cx, ringY),
            end = Offset(cx, size.height),
            strokeWidth = 3.dp.toPx(),
        )
        drawLine(
            color = c.levelOk,
            start = Offset(cx, ringY),
            end = Offset(cx, size.height),
            strokeWidth = 1.5f.dp.toPx(),
        )

        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = 6.dp.toPx() + 1.dp.toPx(),
            center = Offset(cx, ringY),
            style = Stroke(width = 3.dp.toPx()),
        )
        // Hollow until arrival, filled on it — the same idiom as the rose and
        // the bubble level.
        if (onCourse) {
            drawCircle(color = c.levelOk, radius = 6.dp.toPx(), center = Offset(cx, ringY))
        } else {
            drawCircle(
                color = c.levelOk,
                radius = 6.dp.toPx(),
                center = Offset(cx, ringY),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/**
 * Edge indicator for a locked bearing that is outside the camera's view.
 *
 * The chevron carries the direction, so the label carries magnitude only —
 * writing "140° RIGHT" beside an arrow that already points right is the same
 * redundancy the cardinal word was removed from the HUD for.
 */
@Composable
private fun ArLockChevron(
    toRight: Boolean,
    degrees: Int,
    yOffsetPx: Int,
    modifier: Modifier = Modifier,
) {
    val c = appColors
    Row(
        modifier = modifier
            .offset { IntOffset(0, yOffsetPx) }
            .padding(horizontal = 16.dp)
            .background(c.chipBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Locked bearing is $degrees degrees to the " +
                    if (toRight) "right" else "left"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!toRight) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = c.levelOk,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "$degrees°",
            color = c.levelOk,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        if (toRight) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.levelOk,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The line the cardinal labels stand on.
 *
 * Two strokes, not one: a wider dark stroke under a thin light one, so the rule
 * stays legible against both a bright sky and a dark treeline without needing
 * to know which it is over. A single light line disappears over cloud; a single
 * dark one disappears over shadow.
 *
 * Faded to nothing at both ends, so it reads as a horizon continuing past the
 * frame rather than as a UI element clamped to the screen edges.
 *
 * The notch at dead centre is the same idea as the compass rose's lubber index:
 * it marks the direction the device itself is pointing, which is the bearing
 * the HUD is showing. Without it the rule is decoration; with it, the overlay
 * answers "what am I looking at" geometrically as well as numerically.
 */
@Composable
private fun HorizonRule(
    yOffsetPx: Int,
    widthPx: Float,
    heightPx: Float,
    onCourse: Boolean = false,
) {
    val c = appColors

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = heightPx / 2f + yOffsetPx
        if (y < 0f || y > heightPx) return@Canvas

        val fade = { color: Color ->
            Brush.horizontalGradient(
                0.00f to color.copy(alpha = 0f),
                0.18f to color,
                0.82f to color,
                1.00f to color.copy(alpha = 0f),
            )
        }

        // Shadow pass, then the line itself.
        drawLine(
            brush = fade(Color.Black.copy(alpha = 0.45f)),
            start = Offset(0f, y),
            end = Offset(widthPx, y),
            strokeWidth = 3.dp.toPx(),
        )
        drawLine(
            brush = fade(c.textPrimary.copy(alpha = 0.55f)),
            start = Offset(0f, y),
            end = Offset(widthPx, y),
            strokeWidth = 1.dp.toPx(),
        )

        // Centre notch — "you are pointing here".
        val notch = 7.dp.toPx()
        val cx = widthPx / 2f
        drawLine(
            color = Color.Black.copy(alpha = 0.45f),
            start = Offset(cx, y - notch),
            end = Offset(cx, y + notch),
            strokeWidth = 3.dp.toPx(),
        )
        drawLine(
            color = if (onCourse) c.levelOk else c.lubber,
            start = Offset(cx, y - notch),
            end = Offset(cx, y + notch),
            strokeWidth = 1.5f.dp.toPx(),
        )
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
        animationSpec = chromeSpec(Constants.Ui.MODE_TRANSITION_MS),
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
