# Minimal Compass — Technical Readiness for Google Play

Audit date: 2026-09-05
Commit audited: `10b5e23` (branch `redesign/audit-p0-p1`)
Artifact under test: `app/build/outputs/bundle/release/app-release.aab`
Scope: technical stability and Play release readiness only. Store listing, screenshots,
pricing and marketing are out of scope.

---

## Verdict

**Do not ship yet — but the gap is small and well-defined.** The app itself is in
unusually good shape: the sensor lifecycle is correctly scoped to `repeatOnLifecycle`,
every long-lived listener is paired with a teardown, R8 runs with correct Glance keeps, a
baseline profile is embedded, and all bundled native libraries are already 16 KB aligned.
Unit tests pass and `lintVitalRelease` passes. What blocks upload is not code quality: the
app targets API 35 and Google Play has required API 36 for all new submissions since
2026-08-31, and the release AAB is **unsigned** because no upload keystore exists on this
machine. Both were verified directly — `jarsigner -verify` reports `jar is unsigned`, and a
test bump to `compileSdk`/`targetSdk = 36` produced a successful release bundle in 1m14s,
which disproves the "36 breaks this build" note in `CLAUDE.md`. Beyond those two, the
submission itself is gated on a privacy policy URL and the IARC questionnaire, and the
whole launch is gated on the 12-tester / 14-day closed test that personal developer
accounts must complete before production access unlocks. Realistically that is a few hours
of engineering plus about three weeks of calendar time. On the runtime side, the findings
that will cost stars are two permanently-denied-permission dead ends and a main-thread
blocking call in the camera teardown path — none are crashes, all are contained fixes.

---

## BLOCKERS

Must be fixed before the AAB is uploaded.

### B1. targetSdk 35 is below the mandatory level for new apps

**Evidence** — `app/build.gradle.kts:14,19`:
```kotlin
compileSdk = 35
targetSdk = 35
```
Merged release manifest confirms it reaches the artifact:
```
$ grep -oE 'android:targetSdkVersion="[^"]*"' \
    app/build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml
android:targetSdkVersion="35"
```
Google's requirement, effective 2026-08-31 (today is 2026-09-05, so this is already live):
> "New apps and app updates must target Android 16 (API level 36) or higher to be
> submitted to Google Play"

Play will reject the upload outright.

**This is a two-line fix, and it is verified to work.** `CLAUDE.md`'s Key Versions table
says *"compileSdk / targetSdk 35 — Do not use `release(36)` DSL — causes build errors"*,
and `app/proguard-rules.pro:41-42` repeats the claim. That note is stale and is now
actively harmful. I tested it:

```
$ sed -i '' 's/compileSdk = 35/compileSdk = 36/; s/targetSdk = 35/targetSdk = 36/' app/build.gradle.kts
$ ./gradlew bundleRelease
BUILD SUCCESSFUL in 1m 14s
$ grep -oE 'android:targetSdkVersion="[^"]*"' .../AndroidManifest.xml
android:targetSdkVersion="36"
```
R8, lintVitalRelease, resource shrinking and bundling all succeeded. The warning in
`CLAUDE.md` at most applies to the AGP block-DSL form `compileSdk { release(36) }`; the
plain integer assignment is fine. **I reverted the change** — the working tree is back at
35, so this still needs to be applied deliberately.

**Fix:** set `compileSdk = 36` and `targetSdk = 36` in `app/build.gradle.kts:14,19`. Then
re-read the Android 16 behaviour changes that apply at targetSdk 36 — chiefly mandatory
edge-to-edge (already done, `MainActivity.kt:41` calls `enableEdgeToEdge()`) and the
stricter foreground-service and intent rules (this app has neither). Re-run the manual
smoke test in T1 afterwards.

If for any reason 36 cannot be adopted, an extension to 2026-11-01 can be requested from
the Policy Status page in Play Console — but the extension wording is written around
already-published apps needing more time, and it is not confirmed to cover an app that has
never been published. Do not plan around it.

### B2. The release AAB is unsigned

**Evidence:**
```
$ jarsigner -verify -verbose:summary app/build/outputs/bundle/release/app-release.aab
no manifest.
jar is unsigned.

$ grep -i COMPASS_STORE ~/.gradle/gradle.properties
(no match)
$ echo $COMPASS_STORE_FILE
(empty)
```
`app/build.gradle.kts:36-51` only creates the `release` signing config when
`COMPASS_STORE_FILE` resolves to an existing file. It does not, so
`signingConfigs.findByName("release")` returns `null` at line 55 and the `signReleaseBundle`
task runs as a no-op. The build reports SUCCESS and emits an 8.1 MB AAB that Play will
reject. This is exactly the failure mode the config's own comment predicts, and it is silent.

