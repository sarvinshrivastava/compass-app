package com.pubpeb.compass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import android.content.pm.ActivityInfo
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
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { Page.COUNT })

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
        // Exhaustive over Page: a new entry fails to compile until someone
        // decides whether it reports an open. That is the point — this used to
        // be `currentPage == 1`, so inserting a page ahead of the level would
        // have silently re-pointed the counter at the new screen.
        when (Page.at(pagerState.currentPage)) {
            Page.LEVEL -> viewModel.onLevelOpened()
            Page.COMPASS -> Unit
        }
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
    val settledPage = Page.at(pagerState.settledPage)
    val keepAwake = settledPage.keepScreenOn
    // ── Orientation is a property of the page ────────────────────────────────
    // The compass pins portrait; the level rotates freely because rotating the
    // device IS the interaction there. `configChanges` in the manifest absorbs
    // the switch, so this costs no Activity recreation.
    //
    // settledPage, not currentPage: mid-drag the pager reports whichever page
    // is nearest, so keying on it would fire setRequestedOrientation on every
    // frame of a swipe.
    LaunchedEffect(settledPage) {
        (context as? Activity)?.requestedOrientation = if (settledPage.lockPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(keepAwake) {
        val window = (context as? Activity)?.window
        if (keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── AR mode state — owned here so the pager can disable swipe during AR ──
    // null = auto (driven by the sensor), true = forced on, false = forced off.
    // rememberSaveable, not remember: the manifest now absorbs orientation via
    // configChanges, but it does NOT absorb uiMode, fontScale, locale or
    // density, and process death is always possible — any of those would
    // silently discard a deliberate choice.
    var arModeManual by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // isUpright already carries the ViewModel's hysteresis on tilt and on roll
    // (see Constants.Ar) and is held false entirely when the user has turned
    // auto-switching off, so it needs no further debouncing here. Deliberately
    // does not restate the numbers — they have been retuned once already and a
    // comment is the last place that gets updated.
    val isUpright by viewModel.isUpright.collectAsStateWithLifecycle()
    val sensorReady by viewModel.sensorReady.collectAsStateWithLifecycle()

    // ── AR belongs to the compass page, and only to it ───────────────────────
    // isUpright is derived from tilt alone and knows nothing about paging, so
    // without this it went true on the LEVEL page too — where AR never renders,
    // because ArOverlayScreen lives inside CompassScreen. The visible effect
    // was worse than a stray camera: `userScrollEnabled = !isArMode` disabled
    // the pager and the dots faded out, so raising the phone while on the level
    // silently trapped the user there with no affordance left to get back.
    val onCompassPage = settledPage == Page.COMPASS

    // ── Auto-entry needs a FRESH lift, not an inherited posture ──────────────
    // Reading the level upright already holds the phone past the AR threshold,
    // so swiping from there to the compass used to drop the user straight into
    // the camera the instant the page settled. The gate was true, but it was
    // true *about the level* — stale intent, carried across a page change.
    //
    // Arriving already upright therefore disarms auto-entry until the phone
    // comes back down. Lowering it re-arms, so the next deliberate raise works
    // exactly as before, and the camera button is unaffected either way.
    // The ARRIVAL half is resolved during composition, not in an effect. A
    // LaunchedEffect runs after the frame it was composed in, which left one
    // frame where the page had already changed but the arming decision had
    // not — isArMode went true, the pager dropped its safeDrawing padding and
    // the dots faded, and the whole screen reflowed and snapped back. The
    // camera never opened; the layout blinked anyway.
    //
    // remember(settledPage) re-evaluates the instant the page settles, in the
    // same composition, so the decision is never one frame late.
    // Keyed on sensorReady as well as the page, so the snapshot is taken from a
    // real reading rather than from isUpright's placeholder false. On a cold
    // start the hardware only begins after the first composition, so without
    // this the arrival always looked flat — and launching the app while already
    // holding the phone up went straight into the camera, which is precisely
    // what this rule exists to stop on the other path in.
    val uprightOnArrival = remember(settledPage, sensorReady) { sensorReady && isUpright }
    var loweredSinceArrival by remember(settledPage, sensorReady) { mutableStateOf(false) }
    LaunchedEffect(settledPage, isUpright, sensorReady) {
        if (sensorReady && !isUpright) loweredSinceArrival = true
    }

    // Derived, never written during composition: arriving flat arms immediately,
    // arriving already-upright waits for the phone to come down once. The
    // re-arming half below IS an effect, but a late re-arm only ever delays
    // granting AR by a frame — it cannot cause the reflow the arrival half had
    // to avoid.
    val autoEntryArmed = !uprightOnArrival || loweredSinceArrival

    val isArMode = onCompassPage && (arModeManual ?: (isUpright && autoEntryArmed))

    // Re-arm auto-entry once the phone has been lowered, so that tapping
    // "exit AR" while still holding it up does not let auto-entry fire again on
    // the next frame and make the button read as broken.
    //
    // Only a *forced-off* override is cleared this way. Clearing a forced-on
    // one too meant that someone who opened AR deliberately while the phone was
    // flat got thrown out of it by any incidental false→true→false blip in
    // isUpright — a hand tremor was enough. A forced-on override is the user
    // overriding the posture, so posture must not revoke it.
    //
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
            when (Page.at(page)) {
                Page.COMPASS -> CompassScreen(
                    viewModel      = viewModel,
                    isArMode       = isArMode,
                    // Pre-opening the camera is only ever appropriate once the
                    // compass page is settled AND auto-entry is actually armed.
                    arWarmAllowed  = onCompassPage && autoEntryArmed,
                    onArModeChange = { arModeManual = it },
                )
                Page.LEVEL -> TiltScreen(viewModel = viewModel)
            }
        }

        AnimatedVisibility(
            visible = !isArMode,
            enter   = fadeIn(chromeSpec(Constants.Ui.CHROME_FADE_MS)),
            exit    = fadeOut(chromeSpec(Constants.Ui.CHROME_FADE_MS)),
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
        repeat(Page.COUNT) { index ->
            val tint by animateColorAsState(
                targetValue = if (index == currentPage) colors.pagerDotActive
                              else colors.pagerDotIdle,
                animationSpec = chromeSpec(Constants.Ui.PAGER_DOT_MS),
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
