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
    ↓  Flow<CompassSensorData>   (azimuth, pitch, roll, cameraElevationDeg,
                                  tiltFromFlatDeg, accuracy)
CompassViewModel          — AndroidViewModel; collects flow into StateFlow.
                            Owns AR hysteresis, the heading deadband, and the
                            sensor-staleness watchdog.
    ↓  StateFlow  (azimuth, arAzimuth, pitch, roll, cameraElevation, accuracy,
                   trueNorth, declination, hasDeclination, themeMode, isUpright)
UI composables            — collectAsStateWithLifecycle()
```

### Package Layout

- **`sensor/`** — `SensorRepository` (hardware → Flow), `CompassSensorData` (data class: azimuth, pitch, roll, cameraElevationDeg, tiltFromFlatDeg, accuracy) + `CompassAccuracy` enum
- **`viewmodel/`** — `CompassViewModel` (single ViewModel shared across both pages)
- **`ui/`** — `MainScreen` (pager root), `CompassScreen` (page 0), `TiltScreen` (page 1), `ArOverlayScreen` (AR camera overlay), `CompassRose`, `BubbleLevelIndicator`, `theme/Theme`
- **`widget/`** — `CompassWidget` (GlanceAppWidget), `CompassWidgetReceiver`, `CompassWidgetUpdateWorker`

### Navigation

`MainScreen` hosts a `HorizontalPager` (2 pages) plus a two-dot page indicator — there is **no `NavigationBar`**. It was removed deliberately: 80dp of the compass screen to give the bubble level equal billing with the compass inverted the app's priority. The dots are decorative (`clearAndSetSemantics {}`), not a control; the swipe is the navigation.

`MainActivity` owns the single `CompassViewModel` and hoists it *above* `MyCompassTheme` so the Daylight toggle can re-theme the app; `MainScreen(viewModel:)` takes it as a required parameter and passes it to both pages. Horizontal pager swipe is disabled while AR mode is active (the camera view consumes the same gesture).

### Constants

**Every tunable value lives in `Constants.kt`** at the package root, grouped by
subsystem (`Sensor`, `Heading`, `Ar`, `Rose`, `Level`, `Ui`, `Widget`, `Prefs`,
`Legacy`). If a number carries meaning — a threshold, a duration, a filter
coefficient, a proportion — put it there and reference it. Purely local layout
arithmetic (`/ 2f` to find a centre, the `+ 540f` inside a documented formula)
stays inline; hoisting those obscures the maths.

Shared angle helpers are in `util/AngleUtil.kt`: `shortestArcDelta`,
`normalizedHeading`, `shouldPublishHeading`. The shortest-arc formula was
previously copy-pasted at five call sites with its verification comment beside
only one of them — do not re-inline it.

### Sensor lifetime

`CompassViewModel` exposes the hardware through a `stateIn` +
`SharingStarted.WhileSubscribed(5_000)` flow. The UI drives it:

```kotlin
lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    launch { viewModel.observeWhileVisible() }
    launch { viewModel.watchForStaleSensor() }
}
```

**Never collect the sensor flow on `viewModelScope` directly.** That scope
outlives `onStop`, so it kept the accelerometer and magnetometer registered at
`SENSOR_DELAY_UI` in the user's pocket — the app's single largest battery cost.

### Theming

There are **three palettes**, not two: `NightPalette`, `DayPalette`, and
`DaylightPalette` (maximum-contrast white ground for direct sunlight, toggled by
the user). `ThemeMode` is `SYSTEM | DAYLIGHT`; SYSTEM resolves via
`isSystemInDarkTheme()`.

**The old `AppColors` object is gone.** Never write a file-level
`private val BgDeep = AppColors.BgDeep` — that freezes one theme's colours into
every surface. Read the palette inside a composable:

```kotlin
import com.pubpeb.compass.ui.theme.appColors
val c = appColors          // AppPalette, from LocalAppColors
```

For `DrawScope` helpers and other non-composable functions, pass the colours in
as parameters. `MainActivity` also drives `isAppearanceLightStatusBars` from
`resolvePalette(mode).isDark` so system bar icons stay legible in every mode.

**Red is north, and nothing else.** `northLabel` is used for the "N" glyph on the
rose and for north in AR. The fixed rim index on the rose uses `lubber` (neutral)
precisely so red keeps that one meaning — see the note in `CompassRose`.

### Sensor Fusion

`SensorRepository` applies a **low-pass filter (alpha = 0.15)** to raw accelerometer and magnetometer arrays before feeding them into `SensorManager.getRotationMatrix()` + `getOrientation()`. The filter runs on every sensor event, not on the output.

**Coordinate-system remap for upright mode** — When the phone is held upright (AR mode), gravity aligns with the device Y-axis instead of Z-axis, causing the default rotation matrix to produce a wrong azimuth. `SensorRepository` detects this with `|gravity[1]| > |gravity[2]|` and calls `remapCoordinateSystem(AXIS_X, AXIS_Z)` before `getOrientation()`.

**The remap only applies to azimuth.** `getOrientation()` is called **twice**, on two different matrices: pitch and roll always come from the *unremapped* matrix. Reading them from the remapped one made the bubble level jump ~90° the instant the device crossed the threshold.

**Three distinct tilt values, do not confuse them:**
- `pitch` / `roll` — unremapped device orientation, for the level screen.
- `tiltFromFlatDeg` — `acos(|g.z| / |g|)`, a continuous 0–90° magnitude (0 = flat on either face, 90 = upright). The AR trigger signal. Replaced the old `|g.y| > |g.z|` test, which was a hard 45° threshold in disguise.
- `cameraElevationDeg` — `asin(-R[8])`, a **signed** −90…+90° elevation of the rear-camera axis. Anything placing a horizon must use this, never `pitch`: `getOrientation` gives pitch as `asin(-R[7])`, which folds camera-up and camera-down onto the same value.

**Accuracy** — `onAccuracyChanged` is plumbed through to `CompassAccuracy` (magnetometer only; the accelerometer's is always high). It starts at `UNRELIABLE`, never `HIGH`. The ViewModel adds a watchdog that synthesises `NO_SIGNAL` after 3s of silence, because `getRotationMatrix()` returning false stops emissions with no error at all.

### True North Correction

`CompassViewModel.updateDeclination()` fires a single GPS fix via `FusedLocationProviderClient`, computes `GeomagneticField.declination`, and stores it. Display azimuth = `(magneticAzimuth + declination + 360) % 360`, applied only when `trueNorth && hasDeclination`.

**Location is never a gate.** The compass renders magnetic north on the first frame with no permission at all; a fix only ever *upgrades* the reading. The app requests `ACCESS_COARSE_LOCATION` (not FINE) — declination varies over tens of kilometres, so a city-block fix gives the same correction and a milder prompt.

### AR Camera Overlay

`ArOverlayScreen` (`ui/ArOverlayScreen.kt`) is a fullscreen CameraX-based composable that overlays floating cardinal-direction labels on a live camera preview.

**Auto-trigger logic** — `sensor/ArTriggerGate.kt` owns the decision and applies **hysteresis**: enter above `Constants.Ar.ENTER_DEG = 65f`, exit below `Constants.Ar.EXIT_DEG = 50f`, with a `Constants.Ar.DWELL_MS = 800L` dwell before entry. The gate takes an injectable `nowMs: () -> Long` so it unit-tests with no Android framework — see `ArTriggerGateTest`, which includes the 45°-oscillation regression test. Auto-entry is user-toggleable (`Constants.Prefs.KEY_AR_AUTO`, default on); when off, `isUpright` is held false entirely. The old bare 45° threshold fired at the angle people naturally hold a phone, so the camera flickered on and off continuously. `MainScreen` keeps an `arModeManual: Boolean?` override in **`rememberSaveable`** (`null` = auto) and clears it on the falling edge of `isUpright` **only when it was set to `false`** — clearing a forced-*on* override too meant an incidental `false→true→false` blip threw the user out of an AR view they had opened deliberately. `CompassScreen` Crossfades between `CompassFlatContent` and `ArOverlayScreen` in 300 ms.

**Two azimuths, deliberately** — pass `azimuth` (deadbanded) for the numeric HUD and `arAzimuth` (α=0.05 second-stage, shortest-arc blend) for label *positions* only. Feeding the HUD from the heavy filter made the AR readout lag ~400ms behind the flat screen's readout for the same heading.

**FOV-aware label positioning** — On camera bind, CameraX + Camera2 interop reads `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and `SENSOR_INFO_PHYSICAL_SIZE` to compute the real horizontal FOV. Portrait orientation uses `min(sensorWidth, sensorHeight)` (the camera's narrower dimension) to match what's actually visible. Falls back to 60° total if the query fails.

**Label culling** — Labels outside ±1.3× half-screen-width are skipped so they slide in from edges rather than popping.

**Horizon placement** — Label Y tracks `cameraElevationDeg` scaled by a vertical FOV derived from the horizontal one and the viewport aspect ratio. Raising the phone moves labels down.

### Wrap-Around Animation Fix

`CompassRose` maintains a continuous (unbounded) float angle instead of passing raw azimuth to `animateFloatAsState`. The delta formula `((new − old + 540) % 360) − 180` always yields the shortest arc in (−180°, +180°], preventing the rose from spinning backwards when crossing 0°/360°.

### Widget Architecture

The widget shows **no live data**. It is a launcher tile: compass mark, the word
"Compass", and a tap target covering the whole surface.

```
CompassWidget (GlanceAppWidget, SizeMode.Responsive — 2 tiers)
  → provideContent, no data read, no state
  → whole tile: clickable(actionStartActivity<MainActivity>())
  → LocalSize.current.width >= 140dp ? mark + wordmark : mark only
```

**Why no heading.** A widget cannot hold the magnetometer open. Any number it
showed would be one reading snatched in whatever pose the phone happened to be
in — face-down on a desk, in a pocket — then displayed for up to fifteen minutes
with no timestamp and no accuracy gate. That is a guess presented as a fact, and
for direction it is worse than showing nothing. This deleted `CompassWidgetUpdateWorker`,
the `widgetDataStore`, the 15-minute periodic job, and the direct WorkManager and
DataStore dependencies with it.

**Do not reintroduce a live heading here** without also shipping a capture
timestamp, a visible age, and an accuracy gate that refuses to persist a
`LOW`/`UNRELIABLE` reading.

**Glance gotchas:**
- `remember {}` is **not supported** inside `provideContent`. Pre-compute anything
  stateful in `provideGlance` before calling `provideContent`.
- `ColorProvider(Int)` treats its argument as an `@ColorRes` id and passes it to
  `Resources.getColor()` — a raw ARGB literal there crashes the launcher's
  RemoteViews inflater. Use `ColorProvider(androidx.compose.ui.graphics.Color)`.
  Raw `android.graphics.Color` ints are only valid for `android.graphics.Paint`.
- The reified `actionStartActivity<T : Activity>()` is in **`androidx.glance.action`**.
  `androidx.glance.appwidget.action` has same-named `Intent` overloads, and
  importing that package leaves the reified call unresolvable.
- Glance pulls its own `androidx.datastore` transitively for widget state. That is
  expected — it is not a reintroduction of the app's own DataStore usage.

---

## Key Versions

| Dependency | Version | Note |
|---|---|---|
| compileSdk / targetSdk | 35 | Do **not** use `release(36)` DSL — causes build errors |
| minSdk | 24 | |
| Kotlin | 2.2.10 | Requires `kotlin.plugin.compose` plugin |
| Compose BOM | 2024.09.00 | Manages all `androidx.compose.*` versions |
| Accompanist Permissions | 0.36.0 | Matches Compose 1.7.x shipped in this BOM |
| CameraX | 1.4.1 | camera-core, camera-camera2, camera-lifecycle, camera-view |
| Lifecycle | 2.8.6 | |
| Glance | 1.1.1 | Independent of BOM — pinned in `libs.versions.toml`. Pulls DataStore 1.0.0 transitively |

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