**Fix:**
1. Create the upload keystore, outside the repo:
   ```
   keytool -genkeypair -v -keystore ~/keys/minimal-compass-upload.jks \
     -storetype JKS -keyalg RSA -keysize 4096 -validity 10000 -alias upload
   ```
2. Put the four properties in `~/.gradle/gradle.properties` (never the repo), per
   `README.md`'s Releasing section.
3. Back the `.jks` up to at least two independent places, encrypted — a password manager
   attachment and an encrypted cloud folder. Record the store password, key password and
   alias with it; a keystore without its passwords is not a backup.
4. Re-run `./gradlew bundleRelease` and re-verify with `jarsigner -verify`. It must print
   `jar verified`.
5. Consider making the config fail loudly instead of silently downgrading — a
   `gradle.properties` typo currently produces an unsigned artifact with no warning:
   ```kotlin
   if (gradle.startParameter.taskNames.any { it.contains("Release") } && storeFilePath == null) {
       throw GradleException("Release build requires COMPASS_STORE_FILE")
   }
   ```

### B3. No privacy policy URL exists

The app requests `CAMERA` (`AndroidManifest.xml:41`) and `ACCESS_COARSE_LOCATION`
(`AndroidManifest.xml:13`), and ships Firebase Analytics and Crashlytics
(`app/build.gradle.kts:178-180`, wired in `telemetry/Telemetry.kt`). A privacy policy URL is
mandatory in Play Console for any app that accesses personal or sensitive data, and the
requirement is triggered by requesting the permission and shipping the SDK, not by whether
data actually leaves the device. There is no policy URL anywhere in the repo and no
in-app link to one.

**Fix:** publish a policy at a stable public URL before submission. It must state, at
minimum: that camera frames are used only on-device for the AR overlay and are never
stored or transmitted; that approximate location is used only to compute magnetic
declination on-device and the position itself is never transmitted (this is true and
enforced — `sensor/DeclinationRepository.kt:68-75` returns only the derived scalar); that
Firebase Analytics collects anonymous product-usage events and Crashlytics collects crash
diagnostics, both processed by Google; that no advertising ID is collected (the manifest
removes it at `AndroidManifest.xml:30-38`); and a contact address. Declare the URL under
App content → Privacy policy, and link it from the in-app settings dialog
(`ui/CompassScreen.kt:311-366`, currently a single switch). **The policy and the Data
Safety form must say the same thing** — an inconsistency between them is one of the most
common rejection causes.

### B4. Play Console submission forms are not complete

None of these exist yet and each independently prevents a production submission:
- **Data Safety form.** See the checklist below for the exact answers this code implies.
- **Content rating (IARC) questionnaire.** Mandatory for every app regardless of category.
  A compass utility with no user content, no ads and no purchases will land at
  Everyone / PEGI 3.
- **Ads declaration.** Answer "No". Firebase Analytics is not an ad SDK; do not conflate
  them. The `AD_ID` permission is explicitly removed, which supports this answer.
- **Target audience.** Not child-directed; do not opt into Designed for Families.

---

## SHOULD-FIX

Before v1 if time allows, or in the first patch.

### S1. `./gradlew lint` fails, and it aborts the build

```
$ ./gradlew lint bundleRelease
> Task :app:lintDebug FAILED
Lint found 5 errors, 42 warnings.
Execution failed for task ':app:lintDebug'.
> Lint found errors in the project; aborting build.
```
The AAB was never produced by that invocation — `bundleRelease` had to be run separately.
Play itself does not run this check (`lintVitalRelease` passes), so it is not an upload
blocker, but it means the conventional pre-release gate is red and a solo developer's only
static safety net is switched off.

Four of the five errors are one root cause, at `ui/ArOverlayScreen.kt:423, 423, 424, 427`:
```
Error: This declaration is opt-in and its usage should be marked with
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
                    val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
```
This is not cosmetic. The FOV query that positions every AR label depends on an
experimental CameraX API; a CameraX version bump can change it and the compiler will not
warn again once the opt-in is silently suppressed. **Fix:** add
`@OptIn(ExperimentalCamera2Interop::class)` to the `CameraPreview` composable
(`ui/ArOverlayScreen.kt:385`) — a deliberate, documented acknowledgement — and add a unit
or manual check that a wrong FOV is noticed.

