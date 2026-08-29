package com.pubpeb.compass.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SensorRepository.tiltFromFlat] is the AR gate's only input, so a wrong axis
 * or a lost sign breaks auto-entry silently — no crash, just a feature that
 * never fires. It is `internal` and pure `kotlin.math`, so it tests directly.
 *
 * Android's accelerometer reports the reaction force, so at rest the axis
 * pointing *up* reads +9.81.
 */
class TiltFromFlatTest {

    private val g = 9.81f

    @Test
    fun `flat face up is zero`() {
        assertEquals(0f, SensorRepository.tiltFromFlat(floatArrayOf(0f, 0f, g)), 0.01f)
    }

    /** |g.z| folds face-down onto face-up: both are "lying flat". */
    @Test
    fun `flat face down is also zero`() {
        assertEquals(0f, SensorRepository.tiltFromFlat(floatArrayOf(0f, 0f, -g)), 0.01f)
    }

    @Test
    fun `upright portrait is ninety`() {
        assertEquals(90f, SensorRepository.tiltFromFlat(floatArrayOf(0f, g, 0f)), 0.01f)
    }

    /** Rolled onto its side is just as upright as stood on end. */
    @Test
    fun `landscape roll is ninety`() {
        assertEquals(90f, SensorRepository.tiltFromFlat(floatArrayOf(g, 0f, 0f)), 0.01f)
    }

    @Test
    fun `forty five degrees reads forty five`() {
        val c = g * 0.70710677f   // g / sqrt(2) split across two axes
        assertEquals(45f, SensorRepository.tiltFromFlat(floatArrayOf(0f, c, c)), 0.01f)
    }

    /** Freefall, or a lying sensor: the angle is undefined, not a divide by zero. */
    @Test
    fun `near zero gravity returns zero rather than diverging`() {
        val tiny = 1e-5f
        assertEquals(0f, SensorRepository.tiltFromFlat(floatArrayOf(tiny, tiny, tiny)), 0.01f)
    }

    @Test
    fun `result never leaves the zero to ninety range`() {
        var deg = 0
        while (deg < 360) {
            val r = Math.toRadians(deg.toDouble())
            val v = floatArrayOf(0f, (g * Math.sin(r)).toFloat(), (g * Math.cos(r)).toFloat())
            val tilt = SensorRepository.tiltFromFlat(v)
            assertTrue("$deg° gave $tilt", tilt in 0f..90.001f)
            deg += 5
        }
    }
}
