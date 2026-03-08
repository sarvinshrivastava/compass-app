package com.pubpeb.mycompass.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: CompassViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            NavigationBar(
                containerColor = BgCard,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = "Compass",
                        )
                    },
                    label = { Text("Compass") },
                    colors = navBarItemColors(),
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Level",
                        )
                    },
                    label = { Text("Level") },
                    colors = navBarItemColors(),
                )
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            when (page) {
                0 -> CompassScreen(viewModel = viewModel)
                1 -> TiltScreen(viewModel = viewModel)
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
