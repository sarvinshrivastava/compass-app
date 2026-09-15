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

- **`sensor/`** — `SensorRepository` (hardware → Flow), `CompassSensorData` (data class: azimuth, pitch, roll, cameraElevationDeg, tiltFromFlatDeg, screenRollDeg, accuracy), `CompassAccuracy` enum, `ArTriggerGate`, `LevelPostureGate`, `DeclinationRepository`
- **`viewmodel/`** — `CompassViewModel` (single ViewModel shared across both pages)
- **`ui/`** — `MainScreen` (pager root), `CompassScreen` (page 0), `TiltScreen` (page 1), `ArOverlayScreen` (AR camera overlay), `CompassRose`, `BubbleLevelIndicator` (flat posture), `PlumbLevelIndicator` (upright posture), `CalibrationDialog`, `theme/Theme`
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

`util/SmoothingUtil.kt` holds `adaptiveLowPass`, the two-speed filter the AR
horizon uses. It is **non-cyclic only** — never point it at a bearing.

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

**Four distinct tilt values, do not confuse them:**
- `pitch` / `roll` — unremapped device orientation, for the level screen.
- `tiltFromFlatDeg` — `acos(|g.z| / |g|)`, a continuous 0–90° magnitude (0 = flat on either face, 90 = upright). The AR trigger signal. Replaced the old `|g.y| > |g.z|` test, which was a hard 45° threshold in disguise.
- `cameraElevationDeg` — `asin(-R[8])`, a **signed** −90…+90° elevation of the rear-camera axis. Anything placing a horizon must use this, never `pitch`: `getOrientation` gives pitch as `asin(-R[7])`, which folds camera-up and camera-down onto the same value.
- `screenRollDeg` — `atan2(g.x, g.y)`, rotation about the device's viewing axis, 0 = plumb portrait. The upright level's whole reading. **Not** `roll`: that is an Euler angle and degenerates at the ±90° pitch this value is used at. Meaningless while the phone is flat. See `learnings/level-upright-posture-and-euler-degeneracy.md`.

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
  expected — it is not a reintroduction of the app's own DataStore usage. The same
  goes for `androidx.work`: Glance composes inside a `SessionWorker`, so excluding
  that group makes `GlanceAppWidget.<init>` throw `ClassNotFoundException` and kills
  the process the moment the launcher touches the receiver. Never exclude it.
- The provider XML must carry `android:initialLayout`
  (`@layout/glance_default_loading_layout`), and any RemoteViews layout must avoid
  `<Space>` — see `learnings/glance-widget-three-silent-failures.md`. Neither failure
  is visible from the app, `lint`, or the unit tests; both need the widget picker.

---

### Telemetry

Firebase **Crashlytics** and **Analytics** both ship (`telemetry/Telemetry.kt`).
This is not a tracking-free app, and marketing copy must not claim it is — the
Data Safety form and the privacy policy both have to declare them.

The advertising ID is removed at the manifest level
(`AndroidManifest.xml`, three `tools:node="remove"` entries). The
`google_analytics_*` meta-data flags stop the SDK *using* it; only the removals
stop Play's Data Safety review from *reporting* it. Do not delete either half.

`SensorStalledException` is the one non-fatal this app raises deliberately, to
catch a sensor that goes silent with no error. Anything that makes it fire
spuriously destroys its only purpose — see the grace period at the top of
`watchForStaleSensor()`.

### Release build

`compileSdk`/`targetSdk` are **36**. `./gradlew test lint bundleRelease` must be
green before any upload; `lint` is wired to fail the build, and the two errors
it used to report are suppressed at the exact call sites with the reason written
beside them, never project-wide.

Signing is read from `COMPASS_STORE_FILE` / `COMPASS_STORE_PASSWORD` /
`COMPASS_KEY_ALIAS` / `COMPASS_KEY_PASSWORD` in `~/.gradle/gradle.properties`.
**When those are absent the release build still succeeds and emits an UNSIGNED
AAB** — Play rejects it, and nothing in the build output says why. Verify with
`jarsigner -verify app/build/outputs/bundle/release/app-release.aab`; it must
print `jar verified`.

Crashlytics mapping upload is gated on `COMPASS_RELEASE` being set, so a local
`assembleRelease` does not push an orphan mapping to production.

### Permanently-denied permissions

Android reports "denied for good" as *not granted, no rationale* — the same pair
it reports before the first ask. `util/PermissionAskLog.kt` persists the missing
third fact. Both permission call sites (camera in `ArOverlayScreen`, location in
`CompassScreen`) use it to send the user to system settings instead of firing a
request the platform silently refuses. A `rememberSaveable` marker is not enough;
it is lost on a cold start.

---

## Key Versions

