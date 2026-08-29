package com.pubpeb.compass.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Boundary coverage for the two label functions — cheap, and off-by-one prone. */
class CardinalUtilTest {

    @Test
    fun `north spans the seam in both directions`() {
        assertEquals("N", bearingToCardinal(0f))
        assertEquals("N", bearingToCardinal(22.4f))
        assertEquals("N", bearingToCardinal(337.5f))
        assertEquals("N", bearingToCardinal(359.9f))
    }

    @Test
    fun `sector boundaries land on the upper sector`() {
        assertEquals("NE", bearingToCardinal(22.5f))
        assertEquals("E",  bearingToCardinal(67.5f))
        assertEquals("SE", bearingToCardinal(112.5f))
        assertEquals("S",  bearingToCardinal(157.5f))
        assertEquals("SW", bearingToCardinal(202.5f))
        assertEquals("W",  bearingToCardinal(247.5f))
        assertEquals("NW", bearingToCardinal(292.5f))
    }

    @Test
    fun `full labels spell out the four cardinals only`() {
        assertEquals("NORTH", bearingToFullLabel(0f))
        assertEquals("EAST",  bearingToFullLabel(90f))
        assertEquals("SOUTH", bearingToFullLabel(180f))
        assertEquals("WEST",  bearingToFullLabel(270f))
        assertEquals("NE",    bearingToFullLabel(45f))
        assertEquals("SW",    bearingToFullLabel(225f))
    }

    /** Callers normalise first, but 360 is the documented edge — pin it. */
    @Test
    fun `exactly 360 reads as north`() {
        assertEquals("N", bearingToCardinal(360f))
        assertEquals("NORTH", bearingToFullLabel(360f))
    }
}