The fifth error, at `res/layout/widget_preview.xml:20`:
```
Error: Must use app:tint instead of android:tint [UseAppTint]
```
**Do not apply the suggested fix.** That layout is inflated by the launcher through
RemoteViews, which cannot resolve AppCompat attributes — `app:tint` would silently fail and
the widget picker preview would lose its red mark. The correct action is
`tools:ignore="UseAppTint"` on that `ImageView`.

### S2. Permanently-denied camera permission dead-ends the user

`ui/ArOverlayScreen.kt:145-173`. The `when` has three branches: granted, `shouldShowRationale`,
and `else`. On Android 11+ a permission denied twice becomes permanently denied, and in that
state `isGranted` is false **and** `shouldShowRationale` is false — so it falls to the `else`
branch at line 164, which shows the first-run copy "Camera Access / Grant Camera". Tapping
that button calls `cameraPermission.launchPermissionRequest()` at line 170, which the system
auto-denies without ever showing a dialog. The user taps a button that visibly does nothing,
forever, with no explanation. This is a textbook one-star path.

**Fix:** distinguish "never asked" from "asked and permanently denied" by recording the first
request in `rememberSaveable`:
```kotlin
var hasAsked by rememberSaveable { mutableStateOf(false) }
// ... in the else branch, when hasAsked is true:
//   button label "Open Settings", onRequest ->
//   context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
//       Uri.fromParts("package", context.packageName, null)))
```

### S3. Same dead end for location, with no feedback at all

`ui/CompassScreen.kt:152-153` and `:113`. Tapping the MAG tag calls `onRequestLocation()`
→ `launchPermissionRequest()`. Once location is permanently denied this is a no-op, and
unlike the camera path there is not even a screen explaining anything — the tag simply never
changes to TRUE. The user concludes true north is broken.

**Fix:** same settings fallback as S2, or at minimum a one-line inline note under the tag
("Location permission is off — enable it in Settings for true north"). Note the analytics at
`ui/CompassScreen.kt:98-102` also miss this case: `onLocationOutcome` only fires when
`shouldShowRationale || locationGranted`, so permanent denials are never counted and the
"did COARSE improve grant rate?" question the event exists to answer will be silently biased.

### S4. Blocking `.get()` on the main thread in camera teardown

`ui/ArOverlayScreen.kt:465-469`:
```kotlin
onDispose {
    try {
        ProcessCameraProvider.getInstance(context).get().unbindAll()
    } catch (_: Exception) { }
}
```
`onDispose` runs on the main thread and `ListenableFuture.get()` blocks it. In the normal
case the provider is already resolved and this returns instantly, but on a first
initialisation that has not completed, or when the camera service is wedged, this blocks the
UI thread with no timeout — an ANR while leaving AR mode. **Fix:** capture the resolved
provider from the bind listener into a local `var` and call `provider?.unbindAll()` in
`onDispose`, with no blocking call.

### S5. The camera bind listener can fire after disposal

`ui/ArOverlayScreen.kt:403-419` registers `future.addListener` but nothing cancels it. If the
user leaves AR before the provider resolves, `onDispose` (line 465) runs `unbindAll()` first,
then the listener fires and calls `provider.bindToLifecycle(...)` at line 412 and
`onCameraBound(camera)` at line 419 — re-binding the camera *after* teardown, and writing to
state belonging to a composition that is gone. The result is a camera left bound with no
owner: the privacy indicator stays lit, the battery drains, and other apps see the camera as
in use. It also defeats the torch-off cleanup at line 192, which reads a `camera` that was
still null at dispose time.

**Fix:** a disposal flag checked inside the listener:
```kotlin
var disposed = false
future.addListener({ if (disposed) return@addListener; /* ... */ }, mainExecutor)
onDispose { disposed = true; provider?.unbindAll() }
```

### S6. CameraX bind failure is invisible to the user

`ui/ArOverlayScreen.kt:456-459` catches every exception from the bind, logs it, and falls back
to the default FOV. That `catch` is the only handler for the three realistic field failures:
another app holds the camera, the device has no back camera, or the HAL errors. In all three
the user gets a black screen with cardinal labels floating on it and no explanation.

