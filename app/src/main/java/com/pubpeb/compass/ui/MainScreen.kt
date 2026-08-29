package com.pubpeb.compass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.view.WindowManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pubpeb.compass.Constants
import kotlinx.coroutines.launch
import com.pubpeb.compass.ui.theme.appColors
import com.pubpeb.compass.viewmodel.CompassViewModel

/**
 * Root composable called from [com.pubpeb.compass.MainActivity].
 *
 * Hosts two pages in a [HorizontalPager]:
 *  - Page 0: [CompassScreen] (compass rose + degree readout + settings)
 *  - Page 1: [TiltScreen]    (2-D bubble level indicator)
 *
 * There is deliberately **no bottom navigation bar**. A tab bar cost 80 dp —
 * about a tenth of the viewport — to give the bubble level equal billing with
 * the compass on the compass's own screen. The pages are now reached by swipe,
 * with a two-dot indicator as the only affordance, so the primary instrument
 * keeps the space.
 *
 * [viewModel] is a required parameter: [com.pubpeb.compass.MainActivity]
 * hoists it above the theme so toggling Daylight mode re-themes the whole app.
 */
@Composable
fun MainScreen(viewModel: CompassViewModel) {
    val colors     = appColors
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { Constants.Ui.PAGE_COUNT })

    // ── Sensor lifetime ──────────────────────────────────────────────────────
    // The hardware runs only while this screen is STARTED. Collecting on
    // viewModelScope instead kept the accelerometer and magnetometer registered
    // in the user's pocket, which was the app's largest battery cost.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.observeWhileVisible() }
            launch { viewModel.watchForStaleSensor() }
        }
    }

    // Each answers one question: does this page earn its place, and is the
    // auto-trigger wanted or does everyone reach for the button?
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.onLevelOpened()
    }

    // ── Keep the screen awake, but only where it earns it ────────────────────
    // Someone reading a bearing is looking at the screen without touching it,
    // so the display must not sleep mid-navigation. Setting the flag once in
    // onCreate did that but never released it, so a phone left face-up on a
    // table stayed lit until it went flat.
    //
    // Scoping it to the compass page is the compromise: the flag is held while
    // page 0 (or AR) is showing and dropped the moment the user swipes to the
    // level, leaves, or the composable goes away. The flag only applies while
    // this window is foreground anyway, so backgrounding was never the risk —
    // the risk was an abandoned foreground session, and that is exactly what
    // DisposableEffect cleans up.
    val context = LocalContext.current
    // settledPage, not currentPage: currentPage tracks the page nearest the snap
    // position DURING a drag, so dragging back and forth across the boundary
    // toggled the window flag on every frame of the gesture.
    val keepAwake = pagerState.settledPage == 0
    DisposableEffect(keepAwake) {
        val window = (context as? Activity)?.window
        if (keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── AR mode state — owned here so the pager can disable swipe during AR ──
    // null = auto (driven by the sensor), true = forced on, false = forced off.
    // rememberSaveable, not remember: the manifest declares no configChanges,
    // so a rotation recreates the Activity and a plain remember would silently
    // discard a deliberate choice — exit AR, rotate, and it snapped back on.
    var arModeManual by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // isUpright already carries the ViewModel's hysteresis (enter > 65°, exit
    // < 50°, 800 ms dwell) and is held false entirely when the user has turned
    // auto-switching off, so it needs no further debouncing here.
    val isUpright by viewModel.isUpright.collectAsStateWithLifecycle()
    val isArMode = arModeManual ?: isUpright

    // Re-arm auto-entry once the phone has been lowered, so that tapping
    // "exit AR" while still holding it up does not let auto-entry fire again on
    // the next frame and make the button read as broken.
    //
    // Only a *forced-off* override is cleared this way. Clearing a forced-on
    // one too meant that someone who opened AR deliberately while the phone was
    // flat got thrown out of it by any incidental false→true→false blip in
    // isUpright — a hand tremor was enough. A forced-on override is the user
    // overriding the posture, so posture must not revoke it.
    LaunchedEffect(isUpright) {
        if (!isUpright && arModeManual == false) arModeManual = null
    }

    LaunchedEffect(isArMode) {
        if (isArMode) viewModel.onArOpened(auto = arModeManual == null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDeep),
    ) {
        HorizontalPager(
            state             = pagerState,
            // Disable horizontal swipe while the AR camera overlay is active —
            // the camera view consumes the same gesture, causing accidental
            // page switches.
            userScrollEnabled = !isArMode,
            modifier          = Modifier
                .fillMaxSize()
                // The AR overlay is fullscreen by design and handles its own
                // insets; everything else must clear the status bar, camera
                // cutout and gesture area.
                .then(
                    if (isArMode) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                ),
        ) { page ->
            when (page) {
                0 -> CompassScreen(
                    viewModel      = viewModel,
                    isArMode       = isArMode,
                    onArModeChange = { arModeManual = it },
                )
                1 -> TiltScreen(viewModel = viewModel)
                else -> Unit
            }
        }

        AnimatedVisibility(
            visible = !isArMode,
            enter   = fadeIn(tween(durationMillis = 200)),
            exit    = fadeOut(tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 12.dp),
        ) {
            PagerDots(currentPage = pagerState.currentPage)
        }
    }
}

/**
 * Two 6 dp dots marking which page is showing.
 *
 * Purely decorative: it is not clickable and is cleared from the semantics tree
 * because TalkBack already announces the pager's page changes — a second,
 * wordless announcement of the same fact is noise.
 */
@Composable
private fun PagerDots(currentPage: Int, modifier: Modifier = Modifier) {
    val colors = appColors
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(Constants.Ui.PAGE_COUNT) { index ->
            val tint by animateColorAsState(
                targetValue = if (index == currentPage) colors.pagerDotActive
                              else colors.pagerDotIdle,
                animationSpec = tween(durationMillis = 250),
                label = "pagerDot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}
