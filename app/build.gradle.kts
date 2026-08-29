plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    // Code package and published identity are deliberately identical, and both
    // match the app registered in the Firebase project — google-services matches
    // on applicationId, and a mismatch there fails the build outright.
    namespace = "com.pubpeb.compass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pubpeb.compass"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing, sourced from Gradle properties or the environment so no
    // keystore path, password or alias is ever committed. Put these in
    // ~/.gradle/gradle.properties (NOT the repo's):
    //   COMPASS_STORE_FILE / COMPASS_STORE_PASSWORD
    //   COMPASS_KEY_ALIAS  / COMPASS_KEY_PASSWORD
    // Absent, the release build stays unsigned rather than failing, so anyone
    // can still verify R8 output without holding the signing key.
    val storeFilePath: String? =
        (findProperty("COMPASS_STORE_FILE") as String?) ?: System.getenv("COMPASS_STORE_FILE")

    signingConfigs {
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword =
                    (findProperty("COMPASS_STORE_PASSWORD") as String?)
                        ?: System.getenv("COMPASS_STORE_PASSWORD")
                keyAlias =
                    (findProperty("COMPASS_KEY_ALIAS") as String?)
                        ?: System.getenv("COMPASS_KEY_ALIAS")
                keyPassword =
                    (findProperty("COMPASS_KEY_PASSWORD") as String?)
                        ?: System.getenv("COMPASS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Shrink and obfuscate. The Glance widget is resolved reflectively
            // by the launcher, so proguard-rules.pro carries explicit keeps for
            // it — without those the tile fails silently rather than crashing,
            // which is very easy to ship unnoticed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // R8 renames everything, so a real release must upload its mapping
            // file or every Crashlytics stack trace arrives obfuscated.
            //
            // But this is NOT keyed on the build type. The upload is independent
            // of signing, so with it always on, every local `assembleRelease` —
            // a developer checking R8 rules, a CI dry run — pushed an orphan
            // mapping file to the production Crashlytics project for a build ID
            // that will never produce a crash. Gate it on an explicit signal
            // instead, so only a deliberate release publishes.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = System.getenv("COMPASS_RELEASE") != null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

/**
 * google-services.json is deliberately not committed (see .gitignore), so a
 * fresh clone hits the Google Services plugin's own error, which names the file
 * but not where to obtain it. Fail first with something actionable.
 */
// Captured at CONFIGURATION time. Calling file(...) inside doFirst would reach
// through the Project receiver at execution time — the canonical configuration
// cache violation — and once CC is enabled a cryptic CC error would replace the
// friendly message this guard exists to provide.
val googleServicesFile = layout.projectDirectory.file("google-services.json")

tasks.matching { it.name.contains("GoogleServices") }.configureEach {
    doFirst {
        if (!googleServicesFile.asFile.exists()) {
            throw GradleException(
                """
                Missing app/google-services.json.

                This file is intentionally untracked. Download it from the
                Firebase console for the Android app registered as
                "com.pubpeb.compass" and place it at app/google-services.json.

                Full steps: see the Setup section of README.md
                """.trimIndent()
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose BOM — versions of all compose libs managed here
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)                     // HorizontalPager
    implementation(libs.androidx.compose.material.icons.extended)        // Icons.Outlined.Settings etc.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // ProcessLifecycleOwner — process-wide foreground boundaries that, unlike an
    // Activity's, do not fire on configuration change.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Accompanist permissions
    implementation(libs.accompanist.permissions)

    // Google Play Services — Location (for GeomagneticField GPS fix)
    implementation(libs.play.services.location)

    // Glance widget.
    // Glance 1.1.1's POM declares androidx.work, but its own compiled code
    // contains zero references to it — the dependency is vestigial. Excluding
    // it removes WorkManagerInitializer from the merged manifest along with
    // the scheduler services, receivers and the cold-start cost they carry.
    implementation(libs.androidx.glance.appwidget) {
        exclude(group = "androidx.work")
    }
    implementation(libs.androidx.glance.material3) {
        exclude(group = "androidx.work")
    }

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Firebase — Crashlytics for stability, Analytics for the six product events.
    // Both are free at unlimited volume on the Spark plan.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