**Fix:** hoist an error state out of the catch and render a short message over the black
preview — "Camera unavailable. Another app may be using it." — with the existing exit button
still reachable. Three lines of state, and it converts a "the AR mode is broken" review into a
comprehensible message.

### S7. Spurious Crashlytics non-fatals on resume

`viewmodel/CompassViewModel.kt:338-346` and `:306-310`. `lastEmissionAt` is never reset when
the shared sensor flow restarts. On returning to the app after more than three seconds away,
the watchdog's first tick (1 s after `repeatOnLifecycle(STARTED)` re-launches it) compares
`now` against the *previous session's* `lastEmissionAt`, finds it stale, and calls
`publishAccuracy(NO_SIGNAL)` — which fires `Telemetry.reportSensorStall()` because the last
logged accuracy was, say, `HIGH`. Real sensor data usually arrives within ~60 ms at
`SENSOR_DELAY_UI` and beats the 1 s tick, so this only triggers when sensor registration is
slow — but that is common on cold registration on mid-tier hardware.

The cost is not a crash: it is that the `SensorStalledException` non-fatal — the single
diagnostic this app added to detect a real, invisible failure — becomes noise, and the
genuine cases get lost in it. **Fix:** stamp `lastEmissionAt = SystemClock.elapsedRealtime()`
at the top of `watchForStaleSensor()` so each subscription gets a grace period.

### S8. `sensorAvailable` is computed and never used

`viewmodel/CompassViewModel.kt:125-126` exposes `sensorAvailable` from
`sensor/SensorRepository.kt:152-154`, and no composable collects it. On a device with no
magnetometer the compass page shows `0°  NORTH` and, three seconds later, a
`NO SENSOR SIGNAL` tag — technically honest, but it never says the device simply does not
have a compass.

The manifest hard-requires the sensor (`AndroidManifest.xml:19-21`,
`android.hardware.sensor.compass` `required="true"`), so Play filters those devices out of the
listing entirely. **That filter is correct and should stay** — an app called Compass that
cannot find north is worse than an app that is not installable. But it does not cover
sideloads, `adb install`, emulators, or devices that declare the feature with a broken
sensor. **Fix:** consume `sensorAvailable` and render a terminal "This device has no
magnetometer" state instead of a confident zero.

Worth knowing about the install-base cost while you are here: the hard requirement also hides
the app from entry-tier devices that ship without a magnetometer — a non-trivial slice of the
Indian market. The bubble level (`ui/TiltScreen.kt`) and the torch work fine without one and
are currently gated away with it. Keep the filter for v1; revisit only if the level is ever
positioned as a standalone feature.

### S9. Mapping file retention is not handled

`app/build/outputs/mapping/release/mapping.txt` is 47.9 MB and lives under `build/`, which is
gitignored and destroyed by `./gradlew clean`. The Crashlytics upload is gated on
`COMPASS_RELEASE` (`app/build.gradle.kts:75-77`), which is a good decision — but if you forget
the variable on the real release build **and** then clean, every crash report from that
version is permanently unreadable. **Fix:** in the release script (S10), copy
`mapping.txt` to `docs/launch/mappings/<versionCode>-mapping.txt` (or any location outside
`build/`) as an unconditional step, before anything can clean it.

### S10. No release script, and the `COMPASS_RELEASE` gate is easy to forget

There is no CI, which for a solo developer on a two-page compass app is a reasonable call — a
GitHub Actions rig would also need `google-services.json` as a secret, since it is gitignored
(`.gitignore`, "Google Services" section). What is worth ten lines of bash is a single
release command that cannot be run half-right:

```bash
#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export COMPASS_RELEASE=1
./gradlew clean test lint bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab | grep -q "jar verified"
VC=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
mkdir -p docs/launch/mappings
cp app/build/outputs/mapping/release/mapping.txt "docs/launch/mappings/${VC}-mapping.txt"
```
Note `lint` is in there — which is why S1 needs fixing first.

### S11. Crashlytics has never been verified end-to-end

`telemetry/Telemetry.kt` wires Crashlytics correctly (custom key at `:175-177`, non-fatal at
`:186-188`), but nothing in the repo shows a crash has ever actually reached the Firebase
console. A misconfigured `google-services.json`, a wrong project, or a broken upload would all
look exactly like "no crashes yet". **Fix, before the closed test:** force one crash from a
debug build (`throw RuntimeException("Crashlytics smoke test")` behind a long-press somewhere,
or via `adb shell am crash`), confirm it appears in the console, then remove it. Then repeat
once with a `COMPASS_RELEASE=1` release build to confirm the mapping upload deobfuscates it —
that is the only way to know the whole chain works before you need it.

