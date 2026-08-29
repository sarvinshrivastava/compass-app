# Minimal Compass

An Android compass that opens, shows a bearing, and gets out of the way.

## Setup

**This project will not build until you supply `app/google-services.json`.**

That file is deliberately kept out of version control (see `.gitignore`), so a
fresh clone is missing it and the build stops immediately with:

```
Execution failed for task ':app:processDebugGoogleServices'.
> File google-services.json is missing.
  The Google Services Plugin cannot function without it.
```

To get one:

1. Open the [Firebase console](https://console.firebase.google.com/) and select
   the **minimal-compass** project.
2. Project settings → *Your apps* → the Android app registered as
   **`com.pubpeb.compass`** (this must match `applicationId` in
   `app/build.gradle.kts` exactly, or the plugin rejects it).
3. Download `google-services.json` and drop it at `app/google-services.json`.

If you are setting up a *new* Firebase project rather than joining the existing
one, register the app under the same package name and the file will work
unchanged.

### Build

System Java is too old for Gradle; use Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # R8 + resource shrinking (unsigned)
```

### Releasing

`assembleRelease` produces an **unsigned** artifact unless signing properties are
present. Put these in your *user* `~/.gradle/gradle.properties` — never in the
repo:

```properties
COMPASS_STORE_FILE=/absolute/path/to/upload-keystore.jks
COMPASS_STORE_PASSWORD=...
COMPASS_KEY_ALIAS=upload
COMPASS_KEY_PASSWORD=...
```

Back that keystore up somewhere you will not lose it: losing it means you can
never publish an update to the same Play listing again.

**A real release uploads to Firebase.** The Crashlytics mapping file — needed to
turn obfuscated stack traces back into readable ones — is uploaded only when
`COMPASS_RELEASE` is set:

```bash
COMPASS_RELEASE=1 ./gradlew assembleRelease
```

Without it, `assembleRelease` builds and shrinks exactly as it would for a
release but transmits nothing, so you can verify R8 output locally without
littering the production Crashlytics project with mapping files for builds that
will never exist.

## Architecture

See `CLAUDE.md` for the full architecture notes, and `learnings/` for the
non-obvious sensor and widget gotchas discovered while building this.
