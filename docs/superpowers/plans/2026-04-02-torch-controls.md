# Torch Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a draggable, expandable torch control bubble to the AR camera overlay and hide the bottom navigation bar during AR mode.

**Architecture:** Two independent features: (1) `TorchBubble` composable added as a layer in `ArContent`, wired to CameraX torch APIs via the hoisted `Camera` object, and (2) `MainScreen` refactored from `Scaffold` to `Box` layout with `AnimatedVisibility` on the `NavigationBar`. The `CameraPreview` composable is refactored to use `DisposableEffect` for safe camera binding.

**Tech Stack:** Jetpack Compose (animations, gestures, semantics), CameraX + Camera2 interop, Kotlin coroutines

**Spec:** `docs/superpowers/specs/2026-04-02-torch-controls-design.md`

---

## Task 1: Refactor CameraPreview to DisposableEffect and hoist Camera object

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt:273-351`

This is a prerequisite for torch controls — the `Camera` object must be available in `ArContent`.

- [ ] **Step 1: Refactor `CameraPreview` signature to accept `onCameraBound` callback**

In `ArOverlayScreen.kt`, replace the existing `CameraPreview` composable (lines 273–351) with:

```kotlin
@Composable
private fun CameraPreview(
    onCameraBound: (Camera) -> Unit,
    onFovMeasured: (halfFovDeg: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

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

                    onCameraBound(camera)

                    // ── Query actual horizontal FOV via Camera2 interop ────────
                    val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
                    val focalLengths = cam2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    )
                    val sensorSize = cam2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
                    )

                    if (focalLengths != null && focalLengths.isNotEmpty() &&
                        sensorSize != null
                    ) {
                        val focalLength        = focalLengths[0]
                        val portraitSensorWidth = minOf(sensorSize.width, sensorSize.height)
                        val hfovRad            = 2.0 * atan(portraitSensorWidth / (2.0 * focalLength))
                        val halfFovDeg         = Math.toDegrees(hfovRad).toFloat() / 2f
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
```

Add these imports at the top of the file:

```kotlin
import androidx.camera.core.Camera
import androidx.compose.runtime.DisposableEffect
```

- [ ] **Step 2: Update `ArContent` to hoist the `Camera` object**

In `ArContent` (lines 144–191), add a `camera` state and pass it to `CameraPreview`:

```kotlin
@Composable
private fun ArContent(
    azimuth: Float,
    onExitAr: () -> Unit,
) {
    val fovHalfDeg = remember { mutableFloatStateOf(DEFAULT_FOV_HALF_DEG) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 — live camera background
        CameraPreview(
            onCameraBound = { camera = it },
            onFovMeasured = { measuredHalfFov -> fovHalfDeg.floatValue = measuredHalfFov },
            modifier      = Modifier.fillMaxSize(),
        )

        // Layer 2 — cardinal direction labels
        CardinalOverlay(
            azimuth    = azimuth,
            fovHalfDeg = fovHalfDeg.floatValue,
            modifier   = Modifier.fillMaxSize(),
        )

        // Layer 3 — exit button
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

        // Layer 4 — degree + cardinal HUD
        ArHeadingHud(
            azimuth  = azimuth,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
        )

        // Layer 5 — torch bubble (added in Task 3)
    }
}
```

Add import:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

- [ ] **Step 3: Build and verify on device**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. Camera preview and AR overlay work exactly as before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt
git commit -m "refactor: move CameraPreview binding to DisposableEffect, hoist Camera object"
```

---

## Task 2: Hide bottom navigation bar during AR mode

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/MainScreen.kt`

- [ ] **Step 1: Replace Scaffold with Box layout and add AnimatedVisibility**

Replace the entire `MainScreen` composable in `MainScreen.kt` with:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: CompassViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope      = rememberCoroutineScope()

    var arModeManual by remember { mutableStateOf<Boolean?>(null) }
    val isUpright by viewModel.isUpright.collectAsStateWithLifecycle()
    val isArMode = arModeManual ?: isUpright

    // Bottom padding for pager content when nav bar is visible.
    // 80.dp matches the NavigationBar default height.
    val bottomPadding by animateDpAsState(
        targetValue   = if (isArMode) 0.dp else 80.dp,
        animationSpec = tween(durationMillis = 300, easing = EaseInOut),
        label         = "navPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        // Pager — always fills full screen
        HorizontalPager(
            state             = pagerState,
            userScrollEnabled = !isArMode,
            modifier          = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
        ) { page ->
            when (page) {
                0 -> CompassScreen(
                    viewModel      = viewModel,
                    isArMode       = isArMode,
                    onArModeChange = { arModeManual = it },
                )
                1 -> TiltScreen(viewModel = viewModel)
                else -> Unit
            }
        }

        // Navigation bar — slides in/out
        AnimatedVisibility(
            visible = !isArMode,
            enter   = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = tween(durationMillis = 300, easing = EaseInOut),
            ),
            exit    = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = EaseInOut),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NavigationBar(
                containerColor = BgCard,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick  = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon  = {
                        Icon(
                            imageVector        = Icons.Filled.Explore,
                            contentDescription = "Compass",
                        )
                    },
                    label  = { Text("Compass") },
                    colors = navBarItemColors(),
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick  = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon  = {
                        Icon(
                            imageVector        = Icons.Filled.Tune,
                            contentDescription = "Level",
                        )
                    },
                    label  = { Text("Level") },
                    colors = navBarItemColors(),
                )
            }
        }
    }
}
```

Add these imports at the top of `MainScreen.kt`:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
```

Remove these imports (no longer needed):

```kotlin
import androidx.compose.material3.Scaffold
```

- [ ] **Step 2: Build and verify on device**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

Verify on device:
1. Launch app → bottom nav bar visible as before
2. Tilt phone upright or tap camera icon → AR mode activates, nav bar slides down smoothly
3. Tap exit AR → nav bar slides back up smoothly
4. Swipe between Compass and Level pages → still works when nav bar is visible

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/MainScreen.kt
git commit -m "feat: hide bottom navigation bar with slide animation during AR mode"
```

---

## Task 3: Implement TorchButton composable

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt`

- [ ] **Step 1: Add TorchButton composable**

Add the following composable at the bottom of `ArOverlayScreen.kt`, before the closing of the file:

```kotlin
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

    val borderColor = if (isTorchOn) AccentRed else Color(0x26CCD6F6) // 15% TextPrimary
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
                onClick            = onToggle,
                indication         = null,
                interactionSource  = remember { MutableInteractionSource() },
            )
            .semantics {
                contentDescription = if (isTorchOn) "Turn off flashlight" else "Turn on flashlight"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.FlashOn,
            contentDescription = null, // covered by parent semantics
            tint               = iconTint,
            modifier           = Modifier.size(24.dp),
        )
    }
}
```

Add these imports:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
```