### S12. Uncomment the keystore ignores before the keystore exists

`.gitignore` ships the standard commented-out block:
```
# Uncomment the following lines if you do not want to check your keystore files in,
# *.jks
# *.keystore
```
Uncomment both lines now, while there is no keystore to accidentally commit. A signing key in
git history cannot be removed.

### S13. `allowBackup` and data extraction rules are correct — no change needed

Recorded here because it is worth not second-guessing later. `AndroidManifest.xml:45-46` sets
`allowBackup="false"` plus `dataExtractionRules`, and `res/xml/data_extraction_rules.xml`
excludes both `cloud-backup` and `device-transfer`. That combination is right: `allowBackup`
covers cloud backup on every API level, the rules file covers Android 12+ device-to-device
transfer that `allowBackup` does not. Lint's `DataExtractionRules` warning asking for
`fullBackupContent` is a **false positive** here — with `allowBackup="false"` there is no
pre-31 backup to configure. Ignore it.

---

## NICE-TO-HAVE

### N1. All user-facing strings are hardcoded Kotlin literals
`res/values/strings.xml` holds two strings. Every other string is inline: the settings dialog
(`ui/CompassScreen.kt:327, 341, 346`), the confidence labels (`:479-481`), all camera
permission copy (`ui/ArOverlayScreen.kt:156-171`), the axis labels
(`ui/TiltScreen.kt:66-68`), and every `contentDescription`. This makes localisation
impossible and puts the strings outside lint's `MissingTranslation` coverage. Not a Play
issue; a hard ceiling on ever shipping Hindi or regional Indian languages.

### N2. Settings dialog does not survive rotation
`ui/CompassScreen.kt:140` uses `remember`, not `rememberSaveable`, so the dialog closes on
configuration change. Contrast with `arModeManual` at `ui/MainScreen.kt:113`, which correctly
uses `rememberSaveable` and documents why. One-word fix.

### N3. Pager pages are not announced to TalkBack
`ui/MainScreen.kt:157-167`. Neither page has a pane title or content description, so swiping
between the compass and the level announces nothing about what changed. The pager dots are
deliberately and correctly cleared from semantics (`:194`), which leaves nothing to fill the
gap. Everything else in the app is well covered: live regions on both heading readouts
(`ui/CompassScreen.kt:405-410`, `ui/ArOverlayScreen.kt:329-332`), a description on the level
canvas (`ui/BubbleLevelIndicator.kt:158`), real 48 dp touch targets on the MAG tag
(`ui/CompassScreen.kt:443`) and the exit button (`ui/ArOverlayScreen.kt:279`), 56 dp on the
torch (`:661`), and explicit WCAG contrast reasoning in the palettes
(`ui/theme/Theme.kt:74-81`). Lint reported zero accessibility findings.

### N4. Dead code left from the removed widget DataStore migration
`viewmodel/CompassViewModel.kt:19, 26, 30` import `java.io.File`, `launchIn` and
`withContext`, none of which are used. `Constants.kt:149` (`KEY_LEGACY_STORE_PURGED`) and
`:156-158` (`Legacy.WIDGET_DATASTORE_FILE`) are referenced nowhere. The purge those describe
("deleted once on first launch of this version") does not happen. Harmless — the app has never
shipped, so no device has that file — but delete it rather than leave a documented migration
that is a no-op.

### N5. `Log` calls survive into release
Ten sites across `viewmodel/CompassViewModel.kt`, `ui/ArOverlayScreen.kt` and
`sensor/DeclinationRepository.kt`. None leak anything sensitive — the closest,
`"declination acquired: $declination"` (`CompassViewModel.kt:253`), logs a magnetic bearing,
not a position, and `DeclinationRepository` deliberately never logs the fix itself. Optional
tidy-up: `-assumenosideeffects` for `Log.d`/`Log.v` in `proguard-rules.pro`, keeping `w`/`e`.

### N6. Main-thread `Settings.Global` read during composition
`ui/MotionUtil.kt:19-28` performs a ContentResolver binder call inside `remember`. Correctly
memoised and cheap, but it is a blocking IPC on the composition thread. Low risk; move to a
lazily-initialised application-scope value if it ever shows up in a trace.

