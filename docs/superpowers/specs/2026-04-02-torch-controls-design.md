# Torch Controls for AR Camera Overlay

## Summary

Add a draggable, expandable torch control bubble to the AR camera overlay screen (`ArOverlayScreen`). The bubble provides torch on/off toggle and an optional brightness slider (shown only when the OS supports torch intensity control). The bottom navigation bar hides with a slide-down animation when AR mode is active for full immersion.

## Design Decisions

| Decision | Choice |
|---|---|
| Layout style | Expandable Bubble |
| Drag & snap | Left/Right Edge Rail (free vertical slide) |
| Default position | Bottom-right |
| Collapse behavior | Collapsed when off, expands with slider on tap |
| Brightness slider | Only visible when OS supports torch intensity (Android 15+ / API 35) |
| Bottom nav in AR | Slides down out of frame, slides back up on AR exit |

## Feature 1: Torch Bubble

### Collapsed State (torch off)

- Single circular button (**48dp** diameter, meets Material 48dp minimum touch target) inside a rounded pill container
- Positioned on the right edge, bottom area by default
- Subtle border (`rgba(CCD6F6, 0.15)`), semi-transparent dark background (`0xCC0D0F14`)
- Lightning bolt icon in muted `TextSecondary` color, `contentDescription = "Toggle flashlight"`
- Long-press initiates drag mode (via `detectDragGesturesAfterLongPress`)

### Expanded State (torch on)

- Tap the button → torch turns on, brightness slider animates upward out of the button using `animateContentSize` with a spring spec
- Button border and icon switch to `AccentRed` with a subtle glow rendered via `Modifier.drawBehind { drawCircle(Color(0x40E63946), radius = size.minDimension) }` (Compose does not support colored drop shadows natively)
- Vertical slider appears above the button:
  - Track: 6dp wide, rounded, `rgba(CCD6F6, 0.15)` background
  - Fill: gradient from `AccentRed` (bottom) to `TextPrimary` (top)
  - Thumb: 16dp circle, `TextPrimary` fill, dark border
  - Height: ~70dp
  - Semantics: `Modifier.semantics { stateDescription = "Brightness ${(fraction * 100).toInt()}%" }`
  - Slider range maps to brightness levels 1..maxLevel (minimum brightness to maximum; dragging to the bottom gives minimum brightness, not torch off — torch off is handled by the toggle button)
- Tap button again → torch off, slider collapses with reverse animation

### Brightness Slider Visibility — API Level Gates

The brightness slider requires two separate API level checks:

1. **Query support (API 33+ / Android 13):** `CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` can be read to determine if the hardware supports variable brightness. Guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`.

2. **Set brightness (API 35+ / Android 15):** `CaptureRequest.FLASH_STRENGTH_LEVEL` is the capture request key needed to actually set the brightness level. Guard with `Build.VERSION.SDK_INT >= 35`.

Logic:
- If `Build.VERSION.SDK_INT < 35` → `supportsBrightness = false`, no slider shown
- If `Build.VERSION.SDK_INT >= 35` → query `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`:
  - If value > 1 → slider shown
  - If value is 0/1 or null → no slider, on/off only

On devices running API 24–34, only the torch on/off button is available (no brightness control).

### Drag & Snap Behavior

- **Gesture**: `detectDragGesturesAfterLongPress` on the outer bubble container. The brightness slider has its own `Modifier.pointerInput` scope that consumes vertical drag events, preventing them from propagating to the bubble's drag handler.
- **Horizontal snap**: When drag ends, animate to the nearest edge (left or right) using `Animatable.animateTo` with `spring(dampingRatio = 0.7, stiffness = StiffnessMedium)`
- **Vertical freedom**: Y position is freely set by drag, clamped to screen bounds (respecting top safe area / status bar and bottom edge)
- **State**: `offsetX: Animatable<Float>` (snaps to 0 or screenWidth - bubbleWidth), `offsetY: Float` (free)
- **Persistence**: Position resets to default (bottom-right) each time AR mode is entered. No need to persist across sessions.

### Animation Specs

| Animation | Type | Spec |
|---|---|---|
| Expand/collapse slider | `animateContentSize` | `spring(dampingRatio = 0.8, stiffness = StiffnessMediumLow)` |
| Horizontal edge snap | `Animatable.animateTo` | `spring(dampingRatio = 0.7, stiffness = StiffnessMedium)` |
| Torch button glow on | `drawBehind` + `animateFloatAsState` | Animate glow alpha 0→1 over `tween(300ms)` |
| Vertical drag | Direct offset | No animation (follows finger) |

## Feature 2: Bottom Navigation Hide in AR Mode

### Behavior

- When `isArMode` becomes `true`, the `NavigationBar` in `MainScreen` slides down out of frame
- When `isArMode` becomes `false`, it slides back up
- The `HorizontalPager` content fills the full screen when nav is hidden

### Implementation

Replace `Scaffold.bottomBar` with a `Box` overlay approach to avoid frame-by-frame relayout during animation:

```
Box {
    // Pager always fills full screen
    HorizontalPager(modifier = Modifier.fillMaxSize()) { ... }

    // Nav bar overlaid at bottom, slides in/out
    AnimatedVisibility(
        visible = !isArMode,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        NavigationBar { ... }
    }
}
```

This avoids the problem of `Scaffold.innerPadding` changing frame-by-frame during the animation, which would cause the pager to re-layout every frame. Instead, the pager is always full-screen and the nav bar floats on top. When the nav bar is visible, the pager content should add bottom padding equal to the nav bar height (~80dp) to avoid content being obscured. Use `animateDpAsState` keyed on `isArMode` for smooth padding transition, or just accept the overlap since the compass screen already has its own internal layout.

Animation spec: `tween(300ms, easeInOut)` to match the 300ms AR crossfade in `CompassScreen`.

## Architecture

### No ViewModel Changes

All torch state is local to the AR overlay:
- `isTorchOn: Boolean` — mutable state in `ArContent`
- `brightnessLevel: Float` (0..1) — mutable state in `TorchBubble`
- `supportsBrightness: Boolean` — determined on camera bind
- `maxBrightnessLevel: Int` — from camera characteristics, used to map slider fraction to integer level
- `bubbleOffsetX`, `bubbleOffsetY` — drag position state

### Camera Binding Refactor

The current `CameraPreview` binds the camera inside `AndroidView.update`, which runs on every recomposition. Hoisting the `Camera` object via a callback from `update` would cause a state change → recomposition → rebind loop.

**Fix:** Move camera binding out of `AndroidView.update` into a `DisposableEffect` keyed on `lifecycleOwner`:

```kotlin
@Composable
private fun CameraPreview(
    onCameraBound: (Camera) -> Unit,
    onFovMeasured: (halfFovDeg: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }}

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
            )
            onCameraBound(camera)
            // ... FOV query, call onFovMeasured ...
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
```

`ArContent` stores the camera in `remember { mutableStateOf<Camera?>(null) }` and passes it to `TorchBubble`.

### Torch State Synchronization

Add a `LaunchedEffect` in `ArContent` that observes the camera's actual torch state to keep local UI in sync:

```kotlin
val camera: Camera? = ...
val isTorchOn = remember { mutableStateOf(false) }