- [ ] **Step 2: Build to verify compilation**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt
git commit -m "feat: add TorchButton composable with glow animation and accessibility"
```

---

## Task 4: Implement BrightnessSlider composable

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt`

- [ ] **Step 1: Add BrightnessSlider composable**

Add below `TorchButton` in `ArOverlayScreen.kt`:

```kotlin
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
            .width(trackWidthDp + thumbRadiusDp * 2 + 8.dp) // extra horizontal space for thumb
            .height(trackHeightDp + thumbRadiusDp * 2)
            .semantics {
                stateDescription = "Brightness ${(fraction * 100).roundToInt()}%"
            }
            .pointerInput(Unit) {
                val trackHeightPx = trackHeightDp.toPx()
                val thumbRadiusPx = thumbRadiusDp.toPx()
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    // Map Y position to fraction (top = 1.0, bottom = 0.0)
                    val yInTrack = (change.position.y - thumbRadiusPx).coerceIn(0f, trackHeightPx)
                    val newFraction = 1f - (yInTrack / trackHeightPx)
                    onFractionChange(newFraction.coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Track background
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .height(trackHeightDp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x26CCD6F6)) // 15% TextPrimary
        )

        // Track fill (gradient from AccentRed at bottom to TextPrimary at top)
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

        // Thumb
        val thumbOffsetY = trackHeightDp * (1f - fraction)
        Box(
            modifier = Modifier
                .size(thumbRadiusDp * 2)
                .offset(y = thumbOffsetY)
                .align(Alignment.TopCenter)
                .background(TextPrimary, CircleShape)
                .border(2.dp, BgDeep, CircleShape)
        )
    }
}
```

