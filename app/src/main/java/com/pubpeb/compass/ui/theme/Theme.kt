package com.pubpeb.compass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which visual world the app is rendering in.
 *
 * [SYSTEM] follows the OS light/dark setting. [DAYLIGHT] is a deliberate
 * high-contrast outdoor mode: white ground, black type, decoration stripped —
 * for direct sunlight, where a dark UI on an OLED panel is the worst case.
 */
enum class ThemeMode { SYSTEM, DAYLIGHT }

/**
 * Every colour the app draws with, resolved for the active theme.
 *
 * UI files must read these through [LocalAppColors] (or the [appColors]
 * accessor) inside a composable — never as file-level `val`s, which would
 * freeze one theme's colours into the other theme's surfaces.
 */
@Immutable
data class AppPalette(
    // ── Surfaces & type ──────────────────────────────────────────────────
    val bgDeep: Color,
    val bgCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,

    // ── Compass rose ─────────────────────────────────────────────────────
    val roseBg: Color,
    val roseRing: Color,
    val tickMinor: Color,
    val tickMajor: Color,
    val tickCardinal: Color,
    val cardinalText: Color,
    /** Red reserved for north — and for nothing else, anywhere in the app. */
    val northLabel: Color,
    /** Fixed rim index ("lubber line") marking the direction the user faces. */
    val lubber: Color,

    // ── Level ────────────────────────────────────────────────────────────
    val levelOk: Color,
    val levelWarn: Color,
    val levelCritical: Color,
    val crosshair: Color,

    // ── Controls & chrome ────────────────────────────────────────────────
    val buttonBg: Color,
    val chipBg: Color,
    val pagerDotActive: Color,
    val pagerDotIdle: Color,

    /** True when this palette paints light-on-dark. Drives system bar icon colour. */
    val isDark: Boolean,
)

/** Default night palette — the app's original identity, unchanged in hue. */
val NightPalette = AppPalette(
    bgDeep        = Color(0xFF0D0F14),
    bgCard        = Color(0xFF161A22),
    textPrimary   = Color(0xFFCCD6F6),
    textSecondary = Color(0xFF8090B0),

    roseBg        = Color(0xFF111318),
    // Raised from #2A2E38 (1.37:1) to clear the 3:1 floor for graphical objects.
    roseRing      = Color(0xFF4A5464),
    tickMinor     = Color(0xFF5A6272),
    tickMajor     = Color(0xFF8090B0),
    tickCardinal  = Color(0xFFCCD6F6),
    cardinalText  = Color(0xFFFFFFFF),
    // Lightened from #E63946 (4.46:1 — failed AA) to ~7.4:1 on the rose ground.
    northLabel    = Color(0xFFF0616C),
    lubber        = Color(0xFFCCD6F6),

    levelOk       = Color(0xFF4CAF80),
    levelWarn     = Color(0xFFE6A817),
    levelCritical = Color(0xFFF0616C),
    crosshair     = Color(0xFF4A5464),

    buttonBg      = Color(0xFF1E2230),
    chipBg        = Color(0xEB0D0F14),
    pagerDotActive = Color(0xFFCCD6F6),
    pagerDotIdle   = Color(0xFF4A5464),

    isDark = true,
)

/** Light palette for daytime indoor use — follows the system setting. */
val DayPalette = AppPalette(
    bgDeep        = Color(0xFFF2F3F6),
    bgCard        = Color(0xFFFFFFFF),
    textPrimary   = Color(0xFF12161D),
    textSecondary = Color(0xFF515C6B),

    roseBg        = Color(0xFFFFFFFF),
    roseRing      = Color(0xFF9AA5B4),
    tickMinor     = Color(0xFF8B96A6),
    tickMajor     = Color(0xFF515C6B),
    tickCardinal  = Color(0xFF12161D),
    cardinalText  = Color(0xFF12161D),
    northLabel    = Color(0xFFC22A36),
    lubber        = Color(0xFF12161D),

    levelOk       = Color(0xFF1F7A54),
    levelWarn     = Color(0xFFB26206),
    levelCritical = Color(0xFFC22A36),
    crosshair     = Color(0xFF9AA5B4),

    buttonBg      = Color(0xFFE3E7ED),
    chipBg        = Color(0xEBFFFFFF),
    pagerDotActive = Color(0xFF12161D),
    pagerDotIdle   = Color(0xFFB6BEC9),

    isDark = false,
)

/**
 * Maximum-contrast outdoor palette. Pure white ground, near-black type, red
 * north — every decorative stroke pushed to a readable weight or dropped.
 * This is the mode for reading a bearing in direct sun.
 */
val DaylightPalette = AppPalette(
    bgDeep        = Color(0xFFFFFFFF),
    bgCard        = Color(0xFFFFFFFF),
    textPrimary   = Color(0xFF000000),
    textSecondary = Color(0xFF3A3A3A),

    roseBg        = Color(0xFFFFFFFF),
    roseRing      = Color(0xFF000000),
    tickMinor     = Color(0xFF6B6B6B),
    tickMajor     = Color(0xFF000000),
    tickCardinal  = Color(0xFF000000),
    cardinalText  = Color(0xFF000000),
    northLabel    = Color(0xFFB3121F),
    lubber        = Color(0xFF000000),

    levelOk       = Color(0xFF126B45),
    levelWarn     = Color(0xFF8A4A00),
    levelCritical = Color(0xFFB3121F),
    crosshair     = Color(0xFF000000),

    buttonBg      = Color(0xFFE0E0E0),
    chipBg        = Color(0xF5FFFFFF),
    pagerDotActive = Color(0xFF000000),
    pagerDotIdle   = Color(0xFF9E9E9E),

    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { NightPalette }

/** Shorthand for `LocalAppColors.current`. */
val appColors: AppPalette
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** Resolves the palette for [mode] against the current system dark setting. */
@Composable
fun resolvePalette(mode: ThemeMode): AppPalette = when (mode) {
    ThemeMode.DAYLIGHT -> DaylightPalette
    ThemeMode.SYSTEM   -> if (isSystemInDarkTheme()) NightPalette else DayPalette
}

@Composable
fun CompassTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val palette = resolvePalette(mode)

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary          = palette.textPrimary,
            onPrimary        = palette.bgDeep,
            primaryContainer = palette.buttonBg,
            background       = palette.bgDeep,
            surface          = palette.bgCard,
            onSurface        = palette.textPrimary,
            onBackground     = palette.textPrimary,
            error            = palette.northLabel,
        )
    } else {
        lightColorScheme(
            primary          = palette.textPrimary,
            onPrimary        = palette.bgDeep,
            primaryContainer = palette.buttonBg,
            background       = palette.bgDeep,
            surface          = palette.bgCard,
            onSurface        = palette.textPrimary,
            onBackground     = palette.textPrimary,
            error            = palette.northLabel,
        )
    }

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