### N7. Legacy AGP compatibility flags will break on AGP 10
`gradle.properties` sets `android.newDsl=false`, `android.builtInKotlin=false`,
`android.r8.optimizedResourceShrinking=false` and `android.defaults.buildfeatures.resvalues=true`.
Every one emits a deprecation warning saying it is removed in AGP 10. One of them is actively
opting out of better resource shrinking. Not urgent; will need doing before the next major AGP bump.

### N8. Dependency versions are well behind
Lint lists Compose BOM 2024.09.00 → 2026.08.00, CameraX 1.4.1 → 1.6.2, Kotlin 2.2.10 → 2.4.10,
lifecycle 2.8.6 → 2.11.0, Glance 1.1.1 → 1.2.0. Nothing is broken, and a large dependency bump
immediately before a first release is the wrong risk trade. Do it in the first patch cycle,
one group at a time, with the T1 smoke test after each.

---

## Play submission technical checklist

Build and artifact
- [ ] `compileSdk = 36` and `targetSdk = 36` in `app/build.gradle.kts` (B1) — verified to build
- [ ] `app/proguard-rules.pro:41-42` comment corrected — it repeats the stale "36 breaks the build" claim
- [ ] Upload keystore created outside the repo, 4096-bit RSA, 10000-day validity (B2)
- [ ] `COMPASS_STORE_FILE` / `_PASSWORD` / `COMPASS_KEY_ALIAS` / `_PASSWORD` in `~/.gradle/gradle.properties`
- [ ] Keystore + all three secrets backed up to two independent encrypted locations (B2)
- [ ] `*.jks` and `*.keystore` uncommented in `.gitignore` (S12)
- [ ] `jarsigner -verify app-release.aab` prints `jar verified` — not `jar is unsigned`
- [ ] `./gradlew lint` exits 0 (S1: `@OptIn(ExperimentalCamera2Interop::class)` + `tools:ignore="UseAppTint"`)
- [ ] `./gradlew test` passes
- [ ] Built with `COMPASS_RELEASE=1` so the Crashlytics mapping uploads
- [ ] `mapping.txt` archived outside `build/`, named by versionCode (S9)
- [ ] `versionCode` incremented and never reused (currently 2; must strictly increase forever)

Verified already — no action needed
- [x] AAB produced, 8.1 MB (`app/build/outputs/bundle/release/app-release.aab`)
- [x] R8 shrinking and obfuscation active (`mapping.txt`, `usage.txt`, `seeds.txt` all present)
- [x] Resource shrinking active (`isShrinkResources = true`)
- [x] Baseline profile embedded (`BUNDLE-METADATA/.../baseline.prof`)
- [x] All 16 bundled `.so` files 16 KB aligned (LOAD segments `align 2**14`, arm64-v8a and x86_64 checked)
- [x] `extractNativeLibs="false"` in the merged release manifest
- [x] No `android:debuggable` in the release manifest
- [x] Zero `NewApi` / `InlinedApi` lint findings at minSdk 24
- [x] `lintVitalRelease` passes
- [x] Advertising ID permission removed from the merged manifest

Play Console — App content
- [ ] Privacy policy live at a public URL and entered in Play Console (B3)
- [ ] Privacy policy also linked from the in-app settings dialog
- [ ] Data Safety form completed and consistent with the policy (B4), specifically:
  - [ ] App activity → App interactions: **collected**, not linked to identity, not shared (Firebase Analytics, 7 events in `telemetry/Telemetry.kt:200-206`)
  - [ ] App info and performance → Crash logs: **collected**, not linked to identity (Crashlytics)
  - [ ] App info and performance → Diagnostics: **collected** (Crashlytics device/session state)
  - [ ] Device or other IDs: **not collected** — `AD_ID` is removed at `AndroidManifest.xml:30-38` and ad personalisation is disabled at `:59-64`
  - [ ] Photos and videos: **not collected** — camera frames are rendered on-device only and never written or transmitted (`ui/ArOverlayScreen.kt` uses `Preview` only, no `ImageCapture`, no `VideoCapture`)
  - [ ] Location → Approximate location: judgement call. Your own code does not transmit it — `sensor/DeclinationRepository.kt:68-75` returns only the derived declination scalar and discards the fix. But Firebase Analytics derives coarse geography from the request IP server-side. The safe, defensible answer is **collected, not linked to identity, not shared**, attributed to Analytics rather than to app logic. An inconsistent answer here is a common rejection cause.
  - [ ] Data encrypted in transit: yes (Firebase uses TLS)
  - [ ] Users can request deletion: describe the Firebase retention setting you choose