Add these imports:

```kotlin
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
```

Note: `roundToInt` from `kotlin.math` is likely already imported (used by `CardinalOverlay`). The `RoundedCornerShape` import is also already present.

- [ ] **Step 2: Build to verify compilation**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt
git commit -m "feat: add BrightnessSlider composable with vertical drag and gradient fill"
```

---

## Task 5: Implement TorchBubble with drag, snap, and expand/collapse

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt`

- [ ] **Step 1: Add TorchBubble composable**

Add below `BrightnessSlider` in `ArOverlayScreen.kt`:

```kotlin
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

        // Bubble dimensions (estimated; actual measured after layout)
        val bubbleWidthPx  = with(density) { 68.dp.toPx() }  // pill width with padding
        val bubbleHeightPx = with(density) { if (isTorchOn && supportsBrightness) 160.dp.toPx() else 68.dp.toPx() }

        // Snap targets: right edge (default) or left edge
        val rightEdgeX = screenWidthPx - bubbleWidthPx - with(density) { 12.dp.toPx() }
        val leftEdgeX  = with(density) { 12.dp.toPx() }

        // Default position: bottom-right
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
                            // Snap to nearest edge
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
                // Brightness slider — only when torch is on AND brightness is supported
                if (isTorchOn && supportsBrightness) {
                    BrightnessSlider(
                        fraction         = brightnessFraction,
                        onFractionChange = onBrightnessChange,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Torch on/off button
                TorchButton(
                    isTorchOn = isTorchOn,
                    onToggle  = onToggleTorch,
                )
            }
        }
    }
}
```

Add these imports:

```kotlin
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Build to verify compilation**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt
git commit -m "feat: add TorchBubble with edge-rail drag, snap, and expand/collapse"
```

---

