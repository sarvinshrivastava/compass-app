package com.pubpeb.compass.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AR gate is the behaviour that was specified by hand — enter above 65°,
 * exit below 50°, after an 800 ms dwell — so it gets real coverage rather than
 * trust. [ArTriggerGate] takes an injectable clock precisely so these run as
 * plain JVM tests with no Android framework and no Robolectric.
 */
class ArTriggerGateTest {

    private var now = 0L
    private fun gate() = ArTriggerGate(nowMs = { now })

    /** Advances the fake clock and feeds the same tilt in, as a real stream would. */
    private fun ArTriggerGate.hold(tiltDeg: Float, forMs: Long, stepMs: Long = 100L): Boolean {
        var elapsed = 0L
        var state = update(tiltDeg)
        while (elapsed < forMs) {
            now += stepMs
            elapsed += stepMs
            state = update(tiltDeg)
        }
        return state
    }

    @Test
    fun `stays closed below the entry threshold no matter how long`() {
        val g = gate()
        assertFalse(g.hold(tiltDeg = 40f, forMs = 5_000L))
        assertFalse(g.isUpright)
    }

    @Test
    fun `does not engage before the dwell has elapsed`() {
        val g = gate()
        assertFalse(g.hold(tiltDeg = 70f, forMs = 500L))
    }

    @Test
    fun `engages once the dwell has elapsed above the entry threshold`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = 70f, forMs = 900L))
    }

    @Test
    fun `stays engaged inside the hysteresis band`() {
        val g = gate()
        g.hold(tiltDeg = 70f, forMs = 900L)
        // 55° sits between exit (50) and enter (65) — the band exists so a hand
        // drifting here holds steady instead of flickering.
        assertTrue(g.update(55f))
        assertTrue(g.isUpright)
    }

    @Test
    fun `disengages immediately below the exit threshold, with no dwell`() {
        val g = gate()
        g.hold(tiltDeg = 70f, forMs = 900L)
        assertFalse(g.update(45f))
    }

    /**
     * The regression test for the original bug. The old implementation was a
     * bare 45° threshold, so a phone held at a normal reading angle toggled the
     * whole screen into a camera view and back on sensor noise. Nothing in this
     * sequence may ever engage the gate.
     */
    @Test
    fun `oscillating around the old 45 degree threshold never engages`() {
        val g = gate()
        repeat(20) {
            listOf(40f, 44f, 46f, 50f, 44f, 41f).forEach { tilt ->
                now += 100L
                assertFalse("engaged at $tilt°", g.update(tilt))
            }
        }
        assertFalse(g.isUpright)
    }

    @Test
    fun `exactly the entry threshold does not engage`() {
        val g = gate()
        // The guard is `tiltDeg <= enterDeg`, so 65.0 must not enter however long it is held.
        assertFalse(g.hold(tiltDeg = 65f, forMs = 5_000L))
    }

    @Test
    fun `exactly the exit threshold stays engaged`() {
        val g = gate()
        g.hold(tiltDeg = 70f, forMs = 900L)
        // The guard is `tiltDeg < exitDeg`, so 50.0 is still engaged.
        assertTrue(g.update(50f))
    }

    @Test
    fun `a dip below entry restarts the dwell`() {
        val g = gate()
        g.update(70f)
        now += 700L          // nearly there
        g.update(70f)
        g.update(40f)        // dip resets
        now += 700L
        assertFalse(g.update(70f))   // only 0 ms into the new dwell
    }

    @Test
    fun `reset closes the gate`() {
        val g = gate()
        g.hold(tiltDeg = 70f, forMs = 900L)
        assertTrue(g.isUpright)
        g.reset()
        assertFalse(g.isUpright)
    }
}
