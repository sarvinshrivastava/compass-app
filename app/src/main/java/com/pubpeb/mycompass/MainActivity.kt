package com.pubpeb.mycompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pubpeb.mycompass.ui.MainScreen
import com.pubpeb.mycompass.ui.theme.MyCompassTheme
import com.pubpeb.mycompass.widget.CompassWidgetUpdateWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Fullscreen / immersive mode ──────────────────────────────────────
        // enableEdgeToEdge() sets up the window for edge-to-edge drawing and
        // handles the gesture-handle colour on API 29+.
        enableEdgeToEdge()
        // Draw the app content behind the system bars so nothing is reserved.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Hide status bar + navigation bar. On swipe they reappear temporarily
        // (transient) and then auto-hide again — classic immersive sticky mode.
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MyCompassTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }

        // Ensure the home-screen widget stays fresh (no-op if already scheduled)
        CompassWidgetUpdateWorker.enqueuePeriodicWork(this)
    }
}
