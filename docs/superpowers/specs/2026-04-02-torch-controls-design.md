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
| Brightness slider | Only visible when OS supports torch intensity (Android 13+) |
| Bottom nav in AR | Slides down out of frame, slides back up on AR exit |

## Feature 1: Torch Bubble

### Collapsed State (torch off)

- Single circular button (44dp diameter) inside a rounded pill container
- Positioned on the right edge, bottom area by default
- Subtle border (`rgba(CCD6F6, 0.15)`), semi-transparent dark background (`0xCC0D0F14`)
- Lightning bolt icon in muted `TextSecondary` color
- Long-press initiates drag mode

### Expanded State (torch on)

- Tap the button → torch turns on, brightness slider animates upward out of the button using `animateContentSize` with a spring spec
- Button border and icon switch to `AccentRed` with a subtle glow (`0x40E63946` shadow)
- Vertical slider appears above the button:
  - Track: 6dp wide, rounded, `rgba(CCD6F6, 0.15)` background
  - Fill: gradient from `AccentRed` (bottom) to `TextPrimary` (top)
  - Thumb: 16dp circle, `TextPrimary` fill, dark border
  - Height: ~70dp
- Tap button again → torch off, slider collapses with reverse animation

### Brightness Slider Visibility

- On camera bind, query `CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` via Camera2 interop
- If the value is > 1, brightness control is supported → show slider when expanded
- If unsupported or value is 0/1 → hide slider entirely, bubble only shows on/off button
- Brightness is set via `Camera2CameraControl.setFlashStrengthLevel(level)` (Camera2 interop)

### Drag & Snap Behavior

- **Gesture**: `detectDragGestures` on the bubble container, with a long-press threshold to differentiate from tap
- **Horizontal snap**: When drag ends, animate to the nearest edge (left or right) using `animateFloatAsState` with `spring(dampingRatio = 0.7, stiffness = StiffnessMedium)`
- **Vertical freedom**: Y position is freely set by drag, clamped to screen bounds (respecting top safe area / status bar and bottom edge)
- **State**: `offsetX: Animatable<Float>` (snaps to 0 or screenWidth - bubbleWidth), `offsetY: Float` (free)
- **Persistence**: Position resets to default (bottom-right) each time AR mode is entered. No need to persist across sessions.

### Animation Specs

| Animation | Type | Spec |
|---|---|---|
| Expand/collapse slider | `animateContentSize` | `spring(dampingRatio = 0.8, stiffness = StiffnessMediumLow)` |
| Horizontal edge snap | `Animatable.animateTo` | `spring(dampingRatio = 0.7, stiffness = StiffnessMedium)` |
| Torch button glow on | `animateColorAsState` | `tween(300ms)` |
| Vertical drag | Direct offset | No animation (follows finger) |

## Feature 2: Bottom Navigation Hide in AR Mode

### Behavior

- When `isArMode` becomes `true`, the `NavigationBar` in `MainScreen` slides down out of frame
- When `isArMode` becomes `false`, it slides back up
- The `HorizontalPager` content expands to fill the freed space (no bottom padding when nav is hidden)

### Implementation

- Wrap the `NavigationBar` in `AnimatedVisibility`:
  - `visible = !isArMode`
  - `enter = slideInVertically(initialOffsetY = { it })` (slides up from below)
  - `exit = slideOutVertically(targetOffsetY = { it })` (slides down to below)
  - Animation spec: `tween(300ms, easeInOut)` to match the 300ms AR crossfade in `CompassScreen`
- The `Scaffold`'s `innerPadding` automatically adjusts when `bottomBar` content size changes via `AnimatedVisibility`, so the pager fills the full screen during AR mode.

## Architecture

### No ViewModel Changes

All torch state is local to the AR overlay:
- `isTorchOn: Boolean` — mutable state in `ArContent`
- `brightnessLevel: Float` (0..1) — mutable state in `TorchBubble`
- `supportsBrightness: Boolean` — determined on camera bind
- `bubbleOffsetX`, `bubbleOffsetY` — drag position state

The `Camera` object from CameraX `bindToLifecycle` is currently consumed inside `CameraPreview`. It needs to be hoisted up to `ArContent` via a new `onCameraBound: (Camera) -> Unit` callback (same pattern as the existing `onFovMeasured`). `ArContent` stores it in a `remember { mutableStateOf<Camera?>(null) }` and passes it to `TorchBubble`.

### File Changes

| File | Change |
|---|---|
| `ui/ArOverlayScreen.kt` | Add `TorchBubble` composable, `TorchButton`, `BrightnessSlider`; wire torch control via CameraX `CameraControl`; query brightness support via Camera2 interop |
| `ui/MainScreen.kt` | Wrap `NavigationBar` in `AnimatedVisibility` keyed on `isArMode`; adjust padding |
| `app/build.gradle.kts` | No changes needed — CameraX and Camera2 interop already in dependencies |

### CameraX Torch API

```kotlin
// Toggle torch (already available via CameraX)
camera.cameraControl.enableTorch(true/false)

// Query brightness support (Camera2 interop, Android 13+)
val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
val maxLevel = cam2Info.getCameraCharacteristic(
    CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
) ?: 0

// Set brightness level (Camera2 interop)
val cam2Control = Camera2CameraControl.from(camera.cameraControl)
cam2Control.setCaptureRequestOptions(
    CaptureRequestOptions.Builder()
        .setCaptureRequestOption(
            CaptureRequest.FLASH_STRENGTH_LEVEL,
            (fraction * maxLevel).toInt().coerceIn(1, maxLevel)
        )
        .build()
)
```

### Composable Hierarchy

```
ArContent
├── CameraPreview (existing — also returns Camera object now)
├── CardinalOverlay (existing)
├── IconButton (exit AR — existing)
├── ArHeadingHud (existing)
└── TorchBubble (NEW)
    ├── TorchButton (on/off toggle)
    └── BrightnessSlider (conditional, animated expand/collapse)
```

## Edge Cases

- **Camera bind fails** (emulator/no camera): Torch bubble is not shown. It depends on a valid `Camera` object.
- **Brightness query fails**: Fall back to on/off only (no slider).
- **Drag during slider interaction**: Long-press differentiates drag from slider/button taps. Slider touch events consume the gesture so they don't trigger drag.
- **Screen rotation**: App is portrait-locked per manifest, so no handling needed.
- **Torch already on externally**: `camera.cameraInfo.torchState` is observable — sync local state with it on composition.
