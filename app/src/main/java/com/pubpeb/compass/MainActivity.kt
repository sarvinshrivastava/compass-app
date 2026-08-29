package com.pubpeb.compass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pubpeb.compass.telemetry.Telemetry
import com.pubpeb.compass.ui.MainScreen
import com.pubpeb.compass.ui.theme.CompassTheme
import com.pubpeb.compass.ui.theme.resolvePalette
import com.pubpeb.compass.viewmodel.CompassViewModel

// TODO Phase 2 – ARCore hook:
//   Add `implementation("com.google.ar:core:<version>")` to build.gradle.kts.
//   Create an ArSessionLifecycleObserver that initialises / pauses / resumes
//   the ArSession in lockstep with this Activity's lifecycle using
//   lifecycle.addObserver(ArSessionLifecycleObserver(this)).
//   Expose ArSession via a singleton or Hilt-provided dependency.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Window setup ─────────────────────────────────────────────────────
        // Draw behind the system bars, but keep them visible. This is an
        // outdoor navigation app: hiding the status bar takes away the clock
        // and the battery readout — the two things someone walking a trail
        // most wants — and the transient-bar swipe that brings them back
        // fights the pager for the same horizontal gesture.
        Telemetry.init(applicationContext)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // MainScreen consumes WindowInsets.safeDrawing itself.

        setContent {
            val vm: CompassViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()

            // The bars are transparent, so their icons sit directly on whatever
            // the app is painting. Derive their tint from the palette actually
            // in force — otherwise Daylight mode puts white icons on white.
            val palette = resolvePalette(mode)
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }

            CompassTheme(mode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
    }
}
