package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AR gate's behaviour was specified by hand, so it gets real coverage
 * rather than trust. [ArTriggerGate] takes an injectable clock precisely so
 * these run as plain JVM tests with no Android framework and no Robolectric.
 *
 * ### Two kinds of assertion here, deliberately
 * Most tests read their expectations from `Constants.Ar`, because they pin the
 * gate's *shape* — full dwell at the entry angle, minimum at the decisive one,
 * a hysteresis band between entry and exit — and hard-coding those made every
 * retune look like a regression.
 *
 * But a suite made only of those cannot fail. Deriving every expectation from
 * the same constants it is meant to guard means asserting they equal
 * themselves: with `EXIT_DEG = 5f` and `DWELL_MS = 5000L` — AR holding the
 * camera open until the phone is nearly flat, behind a five-second dwell —
 * every shape test still passes.
 *
 * So the safety floors are pinned with **literal angles** below. Those must not
 * be converted to read from `Constants`; that is the whole point of them.
 */
class ArTriggerGateTest {

    // Read from the constants on purpose. These tests pin the gate's *shape* —
    // full dwell at the entry angle, minimum dwell at the decisive angle, a
    // hysteresis band between entry and exit. Hard-coding the numbers made
    // every tuning change look like a regression.
    private val enter = Constants.Ar.ENTER_DEG
    private val exit = Constants.Ar.EXIT_DEG
    private val decisive = Constants.Ar.DECISIVE_DEG
    private val fullDwell = Constants.Ar.DWELL_MS
    private val minDwell = Constants.Ar.DWELL_MIN_MS

    private var now = 0L
    private fun gate() = ArTriggerGate(nowMs = { now })