- [ ] Content rating (IARC) questionnaire completed (B4)
- [ ] Ads declaration: **No** (B4)
- [ ] Target audience: not child-directed; do not opt into Designed for Families (B4)
- [ ] App access: no login exists, so no reviewer instructions needed — confirm no first-run gate blocks the crawler

Confirmed not required for this app
- [x] Location permissions declaration form — triggered by `ACCESS_BACKGROUND_LOCATION` only; this app uses foreground-only `ACCESS_COARSE_LOCATION`
- [x] Photo and Video permissions declaration — no `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`
- [x] Separate CAMERA declaration form — none exists; core-functionality justification applies and the AR overlay satisfies it

Testing and rollout
- [ ] Manual smoke test of the **signed release AAB** on real hardware (T1) — not a debug build
- [ ] Widget placed from the picker in that release build and confirmed to render and launch (R8 keeps are the failure mode, and it fails silently)
- [ ] Crashlytics smoke-tested end to end, including deobfuscation from a release build (S11)
- [ ] Play App Signing accepted on first upload (automatic for new apps)
- [ ] Internal testing track upload, pre-launch report reviewed (advisory only, not blocking)
- [ ] Closed test: **12 testers, opted in continuously for 14 days** — personal accounts created after 2023-11-13
- [ ] "Apply for production" submitted; review takes up to 7 days

Post-launch watch items
- [ ] 16 KB page size: already compliant, but re-verify before 2027-02-01, when updates without it are blocked
- [ ] Android Developer Verification: rolling out from 2026-09-30 in BR/ID/SG/TH, globally through 2027. India is not in the first wave. Play Console account identity verification is separate and already applies at signup.

---

## Testing gaps

Current coverage: five unit test files, all pure functions and the AR gate —
`ArTriggerGateTest` (118 lines, includes the 45°-oscillation regression),
`AngleUtilTest` (102), `TiltFromFlatTest` (65), `CardinalUtilTest` (44),
`CompassAccuracyTest` (43) — plus `ExampleInstrumentedTest` (23 lines), which asserts the
package name and nothing else. The maths is genuinely well covered. Everything with a
lifecycle, a permission, or a hardware dependency is not.

Prioritised by confidence bought per hour:

**T1. Manual smoke test of the signed release AAB — 30 minutes, no code, highest value by far.**
Nothing in the current suite would catch a broken widget, a mis-shrunk resource, or an R8
keep that silently stopped working — and per `proguard-rules.pro:25-27` the widget failure
mode is a blank tile, not a crash. Install the exact artifact you intend to upload
(`bundletool build-apks --local-testing`, or the internal testing track) and walk: compass
reads and rotates; MAG → TRUE after granting location; swipe to level and back; raise the
phone to trigger AR; AR opens with a live preview; torch on, torch off; exit AR and confirm
the torch is off; rotate on both pages; background and foreground the app; place the widget
from the picker, confirm it renders and launches the app. Repeat once with the camera held
by another app open in the background.

**T2. `CompassViewModel` heading logic — ~2 hours, highest-risk untested code.**
`computeDisplayAzimuth` (`:348-353`), the deadband path through `onSensorData` (`:263-288`),
`toggleTrueNorth` re-publishing (`:190-199`), and `updateDeclination`'s failure branch
(`:245-259`). This is pure Kotlin behind an injectable repository; it needs
`kotlinx-coroutines-test` and a `Dispatchers.setMain` rule, and `SensorRepository` /
`DeclinationRepository` extracted behind interfaces. A wrong branch here shows the user the
wrong bearing — which is the entire product.

**T3. Watchdog and `NO_SIGNAL` transitions — ~1 hour, same harness as T2.**
Directly covers S7 (the spurious stall non-fatal on resume) and pins the behaviour that
`learnings/sensor-orientation-and-remap.md` describes as the reason the watchdog exists.

**T4. Compose UI test for the permission dead ends — ~2 hours.**
`createComposeRule`, a fake permission state, assert that a permanently-denied camera state
offers a route to Settings rather than a dead button. This is the test that would have caught
S2 and S3, which are the two findings most likely to generate one-star reviews.

**Not worth the time:** more `AngleUtil` / `CardinalUtil` cases (already thorough), screenshot
tests, Espresso against the rose canvas, and an instrumented Glance render test — T1 covers
the widget far more cheaply than the test harness would.

