package com.pubpeb.compass.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate exists for one reason: a bare threshold swaps the level's instrument
 * under the user's hand on sensor noise. These tests are mostly about that.
 */
class LevelPostureGateTest {

    @Test
    fun `starts flat`() {
        assertEquals(LevelPosture.FLAT, LevelPostureGate().posture)
    }

    @Test
    fun `stays flat below the entry threshold`() {
        val gate = LevelPostureGate()
        assertEquals(LevelPosture.FLAT, gate.update(0f))
        assertEquals(LevelPosture.FLAT, gate.update(59.9f))
    }

    @Test
    fun `enters upright above the entry threshold`() {
        val gate = LevelPostureGate()
        assertEquals(LevelPosture.UPRIGHT, gate.update(60.1f))
    }

    @Test
    fun `entry is exclusive at exactly the threshold`() {
        val gate = LevelPostureGate()
        assertEquals(LevelPosture.FLAT, gate.update(60f))
    }

    @Test
    fun `holds upright through the hysteresis band`() {
        val gate = LevelPostureGate()
        gate.update(70f)
        // 50 is below the 60 entry threshold but above the 45 exit threshold:
        // a bare threshold would drop out here, which is the bug.
        assertEquals(LevelPosture.UPRIGHT, gate.update(50f))
        assertEquals(LevelPosture.UPRIGHT, gate.update(45.1f))
    }

    @Test
    fun `returns to flat below the exit threshold`() {
        val gate = LevelPostureGate()
        gate.update(70f)
        assertEquals(LevelPosture.FLAT, gate.update(44.9f))
    }

    /**
     * The regression test. A phone propped at the entry angle jitters a degree
     * or two either side of it; with a single threshold that swapped the whole
     * instrument on every sample.
     */
    @Test
    fun `oscillation at the entry angle does not flap`() {
        val gate = LevelPostureGate()
        val samples = listOf(59f, 61f, 59f, 61f, 58f, 62f, 59f, 61f)
        val postures = samples.map { gate.update(it) }

        // First sample above 60 latches UPRIGHT and the band holds it there for
        // the rest, since nothing drops below 45.
        assertEquals(LevelPosture.FLAT, postures[0])
        assertEquals(List(7) { LevelPosture.UPRIGHT }, postures.drop(1))
    }
}
