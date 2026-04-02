package com.pubpeb.mycompass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pubpeb.mycompass.viewmodel.CompassViewModel
import kotlinx.coroutines.launch

// ── Colours (shared with the rest of the dark palette) ──────────────────────
private val BgDeep     = Color(0xFF0D0F14)
private val BgCard     = Color(0xFF161A22)
private val TextPrimary   = Color(0xFFCCD6F6)
private val TextSecondary = Color(0xFF8090B0)
private val IndicatorBg   = Color(0xFF2A3550)

/**
 * Root composable called from [com.pubpeb.mycompass.MainActivity].
 *
 * Hosts two pages in a [HorizontalPager]:
 *  - Page 0: [CompassScreen] (compass rose + degree readout + settings)
 *  - Page 1: [TiltScreen]    (2-D bubble level indicator)
 *
 * A [NavigationBar] at the bottom lets the user jump between pages with a tap,
 * and stays in sync with swipe gestures via [pagerState.currentPage].
 * The navigation bar slides down out of frame when AR mode is active,
 * giving a full-screen immersive experience.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: CompassViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope      = rememberCoroutineScope()

    // ── AR mode state — owned here so the pager can disable swipe during AR ──
    // null = auto (driven by sensor), true = forced on, false = forced off.
    var arModeManual by remember { mutableStateOf<Boolean?>(null) }
    // isUpright is derived from raw gravity (|gravity.y| > |gravity.z|) in SensorRepository.
    // This is more reliable than a pitch threshold after remapCoordinateSystem(AXIS_X, AXIS_Z)
    // changes what orientation[1] reports when the phone is upright.
    val isUpright by viewModel.isUpright.collectAsStateWithLifecycle()
    val isArMode = arModeManual ?: isUpright

    // Bottom padding for pager content when nav bar is visible.
    // 80.dp matches the NavigationBar default height.
    val bottomPadding by animateDpAsState(
        targetValue   = if (isArMode) 0.dp else 80.dp,
        animationSpec = tween(durationMillis = 300, easing = EaseInOut),
        label         = "navPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        // Pager — always fills full screen
        HorizontalPager(
            state             = pagerState,
            // Disable horizontal swipe while AR camera overlay is active — the
            // camera view consumes the same gesture, causing accidental page switches.
            userScrollEnabled = !isArMode,
            modifier          = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
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

        // Navigation bar — slides in/out
        AnimatedVisibility(
            visible = !isArMode,
            enter   = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = tween(durationMillis = 300, easing = EaseInOut),
            ),
            exit    = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = EaseInOut),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NavigationBar(
                containerColor = BgCard,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon  = { Icon(Icons.Filled.Explore, contentDescription = "Compass") },
                    label  = { Text("Compass") },
                    colors = navBarItemColors(),
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon  = { Icon(Icons.Filled.Tune, contentDescription = "Level") },
                    label  = { Text("Level") },
                    colors = navBarItemColors(),
                )
            }
        }
    }
}

@Composable
private fun navBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = TextPrimary,
    selectedTextColor   = TextPrimary,
    indicatorColor      = IndicatorBg,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
)