    /** Advances the fake clock and feeds the same tilt in, as a real stream would. */
    // roll defaults to 0 here only — the PRODUCTION signature has no default,
    // so a caller cannot silently bypass the portrait guard.
    private fun ArTriggerGate.hold(
        tiltDeg: Float,
        forMs: Long,
        stepMs: Long = 100L,
        roll: Float = 0f,
    ): Boolean {
        var elapsed = 0L
        var state = update(tiltDeg, roll)
        while (elapsed < forMs) {
            now += stepMs
            elapsed += stepMs
            state = update(tiltDeg, roll)
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
        assertFalse(g.hold(tiltDeg = enter + 1f, forMs = fullDwell / 2))
    }

    @Test
    fun `engages once the dwell has elapsed above the entry threshold`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = enter + 1f, forMs = fullDwell + 200L))
    }

    @Test
    fun `stays engaged inside the hysteresis band`() {
        val g = gate()
        g.hold(tiltDeg = enter + 5f, forMs = fullDwell + 200L)
        // Midway between exit and enter — the band exists so a hand drifting
        // here holds steady instead of flickering.
        assertTrue(g.update((enter + exit) / 2f, 0f))
        assertTrue(g.isUpright)
    }

    @Test
    fun `disengages immediately below the exit threshold, with no dwell`() {
        val g = gate()
        g.hold(tiltDeg = enter + 5f, forMs = fullDwell + 200L)
        assertFalse(g.update(exit - 1f, 0f))
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
                assertFalse("engaged at $tilt°", g.update(tilt, 0f))
            }
        }
        assertFalse(g.isUpright)
    }

    @Test
    fun `exactly the entry threshold does not engage`() {
        val g = gate()
        // The guard is `tiltDeg <= enterDeg`, so the threshold itself must not
        // enter however long it is held.
        assertFalse(g.hold(tiltDeg = enter, forMs = 5_000L))
    }

    @Test
    fun `exactly the exit threshold stays engaged`() {
        val g = gate()
        g.hold(tiltDeg = enter + 5f, forMs = fullDwell + 200L)
        // The guard is `tiltDeg < exitDeg`, so the threshold itself holds.
        assertTrue(g.update(exit, 0f))
    }

    @Test
    fun `a dip below entry restarts the dwell`() {
        val g = gate()
        g.update(enter + 1f, 0f)
        now += fullDwell - 50L       // nearly there
        g.update(enter + 1f, 0f)
        g.update(exit - 10f, 0f)         // dip resets
        now += fullDwell
        assertFalse(g.update(enter + 1f, 0f))   // only 0 ms into the new dwell
    }

    @Test
    fun `reset closes the gate`() {
        val g = gate()
        g.hold(tiltDeg = enter + 5f, forMs = fullDwell + 200L)
        assertTrue(g.isUpright)
        g.reset()
        assertFalse(g.isUpright)
    }

    // ── Adaptive dwell ───────────────────────────────────────────────────────
    //
    // The dwell exists to reject a hand hovering near the entry threshold, not
    // to tax everybody. It now scales with how far past the threshold the lift
    // went, so a deliberate raise to eye level engages roughly three times
    // faster while the anti-flicker guarantee is untouched where it matters.

    @Test
    fun `dwell is the full duration at the entry threshold`() {
        assertEquals(fullDwell, gate().dwellFor(enter))
    }

    @Test
    fun `dwell is the minimum at and beyond the decisive angle`() {
        assertEquals(minDwell, gate().dwellFor(decisive))
        assertEquals(minDwell, gate().dwellFor(90f))
    }

    @Test
    fun `dwell scales linearly between the two`() {
        val midAngle = (enter + decisive) / 2f
        val midDwell = (fullDwell + minDwell) / 2
        assertEquals(midDwell, gate().dwellFor(midAngle))
    }

    @Test
    fun `a decisive lift engages well before the full dwell`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = decisive + 5f, forMs = minDwell + 50L))
        // ...and that is strictly quicker than a marginal lift would be.
        assert(minDwell + 50L < fullDwell)
    }

    @Test
    fun `a marginal lift still pays close to the full dwell`() {
        val g = gate()
        val marginal = enter + 1f
        assertFalse(g.hold(tiltDeg = marginal, forMs = minDwell + 50L))
        assertTrue(g.hold(tiltDeg = marginal, forMs = fullDwell))
    }

    /**
     * Peak, not current: a fast raise that settles back is still a fast raise,
     * so the dwell it already earned must not be revoked when the hand relaxes.
     */
    @Test
    fun `the dwell is charged against the peak tilt, not the latest`() {
        val g = gate()
        g.update(decisive + 5f, 0f)          // decisive peak -> minimum dwell
        now += minDwell + 50L
        assertTrue(g.update(enter + 5f, 0f))
    }

    /**
     * The anti-flicker guarantee, restated against the new code path: dipping
     * below entry must clear the peak as well as the timer, or a single spike
     * past 82 would leave every later marginal hover on a 250 ms fuse.
     */
    @Test
    fun `a dip below entry clears the earned peak`() {
        val g = gate()
        g.update(decisive + 5f, 0f)     // spike
        g.update(exit - 10f, 0f)        // dropped back, resets
        now += minDwell + 50L
        assertFalse(g.update(enter + 1f, 0f))   // marginal lift pays the long dwell again
    }

    // ── Absolute safety floors — literal values on purpose ───────────────────
    //
    // These are the assertions a bad retune has to fail. Do not rewrite them in
    // terms of Constants.Ar.

    @Test
    fun `a phone held at the walking-glance angle never engages`() {
        // 50° is within the range someone holds a phone at while walking and
        // glancing at it. Entry firing here is the original bug.
        assertFalse(gate().hold(tiltDeg = 50f, forMs = 5_000L))
    }

    @Test
    fun `AR lets go by the time the phone is back to 44 degrees`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = 80f, forMs = 1_000L))
        assertFalse("still engaged at 44° — exit is at or above the floor", g.update(44f, 0f))
    }

    @Test
    fun `entry dwell stays within a usable range`() {
        // Long enough to reject a wobble, short enough not to feel broken.
        val atEntry = gate().dwellFor(Constants.Ar.ENTER_DEG)
        assertTrue("entry dwell $atEntry ms is too short to reject a wobble", atEntry >= 250L)
        assertTrue("entry dwell $atEntry ms would feel broken", atEntry <= 1_000L)
    }

    @Test
    fun `the entry angle stays clear of the walking-glance band`() {
        assertTrue(
            "ENTER_DEG ${Constants.Ar.ENTER_DEG} is too close to the 45° floor",
            Constants.Ar.ENTER_DEG >= 52f,
        )
    }

    // ── Portrait requirement ─────────────────────────────────────────────────
    //
    // Tilt alone is not intent. Lying on your side holding the phone rolled
    // toward landscape produces the same tilt-from-flat as raising it to sight
    // a horizon, and opening the camera there is startling and useless.

    @Test
    fun `a decisive lift held in landscape never engages`() {
        val g = gate()
        var state = g.update(decisive + 5f, screenRollDeg = 90f)
        repeat(30) {
            now += 100L
            state = g.update(decisive + 5f, screenRollDeg = 90f)
        }
        assertFalse("engaged while rolled to landscape", state)
    }

    @Test
    fun `upside-down portrait still counts as portrait`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = decisive + 5f, forMs = fullDwell, roll = 180f))
    }

    @Test
    fun `just inside the portrait band engages`() {
        assertTrue(gate().hold(tiltDeg = decisive + 5f, forMs = fullDwell, roll = 44f))
    }

    @Test
    fun `just outside the portrait band does not`() {
        assertFalse(gate().hold(tiltDeg = decisive + 5f, forMs = fullDwell, roll = 46f))
    }

    /**
     * Rolling to landscape while AR is already open must drop out immediately.
     * Holding the camera open because the gate only checks roll on entry is the
     * same class of bug as never checking it at all.
     */
    @Test
    fun `rolling to landscape while engaged disengages at once`() {
        val g = gate()
        assertTrue(g.hold(tiltDeg = decisive + 5f, forMs = fullDwell))
        assertFalse(g.update(decisive + 5f, screenRollDeg = 90f))
    }
}
