# Play release build gotchas

> **Applies to:** `app/src/main/res/layout/widget_preview.xml`

Found while preparing the first Play Store submission (2026-09).

## `compileSdk = 36` does not break this build

- `CLAUDE.md` and `proguard-rules.pro` both carried a note saying API 36 caused
  build errors. It was false, and it nearly blocked a release: Play has required
  targetSdk 36 for **all new submissions since 2026-08-31**.
- Plain `compileSdk = 36` / `targetSdk = 36` builds clean — `test`, `lint`,
  `bundleRelease` all green. The note may have come from the AGP block DSL form
  `compileSdk { release(36) }`, which is a different thing.
- Once on 36, the `-dontwarn android.os.Profiling*` rules Crashlytics needed are
  obsolete: compileSdk 36 resolves those classes directly. Removed.

## An unsigned release AAB builds successfully and says nothing

- `signingConfigs` is only created when `COMPASS_STORE_FILE` points at a real
  file. Absent, `signingConfigs.findByName("release")` returns `null`,
  `signReleaseBundle` runs as a no-op, and the build prints `BUILD SUCCESSFUL`
  over a rejectable artifact.
- Always verify before uploading:
  ```
  jarsigner -verify app/build/outputs/bundle/release/app-release.aab
  ```
  It must print `jar verified`. `jar is unsigned` means the properties did not
  resolve — a typo in `~/.gradle/gradle.properties` looks identical to success.

## `kotlin.OptIn` does not satisfy lint's `UnsafeOptInUsageError`

- `androidx.camera.camera2.interop.ExperimentalCamera2Interop` is a **Java**
  `@RequiresOptIn` marker. `@OptIn(ExperimentalCamera2Interop::class)` (Kotlin's)
  compiles fine but leaves `./gradlew lint` failing with
  `UnsafeOptInUsageError`, which aborts the build.
- The fix is `@androidx.annotation.OptIn(...)`. Spell it fully qualified when the
  file also uses Kotlin's `@OptIn` for a Kotlin marker (this file does, for
  `ExperimentalPermissionsApi`) — importing both is a name clash.

## `UseAppTint` is wrong for a RemoteViews layout

- Lint demands `app:tint` over `android:tint` on `ImageView`. In
  `res/layout/widget_preview.xml` that advice is actively harmful: the launcher
  inflates it through RemoteViews, which cannot resolve AppCompat attributes, so
  `app:tint` silently does nothing and the widget picker loses its red mark.
- Correct action is `tools:ignore="UseAppTint"` with the reason in a comment,
  not the suggested fix and not a project-wide disable.