| Dependency | Version | Note |
|---|---|---|
| compileSdk / targetSdk | 36 | Required by Play for all new submissions since 2026-08-31. The old "36 causes build errors" note here was wrong and cost a release cycle — plain `compileSdk = 36` builds clean |
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

<!-- BEGIN learnings-index (auto-generated by learnings-sync.sh — do not edit by hand) -->
## Learnings index

Non-obvious knowledge already discovered in this repo. **Before implementing or
debugging in an area below, read that file first** — it exists because something
here was not what it looked like.

### Rules — these always apply
- warming `ProcessCameraProvider.getInstance()` is not a camera prewarm — only `bindToLifecycle` starts the clock that matters. <sub>(`learnings/ar-entry-cost-is-the-first-frame.md`)</sub>
- every value that positions something in the AR overlay gets a second-stage filter — if you add a new one, filter it, and never point `adaptiveLowPass` at a bearing. <sub>(`learnings/ar-overlay-filtering-asymmetry.md`)</sub>
- a live region's description must change only as often as you want TalkBack to speak — never once per degree; and never add a sibling, even a zero-size one, to a `SpaceBetween` parent. <sub>(`learnings/compose-semantics-and-layout-traps.md`)</sub>
- persist the declination angle and its timestamp — never the latitude and longitude it came from. <sub>(`learnings/declination-cache-and-bearing-lock.md`)</sub>
- never `exclude(group = "androidx.work")` from Glance, never put `<Space>` in a RemoteViews layout, and never ship an `<appwidget-provider>` without `android:initialLayout`. <sub>(`learnings/glance-widget-three-silent-failures.md`)</sub>
- for any tilt reading taken while the phone is upright, derive the angle from the gravity vector — never from `getOrientation`'s roll, which is degenerate at exactly that pitch. <sub>(`learnings/level-upright-posture-and-euler-degeneracy.md`)</sub>
- never branch on `isGranted` + `shouldShowRationale` alone — pair them with the persisted marker in `util/PermissionAskLog.kt`, or the denied-for-good user gets a button that cannot work. <sub>(`learnings/permission-permanent-denial.md`)</sub>
- the public URL is `https://minimal-compass.vercel.app` — never the `-sarvinshrivastavas-projects.vercel.app` form, which 302s to a Vercel SSO login. <sub>(`learnings/vercel-alias-deployment-protection.md`)</sub>

### Reference — read when touching that area
- `GeomagneticField` still ships WMM-2020, and that is not a bug to fix — `learnings/geomagnetic-field-is-wmm-2020.md`
- Glance app-widget gotchas — `learnings/glance-widget-gotchas.md`
- Play release build gotchas — `learnings/play-release-gotchas.md`
- Sensor orientation, remapping, and what each value actually means — `learnings/sensor-orientation-and-remap.md`

<!-- END learnings-index -->

---

## Bearing lock, calibration, and the level's two postures

Three behaviours added after the 1.0.0 review. Each has a rule that is easy to
undo by accident.

**Bearing lock** — `CompassViewModel.lockedBearing` is held in *display* space,
so `toggleTrueNorth()` must shift it by the same declination it shifts the
heading by. Skipping that silently rotates the direction the user is following,
and both numbers still look plausible afterwards. The readout uses
`shortestArcDelta`, so a lock at 002° while heading 358° reads `4° RIGHT`.

**Calibration** — `CalibrationDialog` is never a gate. The compass keeps
running behind it and it can always be dismissed, because "the compass never
waits for anything" is the rule the app is built on and a degraded reading is
still a reading. It opens automatically at most once per session, and only
after `Constants.Ui.CALIBRATION_PROMPT_DELAY_MS` of sustained `UNRELIABLE` —
accuracy *starts* `UNRELIABLE` by design, so prompting immediately would put a
dialog in front of every cold start.

**Level postures** — `TiltScreen` picks its instrument from
`CompassViewModel.levelPosture`, never by re-deriving an angle. Flat gets the
bullseye (`BubbleLevelIndicator`), upright gets the single-axis rule
(`PlumbLevelIndicator`). `LevelPostureGate` applies hysteresis for the same
reason `ArTriggerGate` does.

**Remembered declination** — cached as an angle plus a timestamp and nothing
else. Never cache the latitude and longitude: that turns `compass_prefs` into a
location history and contradicts the privacy policy and the Data Safety form.

## Screenshot pipeline

`docs/launch/assets/tools/make_store.py` builds the Play phone screenshots in
three layouts (full-bleed hero, device-framed, type-only) per
`docs/launch/aso-screenshot-plan.md`. It **exits non-zero** while any frame is
flagged `pending` or its raw is missing, and prints what still needs a
real-device capture. Three states cannot be produced on an emulator: true north
(no location fix resolves), the `CALIBRATE` warning (emulated magnetometer
always reports `HIGH`), and a real AR horizon.