camera?.let { cam ->
    DisposableEffect(cam) {
        val observer = Observer<Int> { state ->
            isTorchOn.value = (state == TorchState.ON)
        }
        cam.cameraInfo.torchState.observeForever(observer)
        onDispose { cam.cameraInfo.torchState.removeObserver(observer) }
    }
}
```

This handles external torch state changes and ensures the UI is always accurate.

### File Changes

| File | Change |
|---|---|
| `ui/ArOverlayScreen.kt` | Refactor `CameraPreview` to use `DisposableEffect`; add `TorchBubble`, `TorchButton`, `BrightnessSlider` composables; wire torch via `CameraControl`; query brightness support with API level guards |
| `ui/MainScreen.kt` | Replace `Scaffold` with `Box` layout; wrap `NavigationBar` in `AnimatedVisibility` keyed on `isArMode` |
| `app/build.gradle.kts` | No changes needed — CameraX and Camera2 interop already in dependencies |

### CameraX Torch API

```kotlin
// Toggle torch (CameraX — all API levels)
camera.cameraControl.enableTorch(true)   // returns ListenableFuture
camera.cameraControl.enableTorch(false)

// Query brightness support (API 35+ only)
if (Build.VERSION.SDK_INT >= 35) {
    val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
    val maxLevel = cam2Info.getCameraCharacteristic(
        CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
    ) ?: 0
    supportsBrightness = maxLevel > 1
}

// Set brightness level (API 35+ only, call AFTER enableTorch(true))
if (Build.VERSION.SDK_INT >= 35 && supportsBrightness) {
    val cam2Control = Camera2CameraControl.from(camera.cameraControl)
    cam2Control.setCaptureRequestOptions(
        CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                CaptureRequest.FLASH_STRENGTH_LEVEL,
                (fraction * maxLevel).toInt().coerceIn(1, maxLevel)
            )
            .build()
    )
}
// Note: enableTorch(false) resets the strength level. When re-enabling,
// re-apply the brightness level after enableTorch(true) completes.
```

### Composable Hierarchy

```
ArContent
├── CameraPreview (refactored — DisposableEffect, hoists Camera object)
├── CardinalOverlay (existing)
├── IconButton (exit AR — existing)
├── ArHeadingHud (existing)
└── TorchBubble (NEW, only shown when camera != null)
    ├── TorchButton (on/off toggle, 48dp, contentDescription)
    └── BrightnessSlider (conditional on API 35+ & hardware support, animated expand/collapse)
```

## Edge Cases

- **Camera bind fails** (emulator/no camera): `camera` state remains null → torch bubble is not shown.
- **Brightness query fails or API < 35**: Fall back to on/off only (no slider).
- **Drag during slider interaction**: Slider has its own `pointerInput` scope that consumes vertical drag events. The outer bubble's `detectDragGesturesAfterLongPress` only fires on long-press, so normal taps and slider drags don't conflict.
- **Screen rotation**: App is portrait-locked per manifest, so no handling needed.
- **Torch state changed externally**: `DisposableEffect` observes `camera.cameraInfo.torchState` (LiveData) and syncs the local `isTorchOn` state.
- **enableTorch(true) then set brightness**: Brightness must be set after `enableTorch(true)` completes. Chain via `ListenableFuture.addListener` or `await()` in a coroutine.
- **API 24–34 devices**: Only torch on/off button visible. No brightness slider. No crashes — all Camera2 characteristic queries are behind `Build.VERSION.SDK_INT` guards.

## Accessibility

- Torch button: `contentDescription = "Toggle flashlight"` (or "Turn on flashlight" / "Turn off flashlight" based on state)
- Brightness slider: `Modifier.semantics { stateDescription = "Brightness ${percent}%" }`
- TalkBack: The bubble is a single focusable group. Double-tap toggles torch, swipe up/down adjusts brightness when expanded.
