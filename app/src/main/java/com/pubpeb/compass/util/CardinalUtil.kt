package com.pubpeb.compass.util

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
 *
 * @param azimuth 0–360°, pre-normalized.
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

/**
 * Returns the spoken cardinal name for the given azimuth.
 *
 * Separate from [bearingToFullLabel] because that one is tuned for the screen,
 * where "NE" is instantly legible and "NORTH-EAST" is three times as wide. A
 * screen reader handed "NE" says the two letters, and "SW" comes out as
 * "southwest" only by luck of the pronunciation dictionary. Spell it.
 */
fun bearingToSpokenLabel(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "north"
    azimuth < 67.5f  -> "north-east"
    azimuth < 112.5f -> "east"
    azimuth < 157.5f -> "south-east"
    azimuth < 202.5f -> "south"
    azimuth < 247.5f -> "south-west"
    azimuth < 292.5f -> "west"
    else             -> "north-west"
}
