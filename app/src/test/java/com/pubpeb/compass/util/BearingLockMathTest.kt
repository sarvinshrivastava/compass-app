package com.pubpeb.compass.util

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bearing lock's readout is one subtraction, and it is the subtraction the
 * feature exists to save the user doing in their head. It has to be the
 * shortest-arc one: a lock at 002° while heading 358° is 4° to the right, not
 * 356° to the left.
 */
class BearingLockMathTest {

    /** Mirrors the readout in `HeadingReadout`. */
    private fun readout(locked: Float, heading: Float, onCourseDeg: Float = 3f): String {
        val delta = shortestArcDelta(locked, heading)
        val off = abs(delta).roundToInt()
        return when {
            abs(delta) <= onCourseDeg -> "ON COURSE"
            delta > 0 -> "$off° RIGHT"
            else -> "$off° LEFT"
        }
    }

    @Test
    fun `on the bearing reads on course`() {
        assertEquals("ON COURSE", readout(90f, 90f))
        assertEquals("ON COURSE", readout(90f, 92f))
        assertEquals("ON COURSE", readout(90f, 88f))
    }

    @Test
    fun `turning right closes a clockwise gap`() {
        assertEquals("20° RIGHT", readout(110f, 90f))
    }

    @Test
    fun `turning left closes an anticlockwise gap`() {
        assertEquals("20° LEFT", readout(70f, 90f))
    }

    /** The seam. A naive subtraction reports 356° the wrong way here. */
    @Test
    fun `crossing north takes the short way`() {
        assertEquals("4° RIGHT", readout(2f, 358f))
        assertEquals("4° LEFT", readout(358f, 2f))
    }

    @Test
    fun `the opposite bearing is reported as a half turn`() {
        // shortestArcDelta's range is [-180, +180), so an exact reversal lands
        // on -180 and reads LEFT. Either hand is equally correct at 180°; what
        // matters is that it does not read 0 or crash.
        assertEquals("180° LEFT", readout(270f, 90f))
    }

    @Test
    fun `the on-course band is symmetric`() {
        assertEquals("4° RIGHT", readout(94f, 90f))
        assertEquals("4° LEFT", readout(86f, 90f))
    }
}
