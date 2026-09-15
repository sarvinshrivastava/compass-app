package com.pubpeb.compass.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Holding the phone sideways or upside down against a door frame asks the same
 * question about the same edge, so the plumb level folds its reading onto the
 * nearest quarter turn. Reporting "170° off plumb" would be arithmetically
 * true and useless.
 */
class FoldToQuarterTurnTest {

    private val tolerance = 1e-4f

    @Test
    fun `exact quarter turns fold to zero`() {
        assertEquals(0f, foldToQuarterTurn(0f), tolerance)
        assertEquals(0f, foldToQuarterTurn(90f), tolerance)
        assertEquals(0f, foldToQuarterTurn(180f), tolerance)
        assertEquals(0f, foldToQuarterTurn(-90f), tolerance)
        assertEquals(0f, foldToQuarterTurn(-180f), tolerance)
    }

    @Test
    fun `small offsets keep their sign near zero`() {
        assertEquals(10f, foldToQuarterTurn(10f), tolerance)
        assertEquals(-10f, foldToQuarterTurn(-10f), tolerance)
    }

    @Test
    fun `offsets from other quarter turns fold the same way`() {
        assertEquals(10f, foldToQuarterTurn(100f), tolerance)
        assertEquals(10f, foldToQuarterTurn(-80f), tolerance)
        assertEquals(-10f, foldToQuarterTurn(170f), tolerance)
        assertEquals(-10f, foldToQuarterTurn(80f), tolerance)
    }

    @Test
    fun `the midpoint tips to the negative side`() {
        assertEquals(44f, foldToQuarterTurn(44f), tolerance)
        assertEquals(45f, foldToQuarterTurn(45f), tolerance)
        assertEquals(-44f, foldToQuarterTurn(46f), tolerance)
    }

    @Test
    fun `the result is always within a half quarter turn`() {
        var deg = -180f
        while (deg <= 180f) {
            val folded = foldToQuarterTurn(deg)
            assert(folded >= -45f && folded <= 45f) {
                "$deg folded to $folded, outside [-45, 45]"
            }
            deg += 0.5f
        }
    }
}