---

## CLAUDE.md is stale — correct it before the next session

The brief flagged this; here is the specific list, because at least one entry is now actively
dangerous.

1. **"Do not use `release(36)` DSL — causes build errors"** (Key Versions table). As written
   this reads as "API 36 is impossible", and it is repeated at `proguard-rules.pro:41-42`.
   Verified false: `compileSdk = 36` / `targetSdk = 36` builds a release AAB successfully.
   Since 36 is now mandatory, this note would block the release if trusted. Fix it first.
2. **Firebase is entirely absent.** No mention of Crashlytics, Analytics, the `telemetry/`
   package, `Telemetry.kt`, the seven analytics events, `google-services.json`, or the
   `ProcessLifecycleOwner` session model — all of which exist and are load-bearing.
3. **Release engineering is absent.** No mention of the signing config, the `COMPASS_STORE_*`
   properties, `COMPASS_RELEASE`, or the mapping upload gate.
4. **R8 is absent.** No mention of `isMinifyEnabled`, `isShrinkResources`, or the ProGuard
   keeps — the Glance keeps in particular are exactly the kind of silent-failure knowledge
   `CLAUDE.md` exists to carry.
5. **Package layout is incomplete.** It omits `telemetry/`, `util/`,
   `sensor/ArTriggerGate.kt` and `sensor/DeclinationRepository.kt`.
6. The Learnings index at the bottom is present and accurate; leave it.

---

## Sources

| URL | What it confirmed |
|---|---|
| https://developer.android.com/google/play/requirements/target-sdk | Fetched 2026-09-05. "New apps and app updates must target Android 16 (API level 36) or higher to be submitted to Google Play", effective 2026-08-31. Extension to 2026-11-01 requestable via Play Console. Existing apps need API 35+ to stay available to new users. Basis for B1. |
| https://support.google.com/googleplay/android-developer/answer/11926878 | Play Console help mirror of the same target API schedule. |
| https://support.google.com/googleplay/android-developer/answer/14151465 | Fetched 2026-09-05. Personal developer accounts created after 2023-11-13 must "run a closed test for their app with a minimum of 12 testers who have been opted in continuously for at least 14 days" before applying for production. Production review "usually takes seven days or less". The timeline gate. |
| https://developer.android.com/guide/practices/page-sizes | 16 KB page size applies to apps targeting API 35+ with native libs, "either directly or indirectly through an SDK". Enforcement for updates from 2027-02-01. This app is already compliant — verified locally, all LOAD segments `align 2**14`. |
| https://support.google.com/googleplay/android-developer/answer/9842756 | AAB mandatory for new apps; Play App Signing enrolment automatic for new apps; upload key is resettable via Play Console if lost, app signing key is Google-held. Basis for B2's recovery guidance. |
| https://support.google.com/googleplay/android-developer/answer/10787469 | Data Safety: "collected" means transmitted off-device; data processed ephemerally in memory to service the immediate request need not be disclosed. Basis for the camera and location answers in the checklist. |
| https://support.google.com/googleplay/android-developer/answer/9799150 | The Location permissions declaration form is triggered by `ACCESS_BACKGROUND_LOCATION`; foreground-only coarse location does not require it. |
| https://support.google.com/googleplay/android-developer/answer/9844487 | Pre-launch report contents (stability, performance, accessibility, screenshots) and that its findings — accessibility included — are advisory recommendations, not publishing gates. |
| https://support.google.com/googleplay/android-developer/answer/10144311 | Privacy policy requirement for apps accessing personal or sensitive data. Secondary confirmation via search; not independently fetched. |
| https://support.google.com/googleplay/android-developer/answer/9898843 | IARC content rating questionnaire mandatory for all apps. Secondary confirmation via search; not independently fetched. |
| https://android-developers.googleblog.com/2026/03/android-developer-verification.html | Android Developer Verification programme; first-wave countries from 2026-09-30 (BR/ID/SG/TH), global through 2027. India not in the first wave. Cross-referenced against two secondary sources; treat rollout dates as provisional. |

Local command evidence cited in this report: `./gradlew test`, `./gradlew lint bundleRelease`,
`./gradlew bundleRelease`, `jarsigner -verify`, `objdump -p` on the extracted `.so` files, and
the SDK 36 build experiment (applied, verified, reverted — the working tree is unchanged apart
from this document).
