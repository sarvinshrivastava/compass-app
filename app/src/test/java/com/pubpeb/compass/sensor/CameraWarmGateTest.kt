package com.pubpeb.compass.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that stops a hovering hand from strobing the camera and the
 * platform's privacy indicator.
 */
class CameraWarmGateTest {

    private var now = 0L
    private fun gate() = CameraWarmGate(nowMs = { now })

    private fun CameraWarmGate.hold(requested: Boolean, forMs: Long, stepMs: Long = 60L): Boolean {
        var elapsed = 0L
        var out = update(requested)
        while (elapsed < forMs) {
            now += stepMs
            elapsed += stepMs
            out = update(requested)
        }
        return out
    }

    @Test
    fun `starts cold`() {
        assertFalse(gate().isWarm)
    }

    @Test
    fun `a brief request never opens the camera`() {
        val g = gate()
        assertFalse(g.hold(requested = true, forMs = 120L))
    }

    @Test
    fun `a sustained request opens it`() {
        assertTrue(gate().hold(requested = true, forMs = 240L))
    }

    @Test
    fun `release is slower than acquire, so a dip does not tear it down`() {
        val g = gate()
        g.hold(requested = true, forMs = 240L)
        assertTrue("a 300ms dip must not close a warm camera", g.hold(false, 300L))
        assertTrue(g.isWarm)
    }

    @Test
    fun `a sustained drop does close it`() {
        val g = gate()
        g.hold(requested = true, forMs = 240L)
        assertFalse(g.hold(requested = false, forMs = 500L))
    }

    /**
     * The regression this gate exists for: `isArming` flips on single samples
     * either side of the entry threshold. Wired raw to a camera bind, this
     * sequence opens and closes the hardware repeatedly.
     */
    @Test
    fun `oscillating at the threshold never opens the camera`() {
        val g = gate()
        repeat(20) {
            now += 60L
            assertFalse("opened on a hover", g.update(true))
            now += 60L
            assertFalse("opened on a hover", g.update(false))
        }
        assertFalse(g.isWarm)
    }

    @Test
    fun `reset forces it cold`() {
        val g = gate()
        g.hold(requested = true, forMs = 240L)
        assertTrue(g.isWarm)
        g.reset()
        assertFalse(g.isWarm)
    }
}
