package com.pubpeb.compass.util

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-speed filter has one job: hold still under tremor, follow under
 * intent. Both halves are tested, plus the boundary between them.
 */
class SmoothingUtilTest {

    private val slow = 0.10f
    private val fast = 0.35f
    private val threshold = 2.5f

    private fun filter(current: Float, target: Float) =
        adaptiveLowPass(current, target, slow, fast, threshold)

    /** alpha at a given step size, per the documented quadratic ramp. */
    private fun expectedAlpha(step: Float): Float {
        val t = (abs(step) / threshold).coerceIn(0f, 1f)
        return slow + (fast - slow) * t * t
    }

    @Test
    fun `a step at or beyond the threshold uses the fast alpha`() {
        assertEquals(threshold * fast, filter(0f, threshold), 1e-5f)
        assertEquals(10f * fast, filter(0f, 10f), 1e-5f)
        assertEquals(90f * fast, filter(0f, 90f), 1e-4f)
    }

    @Test
    fun `a vanishing step approaches the slow alpha`() {
        // 0.05° step: t² = 0.0004, so alpha is slow to four decimal places.
        val step = 0.05f
        assertEquals(step * expectedAlpha(step), filter(0f, step), 1e-6f)
        assertEquals(slow, expectedAlpha(step), 1e-3f)
    }

    @Test
    fun `the ramp is continuous across the threshold`() {
        val below = filter(0f, threshold - 0.001f)
        val at = filter(0f, threshold)
        assertTrue(
            "ramp jumps at the threshold: $below then $at",
            abs(at - below) < 0.01f,
        )
    }

    @Test
    fun `negative steps are treated by magnitude`() {
        assertEquals(-10f * fast, filter(0f, -10f), 1e-5f)
        assertEquals(-1f * expectedAlpha(1f), filter(0f, -1f), 1e-5f)
    }

    /**
     * The behaviour the AR horizon actually needs: hand tremor around a fixed
     * elevation must not move the output much.
     */
    @Test
    fun `tremor around a fixed value stays near that value`() {
        var out = 30f
        val tremor = listOf(30.4f, 29.6f, 30.5f, 29.5f, 30.3f, 29.7f, 30.2f, 29.8f)
        repeat(8) { i -> out = filter(out, tremor[i % tremor.size]) }
        assertTrue(
            "tremor moved the output to $out, expected to stay near 30",
            abs(out - 30f) < 0.3f,
        )
    }

    /**
     * And the other half: a deliberate tilt must actually arrive, and quickly
     * enough that the labels do not visibly swim behind the camera image.
     *
     * SENSOR_DELAY_UI is roughly 60 ms, so ten samples is about 600 ms.
     */
    @Test
    fun `a deliberate move converges within a few samples`() {
        var out = 0f
        repeat(10) { out = filter(out, 30f) }
        assertTrue("reached only $out of 30 after 10 samples", out > 28.5f)

        repeat(15) { out = filter(out, 30f) }
        assertTrue("reached only $out of 30 after 25 samples", out > 29.5f)
    }

    /**
     * The regression this filter's shape exists for.
     *
     * A hard two-speed switch drops to the slow alpha the moment the residual
     * falls under the threshold, so the last couple of degrees close at
     * tremor-rejection speed — on the AR horizon, labels that arrive low after
     * a quick tilt and sag into place over the next second. The ramp must close
     * that final approach substantially faster than the slow alpha alone would.
     */
    @Test
    fun `the final approach does not collapse to the slow alpha`() {
        // Start just inside the threshold, which is exactly where a hard
        // switch would have handed over to slowAlpha.
        var ramped = 30f - threshold * 0.9f
        repeat(6) { ramped = filter(ramped, 30f) }

        var slowOnly = 30f - threshold * 0.9f
        repeat(6) { slowOnly += slow * (30f - slowOnly) }

        assertTrue(
            "ramped approach ($ramped) should beat a pure slow-alpha one ($slowOnly)",
            (30f - ramped) < (30f - slowOnly) * 0.75f,
        )
    }

    @Test
    fun `an unchanged target is a fixed point`() {
        assertEquals(12.5f, filter(12.5f, 12.5f), 1e-6f)
    }
}
