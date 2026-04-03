package com.pubpeb.mycompass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val BgDeep        = Color(0xFF0D0F14)
    val BgCard        = Color(0xFF161A22)
    val TextPrimary   = Color(0xFFCCD6F6)
    val TextSecondary = Color(0xFF8090B0)
    val AccentRed     = Color(0xFFE63946)
}

private val DarkColorScheme = darkColorScheme(
    primary         = AppColors.TextPrimary,
    onPrimary       = AppColors.BgDeep,
    primaryContainer = Color(0xFF1E2230),
    background      = AppColors.BgDeep,
    surface         = AppColors.BgCard,
    onSurface       = AppColors.TextPrimary,
    onBackground    = AppColors.TextPrimary,
    error           = AppColors.AccentRed,
)

@Composable
fun MyCompassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
