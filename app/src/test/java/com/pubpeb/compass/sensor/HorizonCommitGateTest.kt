package com.pubpeb.compass.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This gate exists because the same logic, written as a `LaunchedEffect` keyed
 * on the live elevation, shipped frozen — and nothing caught it. These tests
 * are what catching it looks like.
 */
class HorizonCommitGateTest {

    private var now = 0L
    private fun gate() = HorizonCommitGate(nowMs = { now })
    private val tol = 1e-4f

    /** Feeds a steady reading for a span, as a real stream would. */
    private fun HorizonCommitGate.hold(deg: Float, forMs: Long, stepMs: Long = 60L): Float {
        var elapsed = 0L
        var out = update(deg)
        while (elapsed < forMs) {
            now += stepMs
            elapsed += stepMs
            out = update(deg)
        }
        return out
    }

    @Test
    fun `first reading seeds the resting position`() {
        assertEquals(30f, gate().update(30f), tol)
    }

    @Test
    fun `tremor inside the dead zone never moves the horizon`() {
        val g = gate()
        g.update(30f)
        // Real hand tremor: sub-degree, alternating, for two full seconds.
        listOf(30.4f, 29.6f, 30.5f, 29.5f, 30.3f, 29.7f, 30.2f, 29.8f).let { tremor ->
            repeat(32) { i ->
                now += 60L
                assertEquals(30f, g.update(tremor[i % tremor.size]), tol)
            }
        }
    }

    @Test
    fun `a sustained re-aim commits after the window`() {
        val g = gate()
        g.update(30f)
        assertEquals("should not move before the window", 30f, g.hold(50f, 200L), tol)
        assertEquals("should have committed by now", 50f, g.hold(50f, 200L), tol)
    }

    @Test
    fun `a flick past the dead zone that returns never commits`() {
        val g = gate()
        g.update(30f)
        now += 60L; g.update(50f)      // out
        now += 60L; g.update(30f)      // straight back in — window abandoned
        now += 600L
        assertEquals(30f, g.update(30f), tol)
    }

    @Test
    fun `it commits the newest reading, not the one that tripped the threshold`() {
        val g = gate()
        g.update(0f)
        now += 60L; g.update(20f)      // trips
        now += 60L; g.update(35f)      // still moving
        now += 300L
        assertEquals(45f, g.update(45f), tol)
    }

    @Test
    fun `exactly the dead zone edge does not trip it`() {
        val g = gate()
        g.update(0f)
        // Guard is `<= deadZone`, so 9.0 with a 9 deg zone stays put.
        assertEquals(0f, g.hold(9f, 2_000L), tol)
    }

    /**
     * The regression. The broken version restarted its timer on every emission,
     * so a value that kept changing slightly — which is every real sensor
     * stream — could never accumulate a full commit window. It froze forever.
     */
    @Test
    fun `a re-aim still commits while the reading keeps jittering`() {
        val g = gate()
        g.update(0f)
        // Held around 40 deg, but never the same float twice, for a second.
        var out = 0f
        repeat(16) { i ->
            now += 60L
            out = g.update(40f + (i % 5) * 0.03f)
        }
        assert(out > 35f) { "horizon never committed; it read $out — this is the frozen-horizon bug" }
    }
}
