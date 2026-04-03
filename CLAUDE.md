# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build & Run Commands

All commands require Android Studio's JDK (system Java is v8, too old for Gradle):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Install on connected device | `./gradlew installDebug` |
| Full clean rebuild | `./gradlew clean assembleDebug` |
| Unit tests | `./gradlew test` |
| Instrumented tests | `./gradlew connectedAndroidTest` |
| Stop Gradle daemon | `./gradlew --stop` |

The device connects over Wi-Fi ADB (TLS). Verify with `adb devices` before installing.

---

## Architecture

### Data Flow

```
Android SensorManager
    ↓  (TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD)
SensorRepository          — callbackFlow; registers/unregisters listener with awaitClose
    ↓  Flow<CompassSensorData>   (azimuth, pitch, roll, isUpright)
CompassViewModel          — AndroidViewModel; collects flow into StateFlow
    ↓  StateFlow<Float>   (azimuth, arAzimuth, tilt, roll, declination, isUpright)
UI composables            — collectAsStateWithLifecycle()
```

### Package Layout

- **`sensor/`** — `SensorRepository` (hardware → Flow), `CompassSensorData` (data class: azimuth, pitch, roll, isUpright)
- **`viewmodel/`** — `CompassViewModel` (single ViewModel shared across both pages)
- **`ui/`** — `MainScreen` (pager root), `CompassScreen` (page 0), `TiltScreen` (page 1), `ArOverlayScreen` (AR camera overlay), `CompassRose`, `BubbleLevelIndicator`, `TiltIndicator`, `theme/Theme`
- **`widget/`** — `CompassWidget` (GlanceAppWidget), `CompassWidgetReceiver`, `CompassWidgetUpdateWorker`

### Navigation

`MainScreen` hosts a `HorizontalPager` (2 pages) + `NavigationBar`. Page 0 is `CompassScreen`, page 1 is `TiltScreen`. Both pages share the same `CompassViewModel` instance passed down from `MainScreen`. Horizontal pager swipe is disabled while AR mode is active (the camera view consumes the same gesture).

### Sensor Fusion

`SensorRepository` applies a **low-pass filter (alpha = 0.15)** to raw accelerometer and magnetometer arrays before feeding them into `SensorManager.getRotationMatrix()` + `getOrientation()`. The filter runs on every sensor event, not on the output.

**Coordinate-system remap for upright mode** — When the phone is held upright (AR mode), gravity aligns with the device Y-axis instead of Z-axis, causing the default rotation matrix to produce a wrong azimuth. `SensorRepository` detects this with `|gravity[1]| > |gravity[2]|` and calls `remapCoordinateSystem(AXIS_X, AXIS_Z)` before `getOrientation()`. This is why `isUpright` is derived from the raw gravity vector rather than from `orientation[1]` (pitch): after the remap, `orientation[1]` no longer represents intuitive device tilt.

### True North Correction

`CompassViewModel.updateDeclination()` fires a single GPS fix via `FusedLocationProviderClient`, computes `GeomagneticField.declination`, and stores it. Display azimuth = `(magneticAzimuth + declination + 360) % 360`. Call this after `ACCESS_FINE_LOCATION` is granted.

### AR Camera Overlay

`ArOverlayScreen` (`ui/ArOverlayScreen.kt`) is a fullscreen CameraX-based composable that overlays floating cardinal-direction labels on a live camera preview.

**Auto-trigger logic** — `SensorRepository` derives `isUpright` from the raw gravity vector (`|gravity.y| > |gravity.z|`), not from the remapped pitch. `MainScreen` auto-enters AR when `isUpright`, with a `arModeManual: Boolean?` override (`null` = auto, `true`/`false` = forced). `CompassScreen` Crossfades between `CompassFlatContent` and `ArOverlayScreen` in 300 ms.

**arAzimuth** — `CompassViewModel` exposes a second-stage heavily-filtered azimuth (α=0.05, shortest-arc blend) for stable AR label positioning. Pass `arAzimuth` (not `azimuth`) to `ArOverlayScreen`.

