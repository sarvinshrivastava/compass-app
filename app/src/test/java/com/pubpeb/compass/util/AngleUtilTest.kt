package com.pubpeb.compass.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleUtilTest {

    // ── shortestArcDelta ─────────────────────────────────────────────────────

    @Test
    fun `crossing north forwards is a small positive step`() {
        assertEquals(1f, shortestArcDelta(0f, 359f), 0.001f)
    }

    @Test
    fun `crossing north backwards is a small negative step`() {
        assertEquals(-1f, shortestArcDelta(359f, 0f), 0.001f)
    }

    @Test
    fun `plain movement away from the seam is unaffected`() {
        assertEquals(10f, shortestArcDelta(100f, 90f), 0.001f)
        assertEquals(-10f, shortestArcDelta(90f, 100f), 0.001f)
    }

    /**
     * The case the exhaustive loop below can never reach: it steps by 7°, and
     * 180 is not a multiple of 7, so an exactly-opposite pair is unreachable by
     * construction. Pinning it explicitly documents that the range is
     * [-180, +180) — the lower bound is closed, the upper open.
     */
    @Test
    fun `exactly opposite headings return minus 180, not plus 180`() {
        assertEquals(-180f, shortestArcDelta(180f, 0f), 0.001f)
        assertEquals(-180f, shortestArcDelta(0f, 180f), 0.001f)
        assertEquals(-180f, shortestArcDelta(270f, 90f), 0.001f)
    }

    @Test
    fun `the result always lies within a half turn`() {
        var a = 0f
        while (a < 360f) {
            var b = 0f
            while (b < 360f) {
                val d = shortestArcDelta(a, b)
                assertTrue("$a -> $b gave $d", d > -180.0001f && d <= 180.0001f)
                b += 7f
            }
            a += 7f
        }
    }

    // ── normalizedHeading ────────────────────────────────────────────────────

    @Test
    fun `359 point 7 degrees displays as 0 not 360`() {
        assertEquals(0, normalizedHeading(359.7f))
    }

    @Test
    fun `ordinary bearings round normally`() {
        assertEquals(0, normalizedHeading(0f))
        assertEquals(90, normalizedHeading(89.6f))
        assertEquals(247, normalizedHeading(247.4f))
        assertEquals(359, normalizedHeading(359.4f))
    }

    // ── shouldPublishHeading ─────────────────────────────────────────────────

    @Test
    fun `movement under the deadband is suppressed`() {
        assertFalse(shouldPublishHeading(new = 0.3f, published = 0f))
    }

    @Test
    fun `movement over the deadband publishes`() {
        assertTrue(shouldPublishHeading(new = 0.6f, published = 0f))
    }

    /**
     * The case a naive `abs(new - published)` gets wrong: it reads this as a
     * 359.7° jump and publishes, so the readout would flicker at due north —
     * exactly where a compass is most likely to be sitting still.
     */
    @Test
    fun `a small move across the north seam is still suppressed`() {
        assertFalse(shouldPublishHeading(new = 0.1f, published = 359.8f))
    }

    @Test
    fun `a real move across the north seam still publishes`() {
        assertTrue(shouldPublishHeading(new = 0.6f, published = 359.8f))
    }

    @Test
    fun `the deadband boundary behaves consistently either side`() {
        assertFalse(shouldPublishHeading(new = 179.51f, published = 180f))
        assertTrue(shouldPublishHeading(new = 179.49f, published = 180f))
    }
}
