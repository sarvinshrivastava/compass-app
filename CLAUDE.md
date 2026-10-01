# Minimal Compass

Android compass app (Kotlin, Jetpack Compose, CameraX AR overlay, Glance widget).
Area-specific rules live in `.claude/rules/*.md` and load when you open matching files
(sensor fusion, AR overlay, theming, widget, release/telemetry, permissions, level/bearing,
screenshots, ARCore hooks). Learnings index: `.claude/rules/learnings/`.

## Build & run

Gradle needs Android Studio's JDK (system Java is v8):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

| Task | Command |
|---|---|
| Debug APK / install | `./gradlew assembleDebug` / `./gradlew installDebug` |
| Clean rebuild | `./gradlew clean assembleDebug` |
| Unit / instrumented tests | `./gradlew test` / `./gradlew connectedAndroidTest` |
| Release gate (before any upload) | `./gradlew test lint bundleRelease` |
| Stop daemon | `./gradlew --stop` |

The device connects over Wi-Fi ADB (TLS). Check `adb devices` before installing.

## Shape

`SensorManager` → `SensorRepository` (callbackFlow) → `CompassViewModel` (single shared
AndroidViewModel, StateFlow) → Compose UI via `collectAsStateWithLifecycle()`. Packages:
`sensor/` (repository + decision gates), `viewmodel/`, `ui/` (+ `theme/`), `widget/`,
`util/`, `telemetry/`. `MainScreen` is a 2-page `HorizontalPager` with decorative dots — no
`NavigationBar`, deliberately.

## Rules that apply everywhere

- **Every tunable value lives in `Constants.kt`**, grouped by subsystem. Local layout
  arithmetic stays inline.
- **Reuse `util/AngleUtil.kt`** (`shortestArcDelta`, `normalizedHeading`,
  `shouldPublishHeading`); never re-inline the shortest-arc formula. `adaptiveLowPass` is
  non-cyclic only — never point it at a bearing.
- **Never collect the sensor flow on `viewModelScope`.** The UI drives it with
  `repeatOnLifecycle(STARTED)`; `viewModelScope` kept the sensors on in the user's pocket.
- **Colours come from `appColors` inside a composable** (three palettes: Night, Day,
  Daylight). No file-level colour vals. **Red means north and nothing else.**
- **The compass never waits for anything:** location, calibration and permissions only ever
  upgrade a reading that is already on screen.
- **Privacy:** never cache latitude/longitude (declination is cached as angle + timestamp).
  The app ships Crashlytics and Analytics, so copy must never claim it is tracking-free.
- **Release trap:** without the `COMPASS_*` signing properties the release build still
  succeeds and emits an **unsigned** AAB. Verify with
  `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` → `jar verified`.
