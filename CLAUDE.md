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
    ↓  Flow<CompassSensorData>
CompassViewModel          — AndroidViewModel; collects flow into StateFlow
    ↓  StateFlow<Float>   (azimuth, tilt, roll, declination)
UI composables            — collectAsStateWithLifecycle()
```

### Package Layout

- **`sensor/`** — `SensorRepository` (hardware → Flow), `CompassSensorData` (data class: azimuth, pitch, roll in degrees)
- **`viewmodel/`** — `CompassViewModel` (single ViewModel shared across both pages)
- **`ui/`** — `MainScreen` (pager root), `CompassScreen` (page 0), `TiltScreen` (page 1), `CompassRose`, `BubbleLevelIndicator`, `TiltIndicator`, `theme/Theme`
- **`widget/`** — `CompassWidget` (GlanceAppWidget), `CompassWidgetReceiver`, `CompassWidgetUpdateWorker`

### Navigation

`MainScreen` hosts a `HorizontalPager` (2 pages) + `NavigationBar`. Page 0 is `CompassScreen`, page 1 is `TiltScreen`. Both pages share the same `CompassViewModel` instance passed down from `MainScreen`.

### Sensor Fusion

`SensorRepository` applies a **low-pass filter (alpha = 0.15)** to raw accelerometer and magnetometer arrays before feeding them into `SensorManager.getRotationMatrix()` + `getOrientation()`. This balances jitter reduction with responsiveness. The filter runs on every sensor event, not on the output.

### True North Correction

`CompassViewModel.updateDeclination()` fires a single GPS fix via `FusedLocationProviderClient`, computes `GeomagneticField.declination`, and stores it. Display azimuth = `(magneticAzimuth + declination + 360) % 360`. Call this after `ACCESS_FINE_LOCATION` is granted.

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
| Kotlin | 2.0.21 | Requires `kotlin.plugin.compose` plugin |
| Compose BOM | 2024.09.00 | Manages all `androidx.compose.*` versions |
| Accompanist Permissions | 0.36.0 | Matches Compose 1.7.x shipped in this BOM |
| Lifecycle | 2.8.6 | |
| Glance | 1.1.1 | Independent of BOM — pinned in `libs.versions.toml` |
| WorkManager | 2.9.1 | |
| DataStore Preferences | 1.1.1 | |

All library aliases are declared in `gradle/libs.versions.toml` and referenced as `libs.*` in `app/build.gradle.kts`.

---

## ARCore Phase 2 Hooks

Comments marked `// TODO Phase 2 – ARCore hook:` exist in:

| File | What to add |
|---|---|
| `MainActivity.kt` | `ArSessionLifecycleObserver` wired to `lifecycle.addObserver()` |
| `CompassViewModel.kt` | `ArSession` field; `arSessionAvailable: StateFlow<Boolean>`; blend ARCore pose with sensor azimuth |
| `SensorRepository.kt` | Replace/augment flow with `ArFrame.getUpdatedTrackables()` orientation |
| `CompassScreen.kt` | AR-toggle button; navigate to AR composable when session available + camera permission granted |
| `CompassRose.kt` | Optional `arOverlay: @Composable BoxScope.() -> Unit` parameter |
| `AndroidManifest.xml` | Uncomment `android.hardware.camera.ar` feature + `CAMERA` permission |
| `app/build.gradle.kts` | Add `implementation("com.google.ar:core:<version>")` |
