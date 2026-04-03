package com.pubpeb.mycompass.util

/**
 * Returns the short cardinal abbreviation for the given azimuth (0–360°).
 * Used by the AR overlay HUD and widget.
 */
fun bearingToCardinal(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "N"
    azimuth < 67.5f  -> "NE"
    azimuth < 112.5f -> "E"
    azimuth < 157.5f -> "SE"
    azimuth < 202.5f -> "S"
    azimuth < 247.5f -> "SW"
    azimuth < 292.5f -> "W"
    else             -> "NW"
}

/**
 * Returns the full cardinal name for N/E/S/W, abbreviation for inter-cardinals.
 * Used by the compass screen heading display.
 */
fun bearingToFullLabel(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "NORTH"
    azimuth < 67.5f  -> "NE"
    azimuth < 112.5f -> "EAST"
    azimuth < 157.5f -> "SE"
    azimuth < 202.5f -> "SOUTH"
    azimuth < 247.5f -> "SW"
    azimuth < 292.5f -> "WEST"
    else             -> "NW"
}