## Task 6: Wire torch controls to CameraX and integrate into ArContent

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt`

- [ ] **Step 1: Add torch state management and Camera2 brightness query to ArContent**

Replace the `ArContent` composable with the full wired version:

```kotlin
@Composable
private fun ArContent(
    azimuth: Float,
    onExitAr: () -> Unit,
) {
    val fovHalfDeg = remember { mutableFloatStateOf(DEFAULT_FOV_HALF_DEG) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // ── Torch state ──────────────────────────────────────────────────────────
    var isTorchOn by remember { mutableStateOf(false) }
    var brightnessFraction by remember { mutableFloatStateOf(1f) }
    var supportsBrightness by remember { mutableStateOf(false) }
    var maxBrightnessLevel by remember { mutableIntStateOf(0) }

    // Sync local torch state with actual camera torch state
    camera?.let { cam ->
        DisposableEffect(cam) {
            val observer = androidx.lifecycle.Observer<Int> { state ->
                isTorchOn = (state == androidx.camera.core.TorchState.ON)
            }
            cam.cameraInfo.torchState.observeForever(observer)
            onDispose { cam.cameraInfo.torchState.removeObserver(observer) }
        }
    }

    // Query brightness support when camera is bound
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val cam2Info = Camera2CameraInfo.from(cam.cameraInfo)
                val maxLevel = cam2Info.getCameraCharacteristic(
                    CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
                ) ?: 0
                if (maxLevel > 1) {
                    supportsBrightness = true
                    maxBrightnessLevel = maxLevel
                }
            } catch (e: Exception) {
                Log.w("ArOverlay", "Brightness query failed: ${e.message}")
            }
        }
    }

    // Apply brightness whenever fraction changes and torch is on.
    // Also re-applies after torch is toggled on (isTorchOn becomes true).
    LaunchedEffect(brightnessFraction, isTorchOn) {
        val cam = camera ?: return@LaunchedEffect
        if (isTorchOn && supportsBrightness && Build.VERSION.SDK_INT >= 35) {
            applyBrightness(cam, brightnessFraction, maxBrightnessLevel)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 — live camera
        CameraPreview(
            onCameraBound = { camera = it },
            onFovMeasured = { measuredHalfFov -> fovHalfDeg.floatValue = measuredHalfFov },
            modifier      = Modifier.fillMaxSize(),
        )

        // Layer 2 — cardinal labels
        CardinalOverlay(
            azimuth    = azimuth,
            fovHalfDeg = fovHalfDeg.floatValue,
            modifier   = Modifier.fillMaxSize(),
        )

        // Layer 3 — exit button
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

        // Layer 4 — heading HUD
        ArHeadingHud(
            azimuth  = azimuth,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
        )

        // Layer 5 — torch bubble (only when camera is available)
        if (camera != null) {
            TorchBubble(
                isTorchOn          = isTorchOn,
                onToggleTorch      = {
                    val cam = camera ?: return@TorchBubble
                    cam.cameraControl.enableTorch(!isTorchOn)
                },
                supportsBrightness = supportsBrightness,
                brightnessFraction = brightnessFraction,
                onBrightnessChange = { brightnessFraction = it },
            )
        }
    }
}
```

- [ ] **Step 2: Add the `applyBrightness` helper function**

Add this top-level private function above `ArContent`:

```kotlin
@androidx.annotation.RequiresApi(35)
private fun applyBrightness(camera: Camera, fraction: Float, maxLevel: Int) {
    if (maxLevel <= 1) return
    try {
        val cam2Control = Camera2CameraControl.from(camera.cameraControl)
        cam2Control.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.FLASH_STRENGTH_LEVEL,
                    (fraction * maxLevel).toInt().coerceIn(1, maxLevel),
                )
                .build()
        )
    } catch (e: Exception) {
        Log.w("ArOverlay", "Failed to set brightness: ${e.message}")
    }
}
```

Add these imports:

```kotlin
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.TorchState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
```

- [ ] **Step 3: Build and verify on device**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

Verify on device:
1. Enter AR mode → torch bubble visible at bottom-right
2. Tap torch button → flashlight turns on, button glows red
3. Tap again → flashlight off, button returns to muted state
4. If device is API 35+ with brightness support → slider appears when torch is on
5. Long-press and drag bubble → snaps to left or right edge
6. Slide bubble up/down along the edge → free vertical movement

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt
git commit -m "feat: wire torch controls to CameraX with brightness support and state sync"
```

---

## Task 7: Clean up unused imports and verify complete build

**Files:**
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt`
- Modify: `app/src/main/java/com/pubpeb/mycompass/ui/MainScreen.kt`

- [ ] **Step 1: Review and clean imports in ArOverlayScreen.kt**

Remove any unused imports and ensure all new imports are present. The file should compile without warnings. Key imports to verify are present:

```kotlin
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
```

- [ ] **Step 2: Full clean build**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean assembleDebug
```
Expected: BUILD SUCCESSFUL with no warnings about unused imports.

- [ ] **Step 3: Full device test**

Verify the complete flow:
1. Launch app → nav bar visible, compass works
2. Enter AR mode (tilt or camera icon) → nav bar slides down, AR overlay fills screen
3. Torch bubble at bottom-right → tap to toggle torch
4. Long-press bubble → drag to left edge → snaps smoothly
5. Drag vertically along edge → moves freely
6. Exit AR → nav bar slides back up, torch turns off
7. Switch to Level page → swipe works normally

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pubpeb/mycompass/ui/ArOverlayScreen.kt app/src/main/java/com/pubpeb/mycompass/ui/MainScreen.kt
git commit -m "chore: clean up imports and verify complete build"
```