**FOV-aware label positioning** — On camera bind, CameraX + Camera2 interop reads `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and `SENSOR_INFO_PHYSICAL_SIZE` to compute the real horizontal FOV. Portrait orientation uses `min(sensorWidth, sensorHeight)` (the camera's narrower dimension) to match what's actually visible. Falls back to 60° total if the query fails.

**Label culling** — Labels outside ±1.3× half-screen-width are skipped so they slide in from edges rather than popping.

### Wrap-Around Animation Fix

`CompassRose` maintains a continuous (unbounded) float angle instead of passing raw azimuth to `animateFloatAsState`. The delta formula `((new − old + 540) % 360) − 180` always yields the shortest arc in (−180°, +180°], preventing the rose from spinning backwards when crossing 0°/360°.

### Widget Architecture

```
WorkManager CoroutineWorker (every 15 min)
  → register sensors on Handler(Looper.getMainLooper())
  → CompletableDeferred bridges SensorEventListener → coroutine
  → withTimeout(3s) for first fused reading
  → write azimuth + cardinal to DataStore Preferences (widgetDataStore)
  → CompassWidget().updateAll(context)   ← needs import androidx.glance.appwidget.updateAll

CompassWidget (GlanceAppWidget, SizeMode.Responsive)
  → provideGlance reads DataStore, pre-renders arrow Bitmap
  → provideContent switches on LocalSize.current:
      100×100dp  → cardinal text only
      180×80dp   → cardinal + degree number
      180×180dp  → rotating Bitmap arrow + label
```

**Glance gotchas:**
- `remember {}` is **not supported** inside `provideContent`. Pre-compute anything stateful (e.g. the arrow `Bitmap`) in `provideGlance` before calling `provideContent`.
- Use `android.graphics.Color` ints for `ColorProvider`, not `androidx.compose.ui.graphics.Color`.
- `updateAll` is a suspend extension from `androidx.glance.appwidget` — the import must be explicit.

---

## Key Versions

| Dependency | Version | Note |
|---|---|---|
| compileSdk / targetSdk | 35 | Do **not** use `release(36)` DSL — causes build errors |
| minSdk | 24 | |
| Kotlin | 2.2.10 | Requires `kotlin.plugin.compose` plugin |
| Compose BOM | 2024.09.00 | Manages all `androidx.compose.*` versions |
| Accompanist Permissions | 0.36.0 | Matches Compose 1.7.x shipped in this BOM |
| Lifecycle | 2.8.6 | |
| Glance | 1.1.1 | Independent of BOM — pinned in `libs.versions.toml` |
| WorkManager | 2.9.1 | |
| DataStore Preferences | 1.1.1 | |
| CameraX | 1.3.4 | camera-core, camera-camera2, camera-lifecycle, camera-view |

All library aliases are declared in `gradle/libs.versions.toml` and referenced as `libs.*` in `app/build.gradle.kts`.

---

## ARCore Phase 2 Hooks

The CameraX-based AR overlay (`ArOverlayScreen`) is **already implemented** — camera preview, FOV-aware cardinal label positioning, permission flow, and auto-trigger via `isUpright`. What remains for true ARCore integration are comments marked `// TODO Phase 2 – ARCore hook:`:

| File | What to add |
|---|---|
| `MainActivity.kt` | `ArSessionLifecycleObserver` wired to `lifecycle.addObserver()` |
| `CompassViewModel.kt` | `ArSession` field; `arSessionAvailable: StateFlow<Boolean>`; blend ARCore pose with sensor azimuth |
| `SensorRepository.kt` | Augment flow with `ArFrame.getCamera()` orientation for higher-accuracy heading |
| `AndroidManifest.xml` | Uncomment `android.hardware.camera.ar` feature |
| `app/build.gradle.kts` | Add `implementation("com.google.ar:core:<version>")` |
